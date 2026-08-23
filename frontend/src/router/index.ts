import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

import { HOME_DASHBOARD_PATH } from '@/constants/app'
import { LAYOUT_ROUTE_NAME } from '@/router/layoutRoute'
import { notFoundRoute } from '@/router/notFoundRoute'
import { collectRoutePermissions, matchPermissions } from '@/utils/permission'
import { isPortalPublicPath } from '@/utils/portalPublicRoute'
import { clearStoredSession, getValidToken } from '@/utils/session'

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
    path: '/ai/chat',
    redirect: '/home/chat',
  },
  {
    path: '/ai/briefings',
    redirect: '/home/briefings',
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
  link.href = '/portal/dashboard.webp'
  link.type = 'image/webp'
  link.setAttribute('fetchpriority', 'high')
  document.head.appendChild(link)
}

function canAccessRoute(
  matched: RouteRecordRaw['children'] extends infer _T
    ? typeof router.currentRoute.value.matched
    : never,
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

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  if (!isPortalPublicPath(to.path)) {
    await import('@/plugins/elementPlus').then(({ ensureElementPlus }) => ensureElementPlus())
  }

  const token = getValidToken()

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)

  if (to.path === '/portal' || to.name === 'Portal') {
    preloadPortalPreview()
    next()
    return
  }

  if (to.path.startsWith('/docs') || to.name === 'PortalDocs') {
    next()
    return
  }

  if (to.path === '/login' || to.name === 'Login') {
    if (token) {
      next('/')
    } else {
      next()
    }
    return
  }

  if (to.path === '/sso/callback' || to.name === 'SsoCallback') {
    next()
    return
  }

  // 深链刷新会先命中静态 NotFound（requiresAuth=false），仍需按有 token 走菜单加载
  const needsAuthGate = requiresAuth || (Boolean(token) && to.name === 'NotFound')

  if (needsAuthGate && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (token) {
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

        if (to.path === '/' || to.path === '') {
          next({ path: HOME_DASHBOARD_PATH, replace: true })
          return
        }

        // 必须用 path 重匹配：首次命中 NotFound 时 to.name 仍是 NotFound，
        // next({ ...to }) 会按 name 导航，导致刷新后一直 404
        next(rematchByPath(to.fullPath))
        return
      } catch (error) {
        console.error('加载用户菜单失败:', error)

        clearStoredSession()

        menuStore.reset(router)

        next({ name: 'Login', query: { redirect: to.fullPath } })
        return
      }
    }

    if (to.path === '/' || to.path === '') {
      next({ path: HOME_DASHBOARD_PATH, replace: true })
      return
    }

    if (to.name === 'NotFound') {
      const recovered = tryRecoverFromNotFound(to.fullPath)
      if (recovered) {
        next(recovered)
        return
      }
      next()
      return
    }

    if (to.name !== 'Forbidden' && !canAccessRoute(to.matched, userStore)) {
      next({ name: 'Forbidden', replace: true })
      return
    }

    next()
    return
  }

  if (to.name === 'NotFound') {
    next()
    return
  }

  next()
})

export default router
