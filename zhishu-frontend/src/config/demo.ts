/**
 * 演示模式：生产构建由 VITE_DEMO_MODE 决定，并在 vite.config.ts 里固化成构建期常量 `__DEMO_MODE__`。
 *
 * 为什么要固化：`import.meta.env.VITE_DEMO_MODE === 'true'` 在产物里不是字面量，
 * Rollup 无法折叠 `if (isDemoMode)` 分支，导致 src/mock/** 被打进生产包（实测过）。
 * 用 define 注入 `false` 后，`if (false)` 分支与 mock 模块的 import 都会被 tree-shaking 剔除。
 * 单测场景由 vitest.config.ts 注入同名常量（恒为 false）。
 */
export const isDemoMode: boolean = __DEMO_MODE__

/** 演示账号（任意密码均可登录） */
export const DEMO_CREDENTIALS = {
  username: 'demo',
  password: 'demo123',
} as const

export const DEMO_TOKEN = 'demo-token-hydro-monitor'
