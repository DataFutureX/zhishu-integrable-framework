import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMenuStore } from '@/stores/useMenuStore'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { findFirstMenuPath } from '@/router/dynamicRoutes'
import type { MenuVO } from '@/types/menu'
import { isProfileRoute, normalizeMenuPath } from '@/utils/menuNavigation'

export const menuContainsPath = (menu: MenuVO, currentPath: string): boolean => {
  const menuPath = normalizeMenuPath(menu.path)
  if (currentPath === menuPath || currentPath.startsWith(`${menuPath}/`)) {
    return true
  }
  return (menu.children || []).some((child) => menuContainsPath(child, currentPath))
}

export const findActiveTopMenu = (menus: MenuVO[], currentPath: string): MenuVO | null => {
  for (const menu of menus) {
    if (menuContainsPath(menu, currentPath)) {
      return menu
    }
  }
  return menus[0] ?? null
}

const getVisibleMenuChildren = (menus: MenuVO[] = []) =>
  menus.filter((menu) => menu.visible !== 0 && menu.menuType !== 'PAGE' && menu.menuType !== 'BUTTON')

export function useLayoutMenu() {
  const route = useRoute()
  const router = useRouter()
  const menuStore = useMenuStore()
  const layoutStore = useLayoutStore()

  const topMenus = computed(() => menuStore.navigationMenus)

  const profileSecondaryMenus = computed(() =>
    getVisibleMenuChildren(menuStore.profileMenu?.children),
  )

  const activeTopMenu = computed(() => {
    if (isProfileRoute(route.path)) return null
    return findActiveTopMenu(topMenus.value, route.path) ?? topMenus.value[0] ?? null
  })

  const activeTopMenuPath = computed(() => {
    if (isProfileRoute(route.path)) {
      return normalizeMenuPath(menuStore.profileMenu?.path) || '/profile'
    }
    return normalizeMenuPath(activeTopMenu.value?.path)
  })

  const secondaryMenus = computed(() => {
    if (isProfileRoute(route.path)) {
      return profileSecondaryMenus.value
    }
    return getVisibleMenuChildren(activeTopMenu.value?.children)
  })

  const showSecondaryAside = computed(() => {
    // 侧边栏/无顶栏模式：所有菜单已在左侧主导航展示，不再显示二级侧栏
    if (layoutStore.isSidebarLayout || layoutStore.isImmersiveLayout) return false
    if (isProfileRoute(route.path)) {
      return profileSecondaryMenus.value.length > 0
    }
    return secondaryMenus.value.length > 0
  })

  const activeMenu = computed(() => route.path)

  const navigateToTopMenu = (menu: MenuVO) => {
    if (menu.children?.length) {
      const targetPath = findFirstMenuPath(menu.children) || menu.redirect || menu.path
      if (targetPath) {
        router.push(normalizeMenuPath(targetPath))
      }
      return
    }

    if (menu.path) {
      router.push(normalizeMenuPath(menu.path))
    }
  }

  return {
    topMenus,
    activeTopMenu,
    activeTopMenuPath,
    secondaryMenus,
    profileSecondaryMenus,
    showSecondaryAside,
    activeMenu,
    navigateToTopMenu,
    normalizeMenuPath,
  }
}
