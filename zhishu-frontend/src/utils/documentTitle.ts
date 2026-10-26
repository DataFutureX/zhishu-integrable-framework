/**
 * 统一的 document.title 组装：`路由标题 · 系统名`。
 *
 * 之前路由守卫与 systemConfigStore 各自直接写 document.title，
 * 配置加载完成后会把路由标题覆盖掉；这里收敛成一处状态，双方只更新自己的部分。
 */

const DEFAULT_SYSTEM_NAME = '知枢可集成框架'

let systemName = ''
let routeTitle = ''

function apply() {
  const base = systemName || DEFAULT_SYSTEM_NAME
  document.title = routeTitle ? `${routeTitle} · ${base}` : base
}

/** 路由切换时更新页面标题（由 router.afterEach 调用） */
export function setRouteTitle(title: string) {
  routeTitle = title
  apply()
}

/** 系统名加载/变更后刷新标题后缀 */
export function setDocumentSystemName(name: string) {
  systemName = name || ''
  apply()
}

/** 单测用：读取当前组装结果 */
export function getDocumentTitleParts() {
  return { systemName, routeTitle }
}
