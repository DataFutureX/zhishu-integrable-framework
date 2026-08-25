import { expect, test } from '@playwright/test'
import { AUTH_PAGES, PUBLIC_PAGES } from './fixtures/pages'
import { expectAuthPageReady, expectPublicPageReady } from './helpers/assertPage'
import { loginAs, resolveCredentials } from './helpers/login'

test.describe('前端功能界面冒烟', () => {
  test.describe('公开页', () => {
    for (const pageItem of PUBLIC_PAGES) {
      test(`${pageItem.name} 可打开且关键 UI 可见`, async ({ page }) => {
        await expectPublicPageReady(page, pageItem.path, pageItem.expectSelector)
      })
    }

    test('登录页表单控件完整', async ({ page }) => {
      await page.goto('/login', { waitUntil: 'domcontentloaded' })
      await expect(page.locator('input[placeholder="用户名"]')).toBeVisible()
      await expect(page.locator('input[placeholder="密码"]')).toBeVisible()
      await expect(page.getByRole('button', { name: /登\s*录/ })).toBeVisible()
    })
  })

  test.describe('登录后业务页', () => {
    test.beforeEach(async ({ page }, testInfo) => {
      const credentials = resolveCredentials(testInfo.project.name)
      await loginAs(page, credentials)
    })

    test('登录后进入 Agent 会话且可刷新', async ({ page }) => {
      await page.goto('/ai/chat', { waitUntil: 'domcontentloaded' })
      await expect(page.locator('.session-rail__title')).toBeVisible()
    })

    for (const pageItem of AUTH_PAGES) {
      test(`${pageItem.name}（${pageItem.path}）可打开且关键 UI 可见`, async ({ page }) => {
        await expectAuthPageReady(page, pageItem)
      })
    }
  })
})
