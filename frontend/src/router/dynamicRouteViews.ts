/** 视图懒加载映射，仅动态路由注册时使用，避免进入入口 chunk */
const viewModules = import.meta.glob('@/views/**/*.vue')

function toViewsPath(modulePath: string) {
  return modulePath.replace(/\\/g, '/').replace(/^.*\/views\//, 'views/')
}

/**
 * 根据后端 component 字段解析 Vue 组件
 * 例: views/dashboard/Dashboard.vue
 */
export function resolveViewComponent(component?: string) {
  if (!component) return undefined

  const normalized = component
    .replace(/^\//, '')
    .replace(/\\/g, '/')
    .replace(/\.vue$/, '')

  const entries = Object.entries(viewModules).map(
    ([path, loader]) => [toViewsPath(path), loader] as const,
  )

  const exact = entries.find(
    ([viewPath]) => viewPath === `${normalized}.vue` || viewPath === normalized,
  )
  if (exact) return exact[1]

  // 兼容目录迁移后仍写旧路径的情况：按文件名唯一匹配
  const fileName = normalized.split('/').pop()
  if (!fileName) return undefined
  const byName = entries.filter(([viewPath]) => {
    const base = viewPath
      .replace(/\.vue$/, '')
      .split('/')
      .pop()
    return base === fileName
  })
  return byName.length === 1 ? byName[0][1] : undefined
}
