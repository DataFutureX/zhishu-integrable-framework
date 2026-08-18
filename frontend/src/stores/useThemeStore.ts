import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ThemeStyle, type ThemeConfig } from '@/types'
import { applyThemeVariables, themeConfigs, themeOrder } from '@/config/themes'
import { useLayoutStore } from '@/stores/useLayoutStore'

const STORAGE_KEY = 'theme_config'
/** 默认主题：云起浅色 */
export const DEFAULT_THEME = ThemeStyle.LIGHT

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeStyle>(DEFAULT_THEME)

  const themeConfig = computed<ThemeConfig>(() => themeConfigs[currentTheme.value])

  const applyThemeToDocument = () => {
    applyThemeVariables(themeConfig.value)
    useLayoutStore().syncContentSchemeWithTheme(currentTheme.value)
  }

  const initTheme = () => {
    const savedTheme = localStorage.getItem(STORAGE_KEY) as ThemeStyle | null
    if (savedTheme && themeConfigs[savedTheme]) {
      currentTheme.value = savedTheme
    } else {
      currentTheme.value = DEFAULT_THEME
    }
    applyThemeToDocument()
  }

  const setTheme = (theme: ThemeStyle) => {
    if (!themeConfigs[theme]) return
    currentTheme.value = theme
    localStorage.setItem(STORAGE_KEY, theme)
    applyThemeToDocument()
  }

  const toggleTheme = () => {
    const currentIndex = themeOrder.indexOf(currentTheme.value)
    const nextIndex = (currentIndex + 1) % themeOrder.length
    setTheme(themeOrder[nextIndex])
  }

  return {
    currentTheme,
    themeConfig,
    initTheme,
    setTheme,
    toggleTheme,
    applyThemeToDocument,
  }
})
