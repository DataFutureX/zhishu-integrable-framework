import { get } from '@/utils/request'
import type { OperationLogQueryDTO, OperationLogVO, PageResult } from '@/types/operationLog'

/** 分页查询操作日志 GET /api/v1/operation-logs/page */
export const getOperationLogPageApi = (params: OperationLogQueryDTO) => {
  return get<PageResult<OperationLogVO>>('/operation-logs/page', { params })
}

/** 查询操作日志详情 GET /api/v1/operation-logs/{id} */
export const getOperationLogDetailApi = (id: number | string) => {
  return get<OperationLogVO>(`/operation-logs/${id}`)
}
