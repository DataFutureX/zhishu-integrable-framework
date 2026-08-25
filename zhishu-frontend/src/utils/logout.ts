import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { useMenuStore } from '@/stores/useMenuStore'
import { useTabsStore } from '@/stores/useTabsStore'
import { useAnnouncementStore } from '@/stores/useAnnouncementStore'

import { isPublicAppPath } from '@/utils/session'

let loggingOut = false

/**
 * 退出登录并跳转登录页（优先 SPA 路由，避免整页刷新闪烁）
 */
export async function logoutAndRedirect(
  router?: Router,
  options?: { silent?: boolean; notifyServer?: boolean },
) {
  if (loggingOut) return
  if (isPublicAppPath(window.location.pathname)) return

  loggingOut = true

  const userStore = useUserStore()
  const menuStore = useMenuStore()
  const tabsStore = useTabsStore()
  const announcementStore = useAnnouncementStore()

  try {
    announcementStore.destroy()
    await userStore.logout({
      silent: options?.silent === true,
      notifyServer: options?.notifyServer !== false,
    })
    menuStore.reset(router)
    tabsStore.reset()

    const activeRouter = router ?? (await import('@/router')).default
    const redirect = activeRouter.currentRoute.value.fullPath
    await activeRouter.replace({
      name: 'Login',
      query: redirect && redirect !== '/login' ? { redirect } : undefined,
    })
  } catch (error) {
    console.warn('路由跳转登录页失败，回退整页跳转', error)
    window.location.replace('/login')
  } finally {
    loggingOut = false
  }
}
