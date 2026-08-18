import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { applyContentSchemeVariables, type ContentScheme } from '@/config/contentScheme'
import { themeConfigs } from '@/config/themes'
import { ThemeStyle } from '@/types'

export type { ContentScheme } from '@/config/contentScheme'

export type LayoutMode = 'sidebar' | 'hybrid'

/** 页面导航上下文展示方式（标签栏与面包屑互斥） */
export type NavContextDisplay = 'tabs' | 'breadcrumb' | 'none'

/** 内容区视觉密度（仅影响页内边距与列表排版，不改变顶栏高度） */
export type LayoutDensity = 'comfortable' | 'compact'
const STORAGE_KEY = 'layout_mode'
const TAB_BAR_STORAGE_KEY = 'show_tab_bar'
const BREADCRUMB_STORAGE_KEY = 'show_breadcrumb'
const NAV_CONTEXT_STORAGE_KEY = 'nav_context_display'
const DENSITY_STORAGE_KEY = 'layout_density'
const CONTENT_SCHEME_STORAGE_KEY = 'content_scheme'
const DEFAULT_LAYOUT_MODE: LayoutMode = 'hybrid'
const DEFAULT_NAV_CONTEXT: NavContextDisplay = 'tabs'

const isValidLayoutMode = (value: string | null): value is LayoutMode =>
  value === 'sidebar' || value === 'hybrid'

const isValidNavContextDisplay = (value: string | null): value is NavContextDisplay =>
  value === 'tabs' || value === 'breadcrumb' || value === 'none'

const isValidDensity = (value: string | null): value is LayoutDensity =>
  value === 'comfortable' || value === 'compact'

const isValidContentScheme = (value: string | null): value is ContentScheme =>
  value === 'light' || value === 'dark'

const readLayoutMode = (): LayoutMode => {
  const saved = localStorage.getItem(STORAGE_KEY)
  return isValidLayoutMode(saved) ? saved : DEFAULT_LAYOUT_MODE
}

const readNavContextDisplay = (): NavContextDisplay => {
  const saved = localStorage.getItem(NAV_CONTEXT_STORAGE_KEY)
  if (isValidNavContextDisplay(saved)) return saved

  const tabBarEnabled = localStorage.getItem(TAB_BAR_STORAGE_KEY) !== 'false'
  const breadcrumbEnabled = localStorage.getItem(BREADCRUMB_STORAGE_KEY) === 'true'
  if (tabBarEnabled) return 'tabs'
  if (breadcrumbEnabled) return 'breadcrumb'
  return DEFAULT_NAV_CONTEXT
}

const readDensity = (): LayoutDensity => {
  const saved = localStorage.getItem(DENSITY_STORAGE_KEY)
  return isValidDensity(saved) ? saved : 'comfortable'
}

const readContentScheme = (): ContentScheme => {
  const saved = localStorage.getItem(CONTENT_SCHEME_STORAGE_KEY)
  return isValidContentScheme(saved) ? saved : 'light'
}

const applyDocumentLayoutPrefs = (density: LayoutDensity, contentScheme: ContentScheme) => {
  document.documentElement.dataset.density = density
  document.documentElement.dataset.contentScheme = contentScheme
  applyContentSchemeVariables(contentScheme, themeConfigs[readThemeStyle()])
}

const readThemeStyle = (): ThemeStyle => {
  const saved = localStorage.getItem('theme_config') as ThemeStyle | null
  return saved && themeConfigs[saved] ? saved : ThemeStyle.LIGHT
}

const applyNavContextDisplay = (
  mode: NavContextDisplay,
  showTabBar: { value: boolean },
  showBreadcrumb: { value: boolean },
) => {
  showTabBar.value = mode === 'tabs'
  showBreadcrumb.value = mode === 'breadcrumb'
  localStorage.setItem(NAV_CONTEXT_STORAGE_KEY, mode)
  localStorage.setItem(TAB_BAR_STORAGE_KEY, String(mode === 'tabs'))
  localStorage.setItem(BREADCRUMB_STORAGE_KEY, String(mode === 'breadcrumb'))
}

export const useLayoutStore = defineStore('layout', () => {
  const layoutMode = ref<LayoutMode>(readLayoutMode())
  const navContextDisplay = ref<NavContextDisplay>(readNavContextDisplay())
  const showTabBar = ref(navContextDisplay.value === 'tabs')
  const showBreadcrumb = ref(navContextDisplay.value === 'breadcrumb')
  const density = ref<LayoutDensity>(readDensity())
  const contentScheme = ref<ContentScheme>(readContentScheme())

  const isHybridLayout = computed(() => layoutMode.value === 'hybrid')
  const isSidebarLayout = computed(() => layoutMode.value === 'sidebar')

  const setLayoutMode = (mode: LayoutMode) => {
    layoutMode.value = mode
    localStorage.setItem(STORAGE_KEY, mode)
  }

  const toggleLayoutMode = () => {
    setLayoutMode(layoutMode.value === 'sidebar' ? 'hybrid' : 'sidebar')
  }

  const setNavContextDisplay = (mode: NavContextDisplay) => {
    navContextDisplay.value = mode
    applyNavContextDisplay(mode, showTabBar, showBreadcrumb)
  }

  const initLayoutMode = () => {
    layoutMode.value = readLayoutMode()
    setNavContextDisplay(readNavContextDisplay())
    density.value = readDensity()
    contentScheme.value = readContentScheme()
    applyDocumentLayoutPrefs(density.value, contentScheme.value)
  }

  const setDensity = (value: LayoutDensity) => {
    density.value = value
    localStorage.setItem(DENSITY_STORAGE_KEY, value)
    applyDocumentLayoutPrefs(value, contentScheme.value)
  }

  const setContentScheme = (value: ContentScheme) => {
    contentScheme.value = value
    localStorage.setItem(CONTENT_SCHEME_STORAGE_KEY, value)
    applyDocumentLayoutPrefs(density.value, value)
  }

  /** 主题切换后重新应用内容区配色（需传入当前主题配置） */
  const syncContentSchemeWithTheme = (themeStyle: ThemeStyle = readThemeStyle()) => {
    applyContentSchemeVariables(contentScheme.value, themeConfigs[themeStyle])
  }

  const setShowTabBar = (visible: boolean) => {
    setNavContextDisplay(visible ? 'tabs' : showBreadcrumb.value ? 'breadcrumb' : 'none')
  }

  const toggleTabBar = () => {
    setNavContextDisplay(showTabBar.value ? 'none' : 'tabs')
  }

  const setShowBreadcrumb = (visible: boolean) => {
    setNavContextDisplay(visible ? 'breadcrumb' : showTabBar.value ? 'tabs' : 'none')
  }

  const toggleBreadcrumb = () => {
    setNavContextDisplay(showBreadcrumb.value ? 'none' : 'breadcrumb')
  }

  /** 登录成功后应用默认顶部菜单布局 */
  const applyLoginDefaultLayout = () => {
    setLayoutMode(DEFAULT_LAYOUT_MODE)
  }

  return {
    layoutMode,
    navContextDisplay,
    showTabBar,
    showBreadcrumb,
    density,
    contentScheme,
    isHybridLayout,
    isSidebarLayout,
    setLayoutMode,
    toggleLayoutMode,
    initLayoutMode,
    applyLoginDefaultLayout,
    setNavContextDisplay,
    setShowTabBar,
    toggleTabBar,
    setShowBreadcrumb,
    toggleBreadcrumb,
    setDensity,
    setContentScheme,
    syncContentSchemeWithTheme,
  }
})
