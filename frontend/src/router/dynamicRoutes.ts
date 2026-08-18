import type { RouteRecordRaw, Router } from 'vue-router'
import type { MenuVO } from '@/types/menu'
import { parseRouteMetaJson } from '@/utils/routeLayout'

export const LAYOUT_ROUTE_NAME = 'Layout'

/** 与静态路由冲突、禁止注册为 Layout 子路由的路径 */
const RESERVED_PATHS = new Set(['/login', '/portal', '/403', '/404'])

/** 与静态路由冲突、禁止用于动态路由的 name */
const RESERVED_ROUTE_NAMES = new Set(['Login', 'Portal', 'Layout', 'Forbidden', 'NotFound'])

const normalizeFullPath = (path: string) => (path.startsWith('/') ? path : `/${path}`)

const isReservedPath = (path?: string) => {
  if (!path) return false
  return RESERVED_PATHS.has(normalizeFullPath(path))
}

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

  const normalized = component.replace(/^\//, '').replace(/\\/g, '/').replace(/\.vue$/, '')

  const entries = Object.entries(viewModules).map(([path, loader]) => [toViewsPath(path), loader] as const)

  const exact = entries.find(([viewPath]) => viewPath === `${normalized}.vue` || viewPath === normalized)
  if (exact) return exact[1]

  // 兼容目录迁移后仍写旧路径的情况：按文件名唯一匹配
  const fileName = normalized.split('/').pop()
  if (!fileName) return undefined
  const byName = entries.filter(([viewPath]) => {
    const base = viewPath.replace(/\.vue$/, '').split('/').pop()
    return base === fileName
  })
  return byName.length === 1 ? byName[0][1] : undefined
}

function parseMenuMeta(menu: MenuVO): Record<string, unknown> {
  return parseRouteMetaJson(menu.meta)
}

function collectMenuPermissions(menu: MenuVO): string[] {
  const meta = parseMenuMeta(menu)
  const fromMeta = meta.permissions
  if (typeof fromMeta === 'string' && fromMeta.trim()) {
    return [fromMeta.trim()]
  }
  if (Array.isArray(fromMeta)) {
    return fromMeta.map(String).filter(Boolean)
  }

  return (menu.children || [])
    .filter((child) => child.menuType === 'BUTTON' && child.routeName)
    .map((child) => String(child.routeName))
}

function buildRouteMeta(menu: MenuVO): Record<string, unknown> {
  const permissions = collectMenuPermissions(menu)
  return {
    title: menu.title,
    icon: menu.icon,
    requiresAuth: menu.requiresAuth !== 0,
    menuId: menu.id,
    ...(permissions.length ? { permissions } : {}),
    ...parseMenuMeta(menu),
    // 显式 permissions 覆盖 meta JSON 中可能存在的同名字段（保持数组）
    ...(permissions.length ? { permissions } : {}),
  }
}

function toRelativePath(fullPath: string, parentFullPath?: string): string {
  const normalized = fullPath.replace(/^\//, '')
  if (!parentFullPath) return normalized

  const parent = parentFullPath.replace(/^\//, '')
  if (normalized === parent) return ''
  if (normalized.startsWith(`${parent}/`)) {
    return normalized.slice(parent.length + 1)
  }
  return normalized
}

export function findFirstMenuPath(menus: MenuVO[]): string | null {
  for (const menu of menus) {
    if (menu.path && isReservedPath(menu.path)) continue
    if (menu.redirect) {
      return menu.redirect.startsWith('/') ? menu.redirect : `/${menu.redirect}`
    }
    if (menu.menuType === 'MENU' && menu.path && menu.component) {
      return menu.path.startsWith('/') ? menu.path : `/${menu.path}`
    }
    if (menu.children?.length) {
      const childPath = findFirstMenuPath(menu.children)
      if (childPath) return childPath
    }
  }
  return null
}

export function filterVisibleMenus(menus: MenuVO[]): MenuVO[] {
  return menus
    .filter(
      (menu) =>
        menu.visible !== 0 &&
        menu.menuType !== 'PAGE' &&
        menu.menuType !== 'BUTTON' &&
        !isReservedPath(menu.path),
    )
    .map((menu) => ({
      ...menu,
      children: menu.children?.length ? filterVisibleMenus(menu.children) : undefined,
    }))
    .filter((menu) => menu.menuType !== 'DIRECTORY' || (menu.children && menu.children.length > 0))
}

function menuToRoute(menu: MenuVO, parentFullPath?: string): RouteRecordRaw | null {
  if (menu.menuType === 'BUTTON') return null
  if (!menu.path || isReservedPath(menu.path)) return null
  if (menu.routeName && RESERVED_ROUTE_NAMES.has(menu.routeName)) return null

  const meta = buildRouteMeta(menu)
  const relativePath = toRelativePath(menu.path, parentFullPath)
  const childRoutes = (menu.children || [])
    .map((child) => menuToRoute(child, menu.path))
    .filter((route): route is RouteRecordRaw => route !== null)

  const component = resolveViewComponent(menu.component)
  const isDirectory =
    menu.menuType === 'DIRECTORY' || (!component && childRoutes.length > 0)

  if (isDirectory) {
    const route: RouteRecordRaw = {
      path: relativePath,
      name: menu.routeName || undefined,
      meta,
      children: childRoutes,
    }
    if (menu.redirect) {
      route.redirect = menu.redirect
    } else {
      const firstPath = findFirstMenuPath(menu.children || [])
      if (firstPath) route.redirect = firstPath
    }
    return route
  }

  if (!component) {
    console.warn(`[dynamicRoutes] 无法解析组件: ${menu.component}`, menu)
    return null
  }

  return {
    path: relativePath,
    name: menu.routeName || undefined,
    component,
    meta,
    ...(menu.redirect ? { redirect: menu.redirect } : {}),
    ...(childRoutes.length > 0 ? { children: childRoutes } : {}),
  } as RouteRecordRaw
}

function collectRouteNames(routes: RouteRecordRaw[]): string[] {
  const names: string[] = []

  const walk = (items: RouteRecordRaw[]) => {
    items.forEach((route) => {
      if (route.name) names.push(route.name as string)
      if (route.children?.length) walk(route.children)
    })
  }

  walk(routes)
  return names
}

let registeredRouteNames: string[] = []

export function menusToRoutes(menus: MenuVO[]): RouteRecordRaw[] {
  return menus
    .map((menu) => menuToRoute(menu))
    .filter((route): route is RouteRecordRaw => route !== null)
}

export function resetDynamicRoutes(router: Router) {
  registeredRouteNames.forEach((name) => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
  registeredRouteNames = []
  ensureStaticRoutes(router)
}

/** 动态路由可能覆盖同名静态路由，重置后恢复 */
export function ensureStaticRoutes(router: Router) {
  if (!router.hasRoute('Login')) {
    router.addRoute({
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/Login.vue'),
      meta: { title: '登录', requiresAuth: false },
    })
  }
}

export function addDynamicRoutes(router: Router, menus: MenuVO[]) {
  resetDynamicRoutes(router)

  const children = menusToRoutes(menus)
  registeredRouteNames = collectRouteNames(children)

  children.forEach((route) => {
    router.addRoute(LAYOUT_ROUTE_NAME, route)
  })

  ensureStaticRoutes(router)

  return children
}
