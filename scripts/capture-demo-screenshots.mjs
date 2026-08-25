/**
 * 联调环境页面截图脚本
 *
 * 前置：
 *   - 前端已启动（默认 http://localhost:3100）
 *   - 联调需后端已启动（默认 http://localhost:8180）
 *
 * 用法（仓库根目录）：
 *   npm install --no-save playwright
 *   npx playwright install chromium
 *   node scripts/capture-demo-screenshots.mjs
 *
 * 环境变量：
 *   DEMO_BASE_URL       前端地址，默认 http://localhost:3100
 *   LOGIN_USERNAME       默认 admin
 *   LOGIN_PASSWORD       默认 admin123
 *   POST_LOGIN_WAIT_MS  登录后额外等待毫秒，默认 0
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
const BASE = process.env.DEMO_BASE_URL || 'http://localhost:3100'
const USERNAME = process.env.LOGIN_USERNAME || 'admin'
const PASSWORD = process.env.LOGIN_PASSWORD || 'admin123'
const POST_LOGIN_WAIT_MS = Number(process.env.POST_LOGIN_WAIT_MS || 0)
const CAPTURE_ONLY = new Set(
  (process.env.CAPTURE_ONLY || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
)

const PUBLIC_PAGES = [
  { name: '01-portal', path: '/portal', label: '产品门户', fullPage: true },
  { name: '02-login', path: '/login', label: '登录', fullPage: true },
]

const PORTAL_SECTIONS = [
  { name: '01a-portal-hero', label: '首屏', mode: 'hero' },
  { name: '01b-portal-opensource', label: '开源', navLabel: '开源', hash: 'opensource' },
  { name: '01c-portal-features', label: '能力', navLabel: '能力', hash: 'features' },
  { name: '01d-portal-stack', label: '技术栈', navLabel: '技术栈', hash: 'stack' },
  { name: '01e-portal-docs', label: '文档', mode: 'docs', path: '/docs/quickstart' },
]

/** 登录后业务页：保留既有文件名，并补齐智能中心 / 开放能力 */
const AUTH_PAGES = [
  { name: '03-dashboard', path: '/ai/chat', label: 'Agent 会话', waitSelector: '.session-rail__title' },
  { name: '15-knowledge-qa', path: '/ai/qa', label: '知识检索', waitSelector: '.session-rail__title' },
  { name: '16-knowledge-graph', path: '/ai/knowledge-graph', label: '知识图谱', waitSelector: '.page-hero__title' },
  { name: '17-agents', path: '/ai/agents', label: 'Agents', waitSelector: '.agent-home' },
  { name: '18-knowledges', path: '/ai/knowledges', label: '知识库', waitSelector: '.page-hero__title' },
  { name: '19-mcp-hub', path: '/ai/mcp', label: 'MCP Hub', waitSelector: '.page-hero__title' },
  { name: '20-model-config', path: '/ai/model-config', label: '模型设置', waitSelector: '.page-header__title' },
  { name: '04-user', path: '/permission/user', label: '用户管理', waitSelector: '.page-hero__title' },
  { name: '07-menu', path: '/permission/menu', label: '菜单管理', waitSelector: '.page-hero__title' },
  { name: '06-role', path: '/permission/role', label: '角色管理', waitSelector: '.page-hero__title' },
  { name: '05-unit', path: '/permission/unit', label: '单位管理', waitSelector: '.page-hero__title' },
  { name: '08-system-config', path: '/system/config', label: '参数配置', waitSelector: '.page-header__title' },
  { name: '10-operation-log', path: '/system/operation-log', label: '操作日志', waitSelector: '.page-hero__title' },
  { name: '09-announcement', path: '/system/announcement', label: '公告管理', waitSelector: '.page-hero__title' },
  { name: '11-monitor', path: '/monitor/ops', label: '运维监控', waitSelector: '.monitor-hero__title' },
  { name: '12-api-docs', path: '/devtools/api', label: '后端接口', waitSelector: '.swagger-embed__title' },
  { name: '21-open-api', path: '/system/open-api', label: '开放能力', waitSelector: '.page-header__title' },
  { name: '13-profile', path: '/profile/info', label: '个人信息', waitSelector: '.profile-card .el-card__header' },
  { name: '14-change-password', path: '/profile/password', label: '修改密码', waitSelector: '.el-card__header' },
]

async function waitSettled(page, ms = 800) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(ms)
}

async function dismissOverlays(page) {
  const msgClose = page.locator('.el-message .el-message__closeBtn')
  if (await msgClose.count()) await msgClose.first().click().catch(() => {})
  const notifyClose = page.locator('.el-notification__closeBtn')
  if (await notifyClose.count()) await notifyClose.first().click().catch(() => {})
}

async function shot(page, name, fullPage = false) {
  await dismissOverlays(page)
  const file = path.join(OUT_DIR, `${name}.png`)
  await page.screenshot({ path: file, fullPage })
  console.log(`✓ ${name}.png`)
  return file
}

