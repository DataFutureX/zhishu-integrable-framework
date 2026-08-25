import type { Page } from '@playwright/test'
import { completeSlideCaptcha } from './captcha'

export type E2ECredentials = {
  username: string
  password: string
}

/** 按 Playwright project 解析账号；可用 E2E_USERNAME / E2E_PASSWORD 覆盖 */
export function resolveCredentials(projectName: string): E2ECredentials {
  const username = process.env.E2E_USERNAME
  const password = process.env.E2E_PASSWORD
  if (projectName === 'integration') {
    return {
      username: username || 'admin',
      password: password || 'admin123',
    }
  }
  return {
    username: username || 'demo',
    password: password || 'demo123',
  }
}

async function waitSettled(page: Page, ms = 800) {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {})
  await page.waitForTimeout(ms)
}

async function attemptLogin(page: Page, credentials: E2ECredentials) {
  let captchaPayload: {
    backgroundImage?: string
    sliderImage?: string
    sliderY?: number
    sliderImageOffsetY?: number
  } | null = null

  const onResponse = async (response: import('@playwright/test').Response) => {
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
      // ignore parse errors
    }
  }

  page.on('response', onResponse)

  try {
    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    await waitSettled(page, 800)

    await page.locator('input[placeholder="用户名"]').fill(credentials.username)
    await page.locator('input[placeholder="密码"]').fill(credentials.password)
    await page.getByRole('button', { name: /登\s*录/ }).click()

    await page.locator('.el-dialog.captcha-dialog').waitFor({ state: 'visible', timeout: 10_000 })
    for (let i = 0; i < 40 && !captchaPayload; i++) {
      await page.waitForTimeout(100)
    }
    await completeSlideCaptcha(page, captchaPayload)
    await waitSettled(page, 1000)
  } finally {
    page.off('response', onResponse)
  }
}

/** 登录并等待侧栏就绪（动态菜单注册完成） */
export async function loginAs(page: Page, credentials: E2ECredentials) {
  let lastError: unknown
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      await attemptLogin(page, credentials)
      await page.locator('.el-menu').first().waitFor({ state: 'visible', timeout: 15_000 })
      return
    } catch (err) {
      lastError = err
      await page.context().clearCookies()
      await page.evaluate(() => {
        localStorage.clear()
        sessionStorage.clear()
      })
    }
  }
  throw lastError
}
