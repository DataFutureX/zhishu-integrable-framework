/**
 * 联调 / 演示模式页面截图脚本
 *
 * 前置：
 *   - 前端：npm run dev 或 npm run dev:demo（默认 http://localhost:3000）
 *   - 联调模式需后端已启动（默认 http://localhost:8080）
 *
 * 用法（在仓库根目录）：
 *   npm install --no-save playwright
 *   npx playwright install chromium
 *   node scripts/capture-demo-screenshots.mjs
 *
 * 环境变量：
 *   DEMO_BASE_URL   前端地址，默认 http://localhost:3000
 *   LOGIN_USERNAME  默认 admin（联调）/ 可通过环境变量覆盖
 *   LOGIN_PASSWORD  默认 admin123
 *   POST_LOGIN_WAIT_MS  登录后等待毫秒，默认 11000
 *
 * 截图输出：screenshot/
 */
import { chromium } from 'playwright'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const OUT_DIR = path.join(ROOT, 'screenshot')
const BASE = process.env.DEMO_BASE_URL || 'http://localhost:3000'
const USERNAME = process.env.LOGIN_USERNAME || 'admin'
const PASSWORD = process.env.LOGIN_PASSWORD || 'admin123'
const POST_LOGIN_WAIT_MS = Number(process.env.POST_LOGIN_WAIT_MS || 0)

const PUBLIC_PAGES = [
  { name: '01-portal', path: '/portal', fullPage: true },
  { name: '02-login', path: '/login', fullPage: true },
]

const AUTH_PAGES = [
  { name: '03-dashboard', path: '/home/dashboard' },
  { name: '04-user', path: '/permission/user' },
  { name: '05-unit', path: '/permission/unit' },
  { name: '06-role', path: '/permission/role' },
  { name: '07-menu', path: '/permission/menu' },
  { name: '08-system-config', path: '/system/config' },
  { name: '09-announcement', path: '/system/announcement' },
  { name: '10-operation-log', path: '/system/operation-log' },
  { name: '11-monitor', path: '/monitor/ops' },
  { name: '12-api-docs', path: '/devtools/api' },
  { name: '13-profile', path: '/profile/info' },
  { name: '14-change-password', path: '/profile/password' },
]

async function waitSettled(page, ms = 800) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(ms)
}

async function shot(page, name, fullPage = false) {
  const file = path.join(OUT_DIR, `${name}.png`)
  await page.screenshot({ path: file, fullPage })
  console.log(`✓ ${name}.png`)
  return file
}

/** 通过「空洞更暗」算法估算拼图目标 X（原图像素） */
async function solveSlideX(page, captcha) {
  const bg = captcha.backgroundImage || ''
  const slider = captcha.sliderImage || ''
  const sliderY = Number(captcha.sliderY || 0)
  const offsetY = Number(captcha.sliderImageOffsetY || 0)

  return page.evaluate(
    async ({ bg, slider, sliderY, offsetY }) => {
      const toSrc = (v) => (String(v).startsWith('data:') ? v : `data:image/png;base64,${v}`)
      const load = (src) =>
        new Promise((resolve, reject) => {
          const img = new Image()
          img.onload = () => resolve(img)
          img.onerror = reject
          img.src = toSrc(src)
        })

      const bgImg = await load(bg)
      const slImg = await load(slider)
      const bgCanvas = document.createElement('canvas')
      bgCanvas.width = bgImg.width
      bgCanvas.height = bgImg.height
      const bgCtx = bgCanvas.getContext('2d')
      bgCtx.drawImage(bgImg, 0, 0)
      const bgData = bgCtx.getImageData(0, 0, bgImg.width, bgImg.height).data

      const slCanvas = document.createElement('canvas')
      slCanvas.width = slImg.width
      slCanvas.height = slImg.height
      const slCtx = slCanvas.getContext('2d')
      slCtx.drawImage(slImg, 0, 0)
      const slData = slCtx.getImageData(0, 0, slImg.width, slImg.height).data

      let bestX = 40
      let bestScore = -1
      const maxX = Math.max(bgImg.width - slImg.width, 40)

      for (let x = 20; x <= maxX; x++) {
        let darkness = 0
        let count = 0
        for (let sy = 0; sy < slImg.height; sy++) {
          for (let sx = 0; sx < slImg.width; sx++) {
            const si = (sy * slImg.width + sx) * 4
            if (slData[si + 3] < 120) continue
            const bx = x + sx
            const by = sliderY - offsetY + sy
            if (bx < 0 || by < 0 || bx >= bgImg.width || by >= bgImg.height) continue
            const bi = (by * bgImg.width + bx) * 4
            darkness += 255 - bgData[bi] + (255 - bgData[bi + 1]) + (255 - bgData[bi + 2])
            count++
          }
        }
        if (!count) continue
        const score = darkness / count
        if (score > bestScore) {
          bestScore = score
          bestX = x
        }
      }
      return { bestX, bestScore, bgWidth: bgImg.width }
    },
    { bg, slider, sliderY, offsetY },
  )
}

