/**
 * Portal 按顶部导航分屏截图（真实点击菜单 + 视口截图，含顶栏）
 *
 * 前置：前端已启动（默认 http://localhost:3000）
 * 用法：node scripts/capture-portal-sections.mjs
 */
import { chromium } from 'playwright'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..')
const OUT_DIR = path.join(ROOT, 'screenshot')
const BASE = process.env.DEMO_BASE_URL || 'http://localhost:3000'

/** 与 PortalLanding.vue 顶部 navItems 一致 */
const NAV_SHOTS = [
  { name: '01a-portal-hero', label: '首屏', mode: 'hero' },
  { name: '01b-portal-opensource', label: '开源', navLabel: '开源', hash: 'opensource' },
  { name: '01c-portal-features', label: '能力', navLabel: '能力', hash: 'features' },
  { name: '01d-portal-stack', label: '技术栈', navLabel: '技术栈', hash: 'stack' },
  { name: '01e-portal-quickstart', label: '快速开始', navLabel: '快速开始', hash: 'quickstart' },
]

async function waitSettled(page, ms = 600) {
  await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {})
  await page.waitForTimeout(ms)
}

async function clickNavAndSettle(page, navLabel, hash) {
  const link = page.locator('.portal-nav__link', { hasText: navLabel }).first()
  await link.waitFor({ state: 'visible', timeout: 10000 })
  await link.click()

  // 等待滚动定位到目标区块（真实点击触发的 smooth scroll）
  await page.waitForFunction(
    (id) => {
      const el = document.getElementById(id)
      if (!el) return false
      const rect = el.getBoundingClientRect()
      const header = document.querySelector('.portal-header')
      const headerH = header ? header.getBoundingClientRect().height : 72
      // 区块顶部进入视口上方合理范围（被顶栏压住一点也可）
      return rect.top <= headerH + 48 && rect.bottom > headerH + 80
    },
    hash,
    { timeout: 8000 },
  ).catch(() => {})

  await page.waitForTimeout(700)
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

  try {
    await page.goto(`${BASE}/portal`, { waitUntil: 'domcontentloaded' })
    await waitSettled(page, 1200)

    // 暂停云朵动画，避免截图发糊；保留顶栏真实样式
    await page.addStyleTag({
      content: `
        .sky-cloud { animation: none !important; }
        html { scroll-behavior: auto !important; }
      `,
    })

    const results = []

    for (const item of NAV_SHOTS) {
      if (item.mode === 'hero') {
        await page.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }))
        await page.locator('.portal-hero').first().waitFor({ state: 'visible' })
        await page.waitForTimeout(500)
      } else {
        console.log(`点击顶部菜单「${item.navLabel}」…`)
        await clickNavAndSettle(page, item.navLabel, item.hash)
      }

      // 视口截图：包含顶部导航菜单
      const file = path.join(OUT_DIR, `${item.name}.png`)
      await page.screenshot({ path: file, fullPage: false })
      console.log(`✓ ${item.name}.png  (${item.label})`)
      results.push(item)
    }

    // 整页全长对照
    await page.evaluate(() => window.scrollTo({ top: 0, left: 0, behavior: 'instant' }))
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(OUT_DIR, '01-portal.png'),
      fullPage: true,
    })
    console.log('✓ 01-portal.png  (整页全长)')

    const indexPath = path.join(OUT_DIR, 'README.md')
    let readme = ''
    try {
      readme = await readFile(indexPath, 'utf8')
    } catch {
      readme = '# 云起应用平台 · 页面截图\n\n'
    }

    const portalTable = [
      '',
      '## Portal 分屏（点击顶部导航 · 视口截图）',
      '',
      `生成时间：${new Date().toISOString()}`,
      `来源：${BASE}/portal`,
      '说明：模拟点击顶部菜单后截取当前视口，每张均含顶部导航。',
      '',
      '| 文件 | 对应导航 |',
      '| --- | --- |',
      ...results.map((s) => `| ${s.name}.png | ${s.label} |`),
      '| 01-portal.png | 整页全长 |',
      '',
    ].join('\n')

    if (readme.includes('## Portal 分屏')) {
      readme = readme.replace(/## Portal 分屏[\s\S]*?(?=\n## |\n# |$)/, portalTable.trim() + '\n\n')
    } else {
      readme = readme.trimEnd() + '\n' + portalTable
    }

    readme = readme.replace(
      /\| 01-portal\.png \| 产品门户.*\|/,
      '| 01-portal.png | 产品门户（整页）；分屏见下方 |',
    )

    await writeFile(indexPath, readme, 'utf8')
    console.log(`\n完成：Portal 分屏 ${results.length} 张 + 整页 → ${OUT_DIR}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
