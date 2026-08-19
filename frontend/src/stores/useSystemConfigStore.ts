import { defineStore } from 'pinia'
import { resolveAssetUrl } from '@/utils/asset'
import { updateBrowserFavicon } from '@/utils/favicon'
import type { SystemConfigVO } from '@/types/systemConfig'
import defaultSystemIcon from '@/assets/yunqi-icon.png'

let configFetchPromise: Promise<void> | null = null

export const DEFAULT_SYSTEM_NAME = '云起应用平台'
export const DEFAULT_ENGLISH_TITLE = 'YunQi Application Platform'
export const DEFAULT_SYSTEM_ICON = defaultSystemIcon
export const DEFAULT_COPYRIGHT = '© 2026 云起应用平台 · MIT 开源'
export const DEFAULT_SYSTEM_INTRODUCTION =
  '一套面向企业数字化应用建设的模块化开发基础平台，通过统一技术架构、业务组件、AI能力和行业扩展能力，帮助企业快速构建智能化应用系统。'
export const DEFAULT_LOGIN_RETRY_LIMIT_ENABLED = true
export const DEFAULT_LOGIN_MAX_RETRY_ATTEMPTS = 5
export const DEFAULT_LOGIN_LOCK_MINUTES = 30

export const useSystemConfigStore = defineStore('systemConfig', {
  state: () => ({
    systemName: DEFAULT_SYSTEM_NAME,
    englishTitle: DEFAULT_ENGLISH_TITLE,
    systemIcon: '',
    copyright: DEFAULT_COPYRIGHT,
    systemIntroduction: DEFAULT_SYSTEM_INTRODUCTION,
    projectSite: '',
    loginRetryLimitEnabled: DEFAULT_LOGIN_RETRY_LIMIT_ENABLED,
    loginMaxRetryAttempts: DEFAULT_LOGIN_MAX_RETRY_ATTEMPTS,
    loginLockMinutes: DEFAULT_LOGIN_LOCK_MINUTES,
    loaded: false,
  }),

  getters: {
    iconUrl: (state) => resolveAssetUrl(state.systemIcon) || DEFAULT_SYSTEM_ICON,
    displayEnglishTitle: (state) => state.englishTitle || DEFAULT_ENGLISH_TITLE,
  },

  actions: {
    applyConfig(config: Partial<SystemConfigVO>) {
      if (config.systemName) {
        this.systemName = config.systemName
      }
      if (config.englishTitle !== undefined) {
        this.englishTitle = config.englishTitle || ''
      }
      if (config.systemIcon !== undefined) {
        this.systemIcon = config.systemIcon || ''
      }
      if (config.copyright !== undefined) {
        this.copyright = config.copyright || DEFAULT_COPYRIGHT
      }
      if (config.systemIntroduction !== undefined) {
        this.systemIntroduction = config.systemIntroduction
      }
      if (config.projectSite !== undefined) {
        this.projectSite = config.projectSite || ''
      }
      if (config.loginRetryLimitEnabled !== undefined) {
        this.loginRetryLimitEnabled = config.loginRetryLimitEnabled
      }
      if (config.loginMaxRetryAttempts !== undefined) {
        this.loginMaxRetryAttempts = config.loginMaxRetryAttempts
      }
      if (config.loginLockMinutes !== undefined) {
        this.loginLockMinutes = config.loginLockMinutes
      }
      updateBrowserFavicon(this.iconUrl)
      this.loaded = true
    },

    async fetchConfig(options?: { publicOnly?: boolean }) {
      if (configFetchPromise) {
        return configFetchPromise
      }

      configFetchPromise = (async () => {
        try {
          const config = options?.publicOnly
            ? await (await import('@/api/publicSystemConfig')).fetchPublicSystemConfig()
            : await (await import('@/api/systemConfig')).getSystemConfigApi()
          if (config) {
            this.applyConfig(config)
            document.title = this.systemName
          }
        } catch (error) {
          console.error('获取系统配置失败:', error)
        }
      })()

      try {
        await configFetchPromise
      } finally {
        configFetchPromise = null
      }
    },

    /** 确保系统配置已加载 */
    async ensureConfigLoaded() {
      if (configFetchPromise) {
        await configFetchPromise
        return
      }
      if (this.loaded) return
      await this.fetchConfig()
    },
  },
})
