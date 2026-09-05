import { test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { loginAs, resolveCredentials } from './helpers/login'

/** 覆盖三种一级分组 + 跨前缀路径 + 个人中心 */
const PROBE_PATHS = [
  '/ai/chat',
  '/ai/model-config',
  '/permission/user',
  '/system/config',
  '/monitor/ops',
  '/devtools/api',
  '/profile/info',
]

/** 采集所有侧栏与顶栏导航的激活/展开状态 */
async function probe(page: Page) {
  return page.evaluate(() => {
    const describeMenu = (rootMenu: Element) => ({
      items: Array.from(rootMenu.querySelectorAll('.el-menu-item'))
        .filter((item) => item.classList.contains('is-active'))
        .map((item) => item.textContent?.trim() ?? ''),
      opened: Array.from(rootMenu.querySelectorAll('.el-sub-menu'))
        .filter((sub) => sub.classList.contains('is-opened'))
        .map((sub) => sub.querySelector('.el-sub-menu__title')?.textContent?.trim() ?? ''),
      itemTotal: rootMenu.querySelectorAll('.el-menu-item').length,
    })
    const asides = Array.from(document.querySelectorAll('.layout-aside')).map((aside) => ({
      variant: aside.classList.contains('layout-aside--secondary') ? 'secondary' : 'primary',
      menu: aside.querySelector(':scope > .el-menu')
        ? describeMenu(aside.querySelector(':scope > .el-menu')!)
        : null,
    }))
    const topNav = document.querySelector('.top-nav-menu .el-menu, .hybrid-top-header .el-menu')
    return {
      path: location.pathname,
      layoutMode: localStorage.getItem('layout_mode'),
      asides,
      topNav: topNav ? describeMenu(topNav) : null,
    }
  })
}

async function runForMode(page: Page, mode: string) {
  console.log(`\n########## layout_mode = ${mode} ##########`)
  await page.addInitScript(
    (m) => {
      localStorage.setItem('layout_mode', m)
    },
    mode,
  )

  for (const path of PROBE_PATHS) {
    try {
      await page.goto(path, { waitUntil: 'commit' })
      await page.waitForTimeout(1600)
      const spa = await probe(page)

      await page.reload({ waitUntil: 'commit' })
      await page.waitForTimeout(2200)
      const reload = await probe(page)

      console.log(`--- ${path}`)
      console.log(`  SPA   : ${JSON.stringify({ asides: spa.asides, topNav: spa.topNav })}`)
      console.log(`  RELOAD: ${JSON.stringify({ asides: reload.asides, topNav: reload.topNav })}`)
    } catch (error) {
      console.log(`--- ${path} FAILED: ${String(error).slice(0, 140)}`)
    }
  }
}

test('诊断：三种布局模式下刷新后菜单高亮', async ({ page }) => {
  test.setTimeout(900_000)
  await loginAs(page, resolveCredentials('demo'))

  for (const mode of ['hybrid', 'immersive']) {
    await runForMode(page, mode)
  }
})
