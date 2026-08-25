import type { RouteRecordRaw } from 'vue-router'

/** 通配 404：须始终挂在路由表末尾，避免抢先匹配动态页 */
export const notFoundRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'NotFound',
  component: () => import('@/views/error/NotFound.vue'),
  meta: { title: '页面不存在', requiresAuth: false },
}

export function ensureNotFoundRoute(router: { hasRoute: (name: string) => boolean; removeRoute: (name: string) => void; addRoute: (route: RouteRecordRaw) => void }) {
  if (router.hasRoute('NotFound')) {
    router.removeRoute('NotFound')
  }
  router.addRoute(notFoundRoute)
}
