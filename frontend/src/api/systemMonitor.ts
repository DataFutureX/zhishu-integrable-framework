import { get } from '@/utils/request'
import type { SystemHealthDTO, SystemStatusDTO } from '@/types/systemMonitor'

/** 获取系统综合运行状态 GET /api/v1/system/status */
export const getSystemStatusApi = () => {
  return get<SystemStatusDTO>('/system/status')
}

/** 系统健康检查 GET /api/v1/system/health */
export const getSystemHealthApi = () => {
  return get<SystemHealthDTO>('/system/health')
}
