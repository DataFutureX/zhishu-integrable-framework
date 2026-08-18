import { ThemeStyle, type ThemeConfig } from '@/types'
import { hydroThemePalette, lightThemePalette } from '@/config/themes'

export type ContentScheme = 'light' | 'dark'

export interface ContentSchemeTokens {
  contentBg: string
  contentBgGradient: string
  surfaceBg: string
  surfaceMuted: string
  borderColor: string
  textPrimary: string
  textRegular: string
  textSecondary: string
}

/** 通用浅色内容区（与侧栏/顶栏主题独立） */
const lightContentTokens: ContentSchemeTokens = {
  contentBg: lightThemePalette.contentBg,
  contentBgGradient: lightThemePalette.contentBgGradient,
  surfaceBg: lightThemePalette.surfaceBg,
  surfaceMuted: lightThemePalette.surfaceMuted,
  borderColor: lightThemePalette.borderColor,
  textPrimary: lightThemePalette.textPrimary,
  textRegular: lightThemePalette.textRegular,
  textSecondary: lightThemePalette.textSecondary,
}

/** 通用深色内容区（GitHub dark） */
const darkContentTokens: ContentSchemeTokens = {
  contentBg: '#0d1117',
  contentBgGradient: 'linear-gradient(180deg, #0d1117 0%, #010409 100%)',
  surfaceBg: '#161b22',
  surfaceMuted: '#21262d',
  borderColor: '#30363d',
  textPrimary: '#e6edf3',
  textRegular: '#c9d1d9',
  textSecondary: '#8b949e',
}

/** 夜空主题下的深色内容区 */
const hydroDarkContentTokens: ContentSchemeTokens = {
  contentBg: hydroThemePalette.deep,
  contentBgGradient: `linear-gradient(180deg, ${hydroThemePalette.ocean} 0%, ${hydroThemePalette.deep} 100%)`,
  surfaceBg: hydroThemePalette.surface,
  surfaceMuted: hydroThemePalette.surfaceMuted,
  borderColor: hydroThemePalette.border,
  textPrimary: hydroThemePalette.textPrimary,
  textRegular: hydroThemePalette.textRegular,
  textSecondary: hydroThemePalette.textSecondary,
}

export const resolveContentSchemeTokens = (
  scheme: ContentScheme,
  theme?: ThemeConfig,
): ContentSchemeTokens => {
  if (scheme === 'light') {
    return lightContentTokens
  }

  if (theme?.style === ThemeStyle.BLUE) {
    return hydroDarkContentTokens
  }

  return darkContentTokens
}

const CONTENT_TOKEN_KEYS: (keyof ContentSchemeTokens)[] = [
  'contentBg',
  'contentBgGradient',
  'surfaceBg',
  'surfaceMuted',
  'borderColor',
  'textPrimary',
  'textRegular',
  'textSecondary',
]

const CSS_VAR_MAP: Record<keyof ContentSchemeTokens, string> = {
  contentBg: '--app-content-bg',
  contentBgGradient: '--app-content-bg-gradient',
  surfaceBg: '--app-surface-bg',
  surfaceMuted: '--app-surface-muted',
  borderColor: '--app-border-color',
  textPrimary: '--app-text-primary',
  textRegular: '--app-text-regular',
  textSecondary: '--app-text-secondary',
}

/** 将内容区配色写入 document（内联样式，优先级高于主题默认值） */
export function applyContentSchemeVariables(
  scheme: ContentScheme,
  theme?: ThemeConfig,
): void {
  const root = document.documentElement
  const tokens = resolveContentSchemeTokens(scheme, theme)

  for (const key of CONTENT_TOKEN_KEYS) {
    root.style.setProperty(CSS_VAR_MAP[key], tokens[key])
  }
}
