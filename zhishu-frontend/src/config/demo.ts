/** 演示模式：通过环境变量 VITE_DEMO_MODE=true 启用，无需后端服务 */
export const isDemoMode = import.meta.env.VITE_DEMO_MODE === 'true'

/** 演示账号（任意密码均可登录） */
export const DEMO_CREDENTIALS = {
  username: 'demo',
  password: 'demo123',
} as const

export const DEMO_TOKEN = 'demo-token-hydro-monitor'
