import { ThemeStyle, type ThemeConfig } from '@/types'

/** 夜空主题色系（深色运维场景，浅蓝点缀） */
export const hydroThemePalette = {
  deep: '#061018',
  ocean: '#0a1f33',
  cyan: '#2f84e8',
  cyanBright: '#7dd3fc',
  aqua: '#bae6fd',
  surface: 'rgba(8, 40, 68, 0.78)',
  surfaceMuted: 'rgba(6, 28, 48, 0.7)',
  border: 'rgba(125, 211, 252, 0.24)',
  textPrimary: '#f0f9ff',
  textRegular: 'rgba(186, 230, 253, 0.9)',
  textSecondary: 'rgba(125, 211, 252, 0.62)',
  headerBg: 'linear-gradient(180deg, rgba(8, 32, 56, 0.98) 0%, rgba(6, 20, 36, 0.96) 100%)',
  headerShadow:
    '0 4px 24px rgba(0, 12, 28, 0.5), 0 1px 0 rgba(125, 211, 252, 0.12) inset',
} as const

/** 云起浅色主题色系（GitHub 式中性白灰 + Primer 蓝） */
export const lightThemePalette = {
  sidebarBg: '#ffffff',
  sidebarText: '#656d76',
  sidebarActive: '#0969da',
  primary: '#0969da',
  primaryDark: '#0550ae',
  headerBg: '#ffffff',
  headerText: '#1f2328',
  headerShadow: '0 1px 0 #d0d7de',
  contentBg: '#f6f8fa',
  contentBgGradient: 'linear-gradient(180deg, #ffffff 0%, #f6f8fa 100%)',
  surfaceBg: '#ffffff',
  surfaceMuted: '#f6f8fa',
  borderColor: '#d0d7de',
  textPrimary: '#1f2328',
  textRegular: '#424a53',
  textSecondary: '#656d76',
} as const

/** 全局主题配置（侧栏 + 顶栏 + 内容区 Design Token；顺序与 ThemeStyle 枚举一致） */
export const themeConfigs: Record<ThemeStyle, ThemeConfig> = {
  [ThemeStyle.LIGHT]: {
    style: ThemeStyle.LIGHT,
    sidebarBgColor: lightThemePalette.sidebarBg,
    sidebarTextColor: lightThemePalette.sidebarText,
    sidebarActiveColor: lightThemePalette.sidebarActive,
    primaryColor: lightThemePalette.primary,
    primaryDark: lightThemePalette.primaryDark,
    headerBg: lightThemePalette.headerBg,
    headerText: lightThemePalette.headerText,
    headerShadow: lightThemePalette.headerShadow,
    contentBg: lightThemePalette.contentBg,
    contentBgGradient: lightThemePalette.contentBgGradient,
    surfaceBg: lightThemePalette.surfaceBg,
    surfaceMuted: lightThemePalette.surfaceMuted,
    borderColor: lightThemePalette.borderColor,
    textPrimary: lightThemePalette.textPrimary,
    textRegular: lightThemePalette.textRegular,
    textSecondary: lightThemePalette.textSecondary,
  },
  [ThemeStyle.BLUE]: {
    style: ThemeStyle.BLUE,
    sidebarBgColor: hydroThemePalette.deep,
    sidebarTextColor: hydroThemePalette.textRegular,
    sidebarActiveColor: hydroThemePalette.cyanBright,
    primaryColor: hydroThemePalette.cyanBright,
    primaryDark: hydroThemePalette.cyan,
    headerBg: hydroThemePalette.headerBg,
    headerText: 'rgba(240, 249, 255, 0.92)',
    headerShadow: hydroThemePalette.headerShadow,
    contentBg: hydroThemePalette.deep,
    contentBgGradient: `linear-gradient(180deg, ${hydroThemePalette.ocean} 0%, ${hydroThemePalette.deep} 100%)`,
    surfaceBg: hydroThemePalette.surface,
    surfaceMuted: hydroThemePalette.surfaceMuted,
    borderColor: hydroThemePalette.border,
    textPrimary: hydroThemePalette.textPrimary,
    textRegular: hydroThemePalette.textRegular,
    textSecondary: hydroThemePalette.textSecondary,
  },
  [ThemeStyle.DARK]: {
    style: ThemeStyle.DARK,
    sidebarBgColor: '#010409',
    sidebarTextColor: '#8b949e',
    sidebarActiveColor: '#58a6ff',
    primaryColor: '#2f81f7',
    primaryDark: '#1f6feb',
    headerBg: '#0d1117',
    headerText: '#e6edf3',
    headerShadow: '0 1px 0 #30363d',
    contentBg: '#0d1117',
    contentBgGradient: 'linear-gradient(180deg, #0d1117 0%, #010409 100%)',
    surfaceBg: '#161b22',
    surfaceMuted: '#21262d',
    borderColor: '#30363d',
    textPrimary: '#e6edf3',
    textRegular: '#c9d1d9',
    textSecondary: '#8b949e',
  },
}

export const themeLabels: Record<ThemeStyle, string> = {
  [ThemeStyle.LIGHT]: '云起浅色',
  [ThemeStyle.BLUE]: '夜空',
  [ThemeStyle.DARK]: '极客黑',
}

/** 主题切换器展示顺序（云起浅色置顶） */
export const themeOrder: ThemeStyle[] = [
  ThemeStyle.LIGHT,
  ThemeStyle.BLUE,
  ThemeStyle.DARK,
]

/** 将主题配置写入 document CSS 变量（壳层：顶栏/侧栏/主色，不含内容区） */
export function applyThemeVariables(config: ThemeConfig): void {
  const root = document.documentElement
  root.dataset.theme = config.style
  root.style.setProperty('--app-primary', config.primaryColor)
  root.style.setProperty('--app-primary-dark', config.primaryDark)
  root.style.setProperty('--app-header-bg', config.headerBg)
  root.style.setProperty('--app-header-text', config.headerText)
  root.style.setProperty('--app-header-shadow', config.headerShadow)
  root.style.setProperty('--app-sidebar-bg', config.sidebarBgColor)
  root.style.setProperty('--app-sidebar-text', config.sidebarTextColor)
  root.style.setProperty('--app-sidebar-active', config.sidebarActiveColor)
  root.style.setProperty('--el-color-primary', config.primaryColor)
  root.style.setProperty('--el-color-primary-dark-2', config.primaryDark)
}
