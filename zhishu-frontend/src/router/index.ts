import {
  createRouter,
  createWebHistory,
  type RouteLocationMatched,
  type RouteRecordRaw,
} from 'vue-router'

import { LAYOUT_ROUTE_NAME } from '@/router/layoutRoute'
import { notFoundRoute } from '@/router/notFoundRoute'
import { collectRoutePermissions, matchPermissions } from '@/utils/permission'
import { isPortalPublicPath } from '@/utils/portalPublicRoute'
import { clearStoredSession, getValidToken } from '@/utils/session'
import { setRouteTitle } from '@/utils/documentTitle'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/sso/callback',
    name: 'SsoCallback',
    component: () => import('@/views/sso/SsoCallback.vue'),
    meta: { title: '单点登录', requiresAuth: false },
  },
  {
    path: '/portal',
    name: 'Portal',
    component: () => import('@/views/portal/PortalLanding.vue'),
    meta: { title: '产品门户', requiresAuth: false },
  },
  {
    path: '/docs',
    redirect: '/docs/quickstart',
  },
  {
    path: '/docs/:docId',
    name: 'PortalDocs',
    component: () => import('@/views/portal/PortalDocsPage.vue'),
    meta: { title: '文档', requiresAuth: false },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { title: '无权访问', requiresAuth: true },
  },
  {
    path: '/devtools/swagger',
    redirect: '/devtools/api',
  },
  {
    path: '/system/monitor',
    redirect: '/monitor/ops',
  },
  {
    path: '/system/user',
    redirect: '/permission/user',
  },
  {
    path: '/system/menu',
    redirect: '/permission/menu',
  },
  {
    path: '/system/role',
    redirect: '/permission/role',
  },
  {
    path: '/system/unit',
    redirect: '/permission/unit',
  },
  {
    path: '/home/chat',
    redirect: '/ai/chat',
  },
  {
    path: '/home/dashboard',
    redirect: '/ai/chat',
  },
  {
    path: '/ai/document-qa',
    redirect: '/ai/qa',
  },
  {
    path: '/',
    name: LAYOUT_ROUTE_NAME,
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'ai/chat',
        name: 'AIChat',
        component: () => import('@/views/ai/AIChat.vue'),
        meta: { title: 'Agent 会话', requiresAuth: true },
      },
      {
        path: 'monitor/ops',
        name: 'OpsMonitor',
        component: () => import('@/views/system/SystemMonitor.vue'),
        meta: { title: '运维监控', requiresAuth: true },
      },
      {
        path: 'devtools/api',
        name: 'BackendApi',
        component: () => import('@/views/devtools/SwaggerEmbed.vue'),
        meta: {
          title: '后端接口',
          requiresAuth: true,
          hideBreadcrumb: true,
          fullBleed: true,
        },
      },
    ],
  },
  // 通配 404 放在最后；动态路由注册后会重新挂到末尾，避免抢先匹配
  notFoundRoute,
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

const PORTAL_PREVIEW_PRELOAD_ID = 'portal-preview-preload'

/** 进入门户前预取预览图，与异步组件 chunk 并行下载 */
function preloadPortalPreview() {
  if (typeof document === 'undefined') return
  if (document.getElementById(PORTAL_PREVIEW_PRELOAD_ID)) return
  const link = document.createElement('link')
  link.id = PORTAL_PREVIEW_PRELOAD_ID
  link.rel = 'preload'
  link.as = 'image'
  link.href = '/portal/dashboard.png'
  link.type = 'image/png'
  link.setAttribute('fetchpriority', 'high')
  document.head.appendChild(link)
}

function canAccessRoute(
  matched: RouteLocationMatched[],
  userStore: { permissions: string[]; isAdmin: boolean },
) {
  const required = collectRoutePermissions(matched)
  if (!required.length) return true
  return matchPermissions(userStore.permissions, required, { isAdmin: userStore.isAdmin })
}

/** 按 path 重新进入，避免沿用首次匹配到的 NotFound name */
function rematchByPath(fullPath: string) {
  return { path: fullPath, replace: true as const }
}

/** 动态路由已注册后，若仍落在 NotFound，再按 path 解析一次 */
function tryRecoverFromNotFound(fullPath: string) {
  const resolved = router.resolve(fullPath)
  const stillNotFound =
    resolved.name === 'NotFound' ||
    (resolved.matched.length > 0 && resolved.matched.every((record) => record.name === 'NotFound'))
  if (!stillNotFound && resolved.matched.length > 0) {
    return rematchByPath(fullPath)
  }
  return null
}

// 路由守卫：按「公共页 → 登录页 → 鉴权 → 动态路由 → 权限」顺序串联，
// 返回值即导航目标（vue-router 4 推荐写法，避免 next() 嵌套跳转遗漏）
router.beforeEach(async (to) => {
  if (!isPortalPublicPath(to.path)) {
    const { ensureElementPlus } = await import('@/plugins/elementPlus')
    await ensureElementPlus()
  }

  const token = getValidToken()

  // 门户 / 文档：完全公开
  if (to.path === '/portal' || to.name === 'Portal') {
    preloadPortalPreview()
    return true
  }
  if (to.path.startsWith('/docs') || to.name === 'PortalDocs') {
    return true
  }

  // 登录页：已登录则回后台
  if (to.path === '/login' || to.name === 'Login') {
    return token ? { path: '/' } : true
  }
  if (to.path === '/sso/callback' || to.name === 'SsoCallback') {
    return true
  }

  // 深链刷新会先命中静态 NotFound（requiresAuth=false），仍需按有 token 走菜单加载
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)
  const needsAuthGate = requiresAuth || (Boolean(token) && to.name === 'NotFound')

  if (needsAuthGate && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (!token) return true

  const [{ useMenuStore }, { useUserStore }] = await Promise.all([
    import('@/stores/useMenuStore'),
    import('@/stores/useUserStore'),
  ])
  const menuStore = useMenuStore()
  const userStore = useUserStore()
  userStore.initUserInfo()

  if (!menuStore.routesLoaded) {
    try {
      await Promise.all([
        menuStore.fetchAndRegisterRoutes(router),
        userStore.fetchUserPermissions({ silent: true }),
      ])
    } catch (error) {
      console.error('加载用户菜单失败:', error)
      clearStoredSession()
      menuStore.reset(router)
      return { name: 'Login', query: { redirect: to.fullPath } }
    }

    if (to.path === '/' || to.path === '') {
      return { path: menuStore.defaultPath, replace: true }
    }

    // 必须用 path 重匹配：首次命中 NotFound 时 to.name 仍是 NotFound，
    // 按 name 导航会导致刷新后一直 404
    return rematchByPath(to.fullPath)
  }

  if (to.path === '/' || to.path === '') {
    return { path: menuStore.defaultPath, replace: true }
  }

  if (to.name === 'NotFound') {
    return tryRecoverFromNotFound(to.fullPath) ?? true
  }

  if (to.name !== 'Forbidden' && !canAccessRoute(to.matched, userStore)) {
    return { name: 'Forbidden', replace: true }
  }

  return true
})

// 路由级标题：`页面标题 · 系统名`（系统名由 systemConfigStore 通过 setDocumentSystemName 同步）
router.afterEach((to) => {
  const routeTitle = typeof to.meta?.title === 'string' ? to.meta.title : ''
  if (routeTitle) setRouteTitle(routeTitle)
})

export default router
