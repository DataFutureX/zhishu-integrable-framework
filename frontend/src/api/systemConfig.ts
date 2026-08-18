import { get, put, post } from '@/utils/request'
import type { SystemConfigVO, SystemConfigUpdateDTO } from '@/types/systemConfig'

/** 获取系统配置 GET /api/v1/system-config */
export const getSystemConfigApi = () => {
  return get<SystemConfigVO>('/system-config')
}

/** 登录页等未鉴权场景获取系统配置（失败时不弹全局错误提示） */
export const getPublicSystemConfigApi = async (): Promise<SystemConfigVO | null> => {
  try {
    return await get<SystemConfigVO>('/system-config', { skipErrorMessage: true })
  } catch {
    return null
  }
}

/** 更新系统配置 PUT /api/v1/system-config */
export const updateSystemConfigApi = (data: SystemConfigUpdateDTO) => {
  return put<SystemConfigVO>('/system-config', data)
}

/** 上传系统图标 POST /api/v1/system-config/icon */
export const uploadSystemIconApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return post<string>('/system-config/icon', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
