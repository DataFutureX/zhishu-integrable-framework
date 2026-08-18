import { expect, type Page } from '@playwright/test'
import type { AuthPageFixture } from '../fixtures/pages'

async function waitSettled(page: Page, ms = 800) {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {})
  await page.waitForTimeout(ms)
}

/** 关闭可能遮挡断言的 Element Plus 消息 */
async function dismissMessages(page: Page) {
  const msgClose = page.locator('.el-message .el-message__closeBtn')
  if (await msgClose.count()) {
    await msgClose.first().click().catch(() => {})
  }
}

/** 打开业务页并断言关键 UI 可见 */
export async function expectAuthPageReady(page: Page, item: AuthPageFixture) {
  await page.goto(item.path, { waitUntil: 'domcontentloaded' })
  await waitSettled(page, 1200)
  await dismissMessages(page)

  await expect(page).not.toHaveURL(/\/login/)
  await expect(page.locator('.el-menu').first()).toBeVisible()

  const titleEl = page.locator(item.titleSelector).first()
  await expect(titleEl).toBeVisible({ timeout: 15_000 })
  if (item.title) {
    await expect(titleEl).toContainText(item.title)
  }
}

/** 打开公开页并断言选择器可见 */
export async function expectPublicPageReady(page: Page, path: string, expectSelector: string) {
  await page.goto(path, { waitUntil: 'domcontentloaded' })
  await waitSettled(page, path === '/portal' ? 1200 : 800)
  await expect(page.locator(expectSelector).first()).toBeVisible({ timeout: 15_000 })
}
