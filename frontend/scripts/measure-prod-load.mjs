/**
 * 生产包加载测速（屏蔽第三方字体/统计，避免拖慢 network）
 * 用法：node scripts/measure-prod-load.mjs [baseUrl]
 */
import { chromium } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

const BASE = process.argv[2] || 'http://127.0.0.1:4173'

const PAGES = [
  { name: 'portal', path: '/portal', ready: '.portal-hero__brand' },
  { name: 'docs-quickstart', path: '/docs/quickstart', ready: '.docs-page__title' },
  { name: 'docs-sso', path: '/docs/sso', ready: '.portal-docs__heading' },
  { name: 'docs-wanxiang', path: '/docs/wanxiang', ready: '.portal-docs__heading' },
  { name: 'docs-sdk', path: '/docs/sso-sdk', ready: '.portal-docs__heading' },
  { name: 'login', path: '/login', ready: 'input[placeholder="用户名"]' },
]

function kb(n) {
  return Math.round(n / 102.4) / 10
}

async function measure(browser, def) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.route('**/*', async (route) => {
    const url = route.request().url()
    if (/googleapis|gstatic|hm\.baidu|google-analytics|doubleclick/i.test(url)) {
      return route.abort()
    }
    return route.continue()
  })
  const page = await context.newPage()
  const assets = []
  page.on('response', async (res) => {
    const url = res.url()
    if (!url.startsWith(BASE)) return
    let size = Number(res.headers()['content-length'] || 0)
    if (!size) {
      try {
        size = (await res.body()).byteLength
      } catch {
        size = 0
      }
    }
    assets.push({
      name: url.replace(BASE, ''),
      type: res.request().resourceType(),
      bytes: size,
    })
  })

  const t0 = Date.now()
  await page.goto(`${BASE}${def.path}`, { waitUntil: 'commit', timeout: 60000 })
  const commitMs = Date.now() - t0
  await page.waitForSelector(def.ready, { state: 'visible', timeout: 30000 })
  const readyMs = Date.now() - t0
  await page.waitForTimeout(150)

  const perf = await page.evaluate(() => {
    const nav = performance.getEntriesByType('navigation')[0]
    const paints = performance.getEntriesByType('paint')
    const res = performance.getEntriesByType('resource')
    const local = res.filter((r) => r.name.startsWith(location.origin))
    return {
      ttfb: nav ? Math.round(nav.responseStart - nav.requestStart) : null,
      dcl: nav ? Math.round(nav.domContentLoadedEventEnd) : null,
      load: nav ? Math.round(nav.loadEventEnd) : null,
      fcp: Math.round(paints.find((p) => p.name === 'first-contentful-paint')?.startTime || 0) || null,
      transfer: Math.round(local.reduce((s, r) => s + (r.transferSize || r.encodedBodySize || 0), 0)),
      count: local.length,
    }
  })

  const js = assets.filter((a) => a.type === 'script')
  const css = assets.filter((a) => a.type === 'stylesheet')
  const img = assets.filter((a) => a.type === 'image')
  const top = [...assets]
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, 6)
    .map((a) => ({ name: a.name.split('/').pop(), type: a.type, kb: kb(a.bytes) }))

  await context.close()
  return {
    name: def.name,
    path: def.path,
    commitMs,
    readyMs,
    ttfbMs: perf.ttfb,
    fcpMs: perf.fcp,
    dclMs: perf.dcl,
    loadMs: perf.load,
    requestCount: assets.length,
    totalKb: kb(assets.reduce((s, a) => s + a.bytes, 0)),
    jsKb: kb(js.reduce((s, a) => s + a.bytes, 0)),
    cssKb: kb(css.reduce((s, a) => s + a.bytes, 0)),
    imgKb: kb(img.reduce((s, a) => s + a.bytes, 0)),
    transferKb: kb(perf.transfer || 0),
    top,
  }
}

async function main() {
  for (let i = 0; i < 40; i++) {
    try {
      await fetch(BASE)
      break
    } catch {
      await new Promise((r) => setTimeout(r, 250))
    }
  }

  const browser = await chromium.launch({ headless: true })
  const cold = []
  const warm = []
  try {
    for (const p of PAGES) {
      const r = await measure(browser, p)
      cold.push(r)
      console.log(`COLD ${r.name} ready=${r.readyMs}ms fcp=${r.fcpMs} total=${r.totalKb}KB js=${r.jsKb} css=${r.cssKb}`)
    }
    for (const p of PAGES) {
      const r = await measure(browser, p)
      warm.push(r)
      console.log(`WARM ${r.name} ready=${r.readyMs}ms total=${r.totalKb}KB`)
    }
  } finally {
    await browser.close()
  }

  const out = {
    measuredAt: new Date().toISOString(),
    base: BASE,
    mode: 'block-third-party-fonts-analytics',
    note: '关键指标=关键选择器可见；已 abort Google Fonts / 百度统计。体积为未压缩 body（线上经 gzip/br 更小）。',
    cold,
    warm,
  }
  await writeFile('dist/load-metrics.json', JSON.stringify(out, null, 2))
  console.log('wrote dist/load-metrics.json')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
