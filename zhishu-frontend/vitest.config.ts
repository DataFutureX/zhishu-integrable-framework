import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
/** 与 API / E2E 报告一致：写入仓库 docs/（覆盖最新一份） */
const unitReportHtml = path.resolve(__dirname, '../docs/unit-test-report/index.html')

export default defineConfig({
  plugins: [vue()],
  // 与 vite.config.ts 保持一致：单测里演示开关恒为 false
  define: {
    __DEMO_MODE__: 'false',
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.ts'],
    globals: false,
    restoreMocks: true,
    reporters: ['default', 'html'],
    outputFile: {
      html: unitReportHtml,
    },
  },
})
