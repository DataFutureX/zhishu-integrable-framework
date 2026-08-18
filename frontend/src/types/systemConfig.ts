export interface SystemConfigVO {
  id?: number | string
  systemName: string
  englishTitle?: string
  systemIcon?: string
  copyright?: string
  systemIntroduction?: string
  /** 项目地（可选，后端兼容字段） */
  projectSite?: string
  /** 是否启用登录重试限制 */
  loginRetryLimitEnabled?: boolean
  /** 登录最大重试次数 */
  loginMaxRetryAttempts?: number
  /** 登录锁定时长（分钟） */
  loginLockMinutes?: number
  createTime?: string
  updateTime?: string
}

export interface SystemConfigUpdateDTO {
  systemName: string
  englishTitle?: string
  systemIcon?: string
  copyright?: string
  systemIntroduction?: string
  projectSite?: string
  loginRetryLimitEnabled: boolean
  loginMaxRetryAttempts: number
  loginLockMinutes: number
}
