import axios from 'axios'

import { isDemoMode } from '@/config/demo'
import type { SystemConfigVO } from '@/types/systemConfig'

function apiBaseUrl(): string {
  if (isDemoMode) return '/api/v1'
  return import.meta.env.DEV ? '/api/v1' : `${import.meta.env.VITE_API_BASE_URL}/v1`
}

/** 门户 / 登录页等未鉴权场景拉取系统配置（不依赖 request 拦截器，避免拉起 Element Plus） */
export async function fetchPublicSystemConfig(): Promise<SystemConfigVO | null> {
  try {
    const { data } = await axios.get<{ code: number; data: SystemConfigVO; message?: string }>(
      `${apiBaseUrl()}/system-config`,
      { timeout: 8000 },
    )
    if (data.code === 200 && data.data) return data.data
    return null
  } catch {
    return null
  }
}