async function completeSlideCaptcha(page, captchaPayload) {
  const captcha = page.locator('.slide-captcha').first()
  await captcha.waitFor({ state: 'visible', timeout: 15000 })
  await captcha.locator('.slide-captcha__loading').waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {})
  const thumb = captcha.locator('.slide-captcha__thumb')
  await thumb.waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(500)

  let targetDisplayX = 200
  if (captchaPayload) {
    const solved = await solveSlideX(page, captchaPayload)
    const wrap = captcha.locator('.slide-captcha__image-wrap')
    const wrapBox = await wrap.boundingBox()
    const scale = wrapBox ? wrapBox.width / solved.bgWidth : 1
    targetDisplayX = Math.round(solved.bestX * scale)
    console.log(`验证码推算 targetX=${solved.bestX} displayX=${targetDisplayX} score=${solved.bestScore.toFixed(1)}`)
  }

  const box = await thumb.boundingBox()
  if (!box) throw new Error('captcha thumb not found')
  const startX = box.x + box.width / 2
  const startY = box.y + box.height / 2
  const endX = startX + Math.max(targetDisplayX, 40)

  await page.mouse.move(startX, startY)
  await page.mouse.down()
  await page.mouse.move(endX, startY, { steps: 28 })
  await page.mouse.up()

  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 25000 })
}

async function login(page) {
  let captchaPayload = null
  const onResponse = async (response) => {
    try {
      const url = response.url()
      if (!url.includes('/auth/captcha') || response.request().method() !== 'GET') return
      if (url.includes('/verify')) return
      const json = await response.json()
      const data = json?.data ?? json
      if (data?.backgroundImage && data?.sliderImage) {
        captchaPayload = data
      }
    } catch {
      // ignore
    }
  }
  page.on('response', onResponse)

  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await waitSettled(page, 800)

  await page.locator('input[placeholder="用户名"]').fill(USERNAME)
  await page.locator('input[placeholder="密码"]').fill(PASSWORD)
  await page.getByRole('button', { name: /登\s*录/ }).click()

  await page.locator('.el-dialog.captcha-dialog').waitFor({ state: 'visible', timeout: 10000 })
  // 等 captcha 接口返回
  for (let i = 0; i < 40 && !captchaPayload; i++) {
    await page.waitForTimeout(100)
  }
  await completeSlideCaptcha(page, captchaPayload)
  page.off('response', onResponse)
  await waitSettled(page, 1000)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })

  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
  })
  const page = await context.newPage()
  page.setDefaultTimeout(30000)

  const results = []

  try {
    for (const item of PUBLIC_PAGES) {
      await page.goto(`${BASE}${item.path}`, { waitUntil: 'domcontentloaded' })
      await waitSettled(page, item.path === '/portal' ? 1200 : 800)
      results.push(await shot(page, item.name, Boolean(item.fullPage)))
    }

    console.log(`使用账号 ${USERNAME} 登录…`)
    await login(page)
    if (POST_LOGIN_WAIT_MS > 0) {
      console.log(`登录成功，等待 ${POST_LOGIN_WAIT_MS}ms …`)
      await page.waitForTimeout(POST_LOGIN_WAIT_MS)
    } else {
      console.log('登录成功，开始截图…')
    }

    for (const item of AUTH_PAGES) {
      await page.goto(`${BASE}${item.path}`, { waitUntil: 'domcontentloaded' })
      await waitSettled(page, 1200)
      const msgClose = page.locator('.el-message .el-message__closeBtn')
      if (await msgClose.count()) await msgClose.first().click().catch(() => {})
      results.push(await shot(page, item.name, false))
    }

    const index = [
      '# 云起应用平台 · 页面截图',
      '',
      `生成时间：${new Date().toISOString()}`,
      `来源：${BASE}`,
      `账号：${USERNAME}`,
      `登录后等待：${POST_LOGIN_WAIT_MS}ms`,
      '',
      '| 文件 | 页面 |',
      '| --- | --- |',
      '| 01-portal.png | 产品门户 |',
      '| 02-login.png | 登录 |',
      '| 03-dashboard.png | 仪表盘 |',
      '| 04-user.png | 用户管理 |',
      '| 05-unit.png | 单位管理 |',
      '| 06-role.png | 角色管理 |',
      '| 07-menu.png | 菜单管理 |',
      '| 08-system-config.png | 系统设置 |',
      '| 09-announcement.png | 公告管理 |',
      '| 10-operation-log.png | 操作日志 |',
      '| 11-monitor.png | 系统监控 |',
      '| 12-api-docs.png | API 文档 |',
      '| 13-profile.png | 个人信息 |',
      '| 14-change-password.png | 修改密码 |',
      '',
    ].join('\n')

    await writeFile(path.join(OUT_DIR, 'README.md'), index, 'utf8')
    console.log(`\n完成：共 ${results.length} 张截图 → ${OUT_DIR}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