async function pausePortalMotion(page) {
  await page.addStyleTag({
    content: `
      *, *::before, *::after { animation-duration: 0s !important; animation-delay: 0s !important; transition-duration: 0s !important; }
      html { scroll-behavior: auto !important; }
    `,
  }).catch(() => {})
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

async function attemptLogin(page) {
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

  try {
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await waitSettled(page, 800)
    await page.locator('input[placeholder="用户名"]').fill(USERNAME)
    await page.locator('input[placeholder="密码"]').fill(PASSWORD)
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.locator('.el-dialog.captcha-dialog').waitFor({ state: 'visible', timeout: 10000 })
    for (let i = 0; i < 40 && !captchaPayload; i++) {
      await page.waitForTimeout(100)
    }
    await completeSlideCaptcha(page, captchaPayload)
    await waitSettled(page, 1000)
    await page.locator('.el-menu').first().waitFor({ state: 'visible', timeout: 15000 })
  } finally {
    page.off('response', onResponse)
  }
}

async function login(page) {
  let lastError
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      await attemptLogin(page)
      return
    } catch (err) {
      lastError = err
      console.warn(`登录失败（第 ${attempt + 1} 次），重试…`, err?.message || err)
      await page.context().clearCookies()
      await page.evaluate(() => {
        localStorage.clear()
        sessionStorage.clear()
      }).catch(() => {})
    }
  }
  throw lastError
}

async function capturePortalSections(page, results, sections = PORTAL_SECTIONS) {
  await page.goto(`${BASE}/portal`, { waitUntil: 'domcontentloaded' })
  await waitSettled(page, 1200)
  await pausePortalMotion(page)

  for (const item of sections) {
    if (item.mode === 'hero') {
      await page.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }))
      await page.locator('.portal-hero').first().waitFor({ state: 'visible' })
      await page.waitForTimeout(400)
    } else if (item.mode === 'docs') {
      await page.goto(`${BASE}${item.path}`, { waitUntil: 'domcontentloaded' })
      await waitSettled(page, 800)
      await page.locator('.docs-page__title').first().waitFor({ state: 'visible', timeout: 12000 })
      await pausePortalMotion(page)
      await page.waitForTimeout(400)
    } else {
      if (!page.url().includes('/portal')) {
        await page.goto(`${BASE}/portal`, { waitUntil: 'domcontentloaded' })
        await waitSettled(page, 600)
        await pausePortalMotion(page)
      }
      const link = page.locator('.portal-nav__link', { hasText: item.navLabel }).first()
      await link.waitFor({ state: 'visible', timeout: 10000 })
      await link.click()
      await page.waitForFunction(
        (id) => {
          const el = document.getElementById(id)
          if (!el) return false
          const rect = el.getBoundingClientRect()
          const header = document.querySelector('.portal-header')
          const headerH = header ? header.getBoundingClientRect().height : 72
          return rect.top <= headerH + 48 && rect.bottom > headerH + 80
        },
        item.hash,
        { timeout: 8000 },
      ).catch(() => {})
      await page.waitForTimeout(700)
    }
    results.push(await shot(page, item.name, false))
  }
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
    const publicPages = PUBLIC_PAGES.filter((item) => !CAPTURE_ONLY.size || CAPTURE_ONLY.has(item.name))
    const portalSections = PORTAL_SECTIONS.filter((item) => !CAPTURE_ONLY.size || CAPTURE_ONLY.has(item.name))
    const authPages = AUTH_PAGES.filter((item) => !CAPTURE_ONLY.size || CAPTURE_ONLY.has(item.name))

    for (const item of publicPages) {
      await page.goto(`${BASE}${item.path}`, { waitUntil: 'domcontentloaded' })
      await waitSettled(page, item.path === '/portal' ? 1200 : 800)
      if (item.path === '/portal') await pausePortalMotion(page)
      results.push(await shot(page, item.name, Boolean(item.fullPage)))
    }

    if (portalSections.length) {
      console.log('截取门户分屏…')
      await capturePortalSections(page, results, portalSections)
    }

    if (!authPages.length) {
      console.log(`\n完成：共 ${results.length} 张截图 → ${OUT_DIR}`)
      return
    }

    console.log(`使用账号 ${USERNAME} 登录…`)
    await login(page)
    if (POST_LOGIN_WAIT_MS > 0) {
      console.log(`登录成功，等待 ${POST_LOGIN_WAIT_MS}ms …`)
      await page.waitForTimeout(POST_LOGIN_WAIT_MS)
    } else {
      console.log('登录成功，开始截图…')
    }

    for (const item of authPages) {
      await page.goto(`${BASE}${item.path}`, { waitUntil: 'domcontentloaded' })
      await waitSettled(page, 1200)
      if (item.waitSelector) {
        await page.locator(item.waitSelector).first().waitFor({ state: 'visible', timeout: 15000 }).catch(() => {
          console.warn(`  未等到 ${item.waitSelector}（${item.path}）`)
        })
      }
      await page.locator('.el-loading-mask').first().waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {})
      await page.waitForTimeout(500)
      results.push(await shot(page, item.name, false))
    }

    if (!CAPTURE_ONLY.size) {
      const allRows = [...PUBLIC_PAGES, ...PORTAL_SECTIONS, ...AUTH_PAGES]
      const index = [
        '# 知枢可集成框架 · 页面截图',
        '',
        `生成时间：${new Date().toISOString()}`,
        `来源：${BASE}`,
        `账号：${USERNAME}`,
        `登录后等待：${POST_LOGIN_WAIT_MS}ms`,
        '',
        '| 文件 | 页面 |',
        '| --- | --- |',
        ...allRows.map((item) => `| ${item.name}.png | ${item.label} |`),
        '',
        '## Portal 分屏（点击顶部导航 · 视口截图）',
        '',
        '说明：模拟点击顶部菜单后截取当前视口，每张均含顶部导航。',
        '',
        '| 文件 | 对应导航 |',
        '| --- | --- |',
        ...PORTAL_SECTIONS.map((item) => `| ${item.name}.png | ${item.label} |`),
        '| 01-portal.png | 整页全长 |',
        '',
      ].join('\n')
      await writeFile(path.join(OUT_DIR, 'README.md'), index, 'utf8')
    }
    console.log(`\n完成：共 ${results.length} 张截图 → ${OUT_DIR}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
