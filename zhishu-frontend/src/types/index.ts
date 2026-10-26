// 通用响应 / 分页类型统一在 types/common.ts
// 用户状态枚举
export enum UserStatus {
  ACTIVE = 1,
  INACTIVE = 0,
  DISABLED = -1,
}

// 角色枚举
export enum Role {
  ADMIN = 'admin',
  USER = 'user',
  GUEST = 'guest',
}

// 主题风格枚举（顺序即切换器展示顺序；云起浅色置顶且为默认）
export enum ThemeStyle {
  LIGHT = 'light', // 云起浅色（默认）
  BLUE = 'blue', // 夜空
  DARK = 'dark', // 极客黑
}

// 主题配置接口
export interface ThemeConfig {
  style: ThemeStyle
  sidebarBgColor: string
  sidebarTextColor: string
  sidebarActiveColor: string
  primaryColor: string
  primaryDark: string
  headerBg: string
  headerText: string
  headerShadow: string
  contentBg: string
  contentBgGradient: string
  surfaceBg: string
  surfaceMuted: string
  borderColor: string
  textPrimary: string
  textRegular: string
  textSecondary: string
}
