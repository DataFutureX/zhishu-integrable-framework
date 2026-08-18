import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

import { useMenuStore } from '@/stores/useMenuStore'
import { useUserStore } from '@/stores/useUserStore'
import { HOME_DASHBOARD_PATH } from '@/constants/app'
import { LAYOUT_ROUTE_NAME } from '@/router/dynamicRoutes'
import { collectRoutePermissions, matchPermissions } from '@/utils/permission'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/portal',
    name: 'Portal',
    component: () => import('@/views/portal/PortalLanding.vue'),
    meta: { title: '产品门户', requiresAuth: false },
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
    path: '/',
    name: LAYOUT_ROUTE_NAME,
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', requiresAuth: false },
  },
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
  matched: RouteRecordRaw['children'] extends infer _T ? typeof router.currentRoute.value.matched : never,
  userStore: ReturnType<typeof useUserStore>,
) {
  const required = collectRoutePermissions(matched)
  if (!required.length) return true
  return matchPermissions(userStore.permissions, required, { isAdmin: userStore.isAdmin })
}

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)

  if (to.path === '/portal' || to.name === 'Portal') {
    preloadPortalPreview()
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

  if (requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (token) {
    const menuStore = useMenuStore()
    const userStore = useUserStore()
    userStore.initUserInfo()

    if (!menuStore.routesLoaded) {
      try {
        await Promise.all([
          menuStore.fetchAndRegisterRoutes(router),
          userStore.fetchUserPermissions(),
        ])

        if (to.path === '/' || to.path === '') {
          next({ path: HOME_DASHBOARD_PATH, replace: true })
          return
        }

        // 动态路由刚注册，需重新匹配（含原先落入 NotFound 的目标路径）
        next({ path: to.fullPath, replace: true })
        return
      } catch (error) {
        console.error('加载用户菜单失败:', error)

        localStorage.removeItem('token')

        sessionStorage.removeItem('userInfo')
        sessionStorage.removeItem('permissions')

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
