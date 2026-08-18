import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { useMenuStore } from '@/stores/useMenuStore'
import { useTabsStore } from '@/stores/useTabsStore'
import { useAnnouncementStore } from '@/stores/useAnnouncementStore'

let loggingOut = false

/**
 * 退出登录并跳转登录页
 * 使用整页跳转，避免 /login 被解析为 Layout 子路由导致登录页嵌套
 */
export async function logoutAndRedirect(
  router?: Router,
  options?: { silent?: boolean; notifyServer?: boolean },
) {
  if (loggingOut) return
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
  } finally {
    window.location.replace('/login')
  }
}
