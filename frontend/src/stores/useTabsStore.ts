import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import { HOME_DASHBOARD_PATH } from '@/constants/app'
import { LAYOUT_ROUTE_NAME } from '@/router/layoutRoute'
import type { LayoutTab } from '@/types/tabs'

const resolveTabTitle = (route: RouteLocationNormalized) => {
  const matched = [...route.matched].reverse().find((record) => record.meta?.title)
  return (matched?.meta?.title as string) || ''
}

const shouldTrackTab = (route: RouteLocationNormalized) => {
  if (!route.name || route.name === LAYOUT_ROUTE_NAME || route.name === 'Login') {
    return false
  }
  return !!resolveTabTitle(route)
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<LayoutTab[]>([])
  const refreshKeys = ref<Record<string, number>>({})

  const addTab = (route: RouteLocationNormalized) => {
    if (!shouldTrackTab(route)) return

    const title = resolveTabTitle(route)
    const existing = tabs.value.find((tab) => tab.fullPath === route.fullPath)
    if (existing) {
      existing.title = title
      return
    }

    tabs.value.push({
      path: route.path,
      fullPath: route.fullPath,
      title,
      name: route.name ?? undefined,
      affix: false,
    })
  }

  const removeTab = (fullPath: string) => {
    const index = tabs.value.findIndex((tab) => tab.fullPath === fullPath)
    if (index === -1) return null

    tabs.value.splice(index, 1)
    return index
  }

  const closeOtherTabs = (fullPath: string) => {
    tabs.value = tabs.value.filter((tab) => tab.fullPath === fullPath)
  }

  const closeAllTabs = () => {
    tabs.value = []
    return HOME_DASHBOARD_PATH
  }

  const reset = () => {
    tabs.value = []
    refreshKeys.value = {}
  }

  const getRefreshKey = (fullPath: string) => refreshKeys.value[fullPath] ?? 0

  const refreshTab = (fullPath: string) => {
    refreshKeys.value[fullPath] = getRefreshKey(fullPath) + 1
  }

  return {
    tabs,
    addTab,
    removeTab,
    closeOtherTabs,
    closeAllTabs,
    reset,
    getRefreshKey,
    refreshTab,
  }
})
