import { getAppInstance } from './appRef'

let readyPromise: Promise<void> | null = null
let ready = false

/** 登录 / 后台等路由进入前加载 Element Plus 样式与全局图标 */
export function ensureElementPlus(): Promise<void> {
  if (ready) return Promise.resolve()
  if (readyPromise) return readyPromise

  readyPromise = (async () => {
    // 模板里的 el-* 组件样式由 unplugin-vue-components 的 ElementPlusResolver 按需注入，
    // 这里只补「无法被模板静态识别」的程序式调用组件（ElMessage / MessageBox / Notification /
    // ElLoading 以及 v-loading 指令），避免再引入 352KB 的 element-plus/dist/index.css。
    await Promise.all([
      import('element-plus/es/components/message/style/css'),
      import('element-plus/es/components/message-box/style/css'),
      import('element-plus/es/components/notification/style/css'),
      import('element-plus/es/components/loading/style/css'),
    ])

    const icons = await import('@element-plus/icons-vue')
    const app = getAppInstance()
    if (app) {
      for (const [key, component] of Object.entries(icons)) {
        if (key === 'default' || typeof component !== 'object' || component === null) continue
        if (!app.component(key)) {
          app.component(key, component)
        }
      }
    }

    ready = true
  })()

  return readyPromise
}
