import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Router } from 'vue-router'
import { getCurrentUserMenusApi } from '@/api/menu'
import type { MenuVO } from '@/types/menu'
import {
  addDynamicRoutes,
  resetDynamicRoutes,
  filterVisibleMenus,
  findFirstMenuPath,
} from '@/router/dynamicRoutes'
import { applyTerminologyToMenus } from '@/utils/terminology'
import { filterNavigationMenus, isProfileMenu } from '@/utils/menuNavigation'
import { HOME_DASHBOARD_PATH } from '@/constants/app'

export const useMenuStore = defineStore('menu', () => {
  const menuTree = ref<MenuVO[]>([])
  const routesLoaded = ref(false)
  const defaultPath = ref(HOME_DASHBOARD_PATH)

  const sidebarMenus = computed(() => filterVisibleMenus(menuTree.value))

  const navigationMenus = computed(() => filterNavigationMenus(sidebarMenus.value))

  /** 侧边栏布局主导航（含个人中心） */
  const sidebarNavigationMenus = computed(() =>
    filterNavigationMenus(sidebarMenus.value, { includeProfile: true }),
  )

  const profileMenu = computed(() => sidebarMenus.value.find((menu) => isProfileMenu(menu)))

  const fetchAndRegisterRoutes = async (router: Router) => {
    const menus = applyTerminologyToMenus(await getCurrentUserMenusApi())
    menuTree.value = menus
    defaultPath.value = findFirstMenuPath(menus) || HOME_DASHBOARD_PATH
    addDynamicRoutes(router, menus)
    routesLoaded.value = true
    return menus
  }

  const reset = (router?: Router) => {
    if (router) {
      resetDynamicRoutes(router)
    }
    menuTree.value = []
    routesLoaded.value = false
    defaultPath.value = HOME_DASHBOARD_PATH
  }

  return {
    menuTree,
    routesLoaded,
    defaultPath,
    sidebarMenus,
    navigationMenus,
    sidebarNavigationMenus,
    profileMenu,
    fetchAndRegisterRoutes,
    reset,
  }
})
