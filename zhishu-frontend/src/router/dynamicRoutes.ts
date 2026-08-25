import { RouterView, type RouteRecordRaw, type Router } from 'vue-router'
import type { MenuVO } from '@/types/menu'
import { LAYOUT_ROUTE_NAME } from '@/router/layoutRoute'
import { resolveViewComponent } from '@/router/dynamicRouteViews'
import { parseRouteMetaJson } from '@/utils/routeLayout'
import { ensureNotFoundRoute } from '@/router/notFoundRoute'

export { LAYOUT_ROUTE_NAME } from '@/router/layoutRoute'

/** 与静态路由冲突、禁止注册为 Layout 子路由的路径 */
const RESERVED_PATHS = new Set(['/login', '/portal', '/docs', '/403', '/404'])

/** 与静态路由冲突、禁止用于动态路由的 name */
const RESERVED_ROUTE_NAMES = new Set([
  'Login',
  'Portal',
  'PortalDocs',
  'Layout',
  'Forbidden',
  'NotFound',
])

const normalizeFullPath = (path: string) => (path.startsWith('/') ? path : `/${path}`)

const isReservedPath = (path?: string) => {
  if (!path) return false
  const normalized = normalizeFullPath(path)
  if (RESERVED_PATHS.has(normalized)) return true
  return normalized === '/docs' || normalized.startsWith('/docs/')
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

/** 子菜单 path 是否落在父级 path 之下（否则不能作为 Vue Router 嵌套子路由） */
function isNestedUnder(childPath: string, parentPath: string): boolean {
  const child = childPath.replace(/^\//, '')
  const parent = parentPath.replace(/^\//, '')
  return child === parent || child.startsWith(`${parent}/`)
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

/**
 * 将菜单转为当前层级的路由列表。
 * 侧栏分组（如系统设置）下的子页 path 若不以父 path 为前缀
 * （/monitor/ops、/devtools/api），必须提升到与父级同级，否则会注册成 /system/monitor/ops 导致 404。
 */
function convertMenu(menu: MenuVO, parentFullPath?: string): RouteRecordRaw[] {
  if (menu.menuType === 'BUTTON') return []
  if (!menu.path || isReservedPath(menu.path)) return []
  if (menu.routeName && RESERVED_ROUTE_NAMES.has(menu.routeName)) return []

  const nestedChildren: MenuVO[] = []
  const hoisted: RouteRecordRaw[] = []

  for (const child of menu.children || []) {
    if (child.menuType === 'BUTTON') continue
    if (child.path && menu.path && !isNestedUnder(child.path, menu.path)) {
      hoisted.push(...convertMenu(child, parentFullPath))
    } else {
      nestedChildren.push(child)
    }
  }

  const meta = buildRouteMeta(menu)
  const nestedInParent = Boolean(parentFullPath && isNestedUnder(menu.path, parentFullPath))
  const relativePath = nestedInParent
    ? toRelativePath(menu.path, parentFullPath)
    : menu.path.startsWith('/')
      ? menu.path
      : `/${menu.path}`
  const childRoutes = nestedChildren.flatMap((child) => convertMenu(child, menu.path))

  const component = resolveViewComponent(menu.component)
  const isDirectory = menu.menuType === 'DIRECTORY' || (!component && childRoutes.length > 0)
  const current: RouteRecordRaw[] = []

  if (isDirectory) {
    const route: RouteRecordRaw = {
      path: relativePath,
      name: menu.routeName || undefined,
      // 目录本身无页面，需要 RouterView 才能渲染嵌套子页
      component: RouterView,
      meta,
      children: childRoutes,
    }
    if (menu.redirect) {
      route.redirect = menu.redirect
    } else {
      const firstPath = findFirstMenuPath(menu.children || [])
      if (firstPath) route.redirect = firstPath
    }
    current.push(route)
  } else if (!component) {
    console.warn(`[dynamicRoutes] 无法解析组件: ${menu.component}`, menu)
  } else {
    current.push({
      path: relativePath,
      name: menu.routeName || undefined,
      component,
      meta,
      ...(menu.redirect ? { redirect: menu.redirect } : {}),
      ...(childRoutes.length > 0 ? { children: childRoutes } : {}),
    } as RouteRecordRaw)
  }

  return [...current, ...hoisted]
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
  return menus.flatMap((menu) => convertMenu(menu))
}

export function resetDynamicRoutes(router: Router) {
  registeredRouteNames.forEach((name) => {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  })
  registeredRouteNames = []
  ensureStaticRoutes(router)
  ensureNotFoundRoute(router)
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
  if (!router.hasRoute('OpsMonitor')) {
    router.addRoute(LAYOUT_ROUTE_NAME, {
      path: 'monitor/ops',
      name: 'OpsMonitor',
      component: () => import('@/views/system/SystemMonitor.vue'),
      meta: { title: '运维监控', requiresAuth: true },
    })
  }
  if (!router.hasRoute('BackendApi')) {
    router.addRoute(LAYOUT_ROUTE_NAME, {
      path: 'devtools/api',
      name: 'BackendApi',
      component: () => import('@/views/devtools/SwaggerEmbed.vue'),
      meta: { title: '后端接口', requiresAuth: true, hideBreadcrumb: true, fullBleed: true },
    })
  }
}

export function addDynamicRoutes(router: Router, menus: MenuVO[]) {
  resetDynamicRoutes(router)

  const children = menusToRoutes(menus)
  const addedNames: string[] = []

  children.forEach((route) => {
    if (route.name && router.hasRoute(route.name)) return
    router.addRoute(LAYOUT_ROUTE_NAME, route)
    addedNames.push(...collectRouteNames([route]))
  })
  registeredRouteNames = addedNames

  ensureStaticRoutes(router)
  // 动态路由加完后把 404 挂回末尾，防止通配符抢先匹配
  ensureNotFoundRoute(router)

  return children
}
