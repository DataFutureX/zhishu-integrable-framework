import { getAppInstance } from './appRef'

let readyPromise: Promise<void> | null = null
let ready = false

/** 登录 / 后台等路由进入前加载 Element Plus 样式与全局图标 */
export function ensureElementPlus(): Promise<void> {
  if (ready) return Promise.resolve()
  if (readyPromise) return readyPromise

  readyPromise = (async () => {
    await import('element-plus/dist/index.css')
    const icons = await import('@element-plus/icons-vue')
    const app = getAppInstance()
    if (app) {
      for (const [key, component] of Object.entries(icons)) {
        if (!app.component(key)) {
          app.component(key, component)
        }
      }
    }
    ready = true
  })()

  return readyPromise
}
