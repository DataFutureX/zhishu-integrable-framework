import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
/** 与 API 报告一致：写入仓库 docs/（覆盖最新一份） */
const e2eReportDir = path.resolve(__dirname, '../docs/e2e-test-report')

const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:3100'

function selectedProjects(): string[] {
  const projects: string[] = []
  for (let i = 0; i < process.argv.length; i++) {
    const arg = process.argv[i]
    if (arg === '--project' && process.argv[i + 1]) {
      projects.push(process.argv[i + 1])
    } else if (arg.startsWith('--project=')) {
      projects.push(arg.slice('--project='.length))
    }
  }
  return projects
}

const selected = selectedProjects()
/** 仅跑 integration 时不自启演示前端（假定联调环境已就绪） */
const integrationOnly = selected.length === 1 && selected[0] === 'integration'
const enableWebServer = process.env.E2E_NO_WEBSERVER !== '1' && !integrationOnly

const chromeUse = {
  ...devices['Desktop Chrome'],
  baseURL,
  viewport: { width: 1440, height: 900 } as const,
}

/**
 * 前端冒烟：demo（自启 Vite 演示模式）/ integration（需已启动前后端）
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: e2eReportDir }]],
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: {
    baseURL,
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'demo',
      use: chromeUse,
    },
    {
      name: 'integration',
      use: chromeUse,
    },
  ],
  ...(enableWebServer
    ? {
        webServer: {
          command: 'npx vite --mode demo --host 127.0.0.1 --port 3100 --strictPort',
          url: baseURL,
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
          env: {
            ...process.env,
            PW_TEST: '1',
          },
        },
      }
    : {}),
})
