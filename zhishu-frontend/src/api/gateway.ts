import { del, get, post, put } from '@/utils/request'
import type {
  GatewayModelUpsertDTO,
  GatewayModelVO,
  GatewayPageResult,
  GatewayProviderUpsertDTO,
  GatewayProviderVO,
  GatewayQuotaUpsertDTO,
  GatewayQuotaVO,
  GatewayModelImportItem,
  GatewayUpstreamModel,
  GatewayUsageSummaryVO,
  GatewayUsageVO,
} from '@/types/modelGateway'

/** 供应商列表 GET /api/v1/model-gateway/providers */
export const listGatewayProvidersApi = () => {
  return get<GatewayProviderVO[]>('/model-gateway/providers')
}

/** 新建供应商 POST /api/v1/model-gateway/providers */
export const createGatewayProviderApi = (data: GatewayProviderUpsertDTO) => {
  return post<GatewayProviderVO>('/model-gateway/providers', data)
}

/** 修改供应商 PUT /api/v1/model-gateway/providers/{id} */
export const updateGatewayProviderApi = (id: number, data: GatewayProviderUpsertDTO) => {
  return put<GatewayProviderVO>(`/model-gateway/providers/${id}`, data)
}

/** 删除供应商 DELETE /api/v1/model-gateway/providers/{id} */
export const deleteGatewayProviderApi = (id: number) => {
  return del(`/model-gateway/providers/${id}`)
}

/** 设为默认供应商 POST /api/v1/model-gateway/providers/{id}/default */
export const setDefaultGatewayProviderApi = (id: number) => {
  return post<GatewayProviderVO>(`/model-gateway/providers/${id}/default`)
}

/** 供应商连通性测试 POST /api/v1/model-gateway/providers/{id}/test */
export const testGatewayProviderApi = (id: number) => {
  return post<string>(`/model-gateway/providers/${id}/test`)
}

/** 拉取上游模型列表（含元数据） GET /api/v1/model-gateway/providers/{id}/upstream-models */
export const listUpstreamModelsApi = (id: number | string) => {
  return get<GatewayUpstreamModel[]>(`/model-gateway/providers/${id}/upstream-models`)
}

/** 模型列表 GET /api/v1/model-gateway/models */
export const listGatewayModelsApi = (params?: { type?: string; providerId?: number }) => {
  return get<GatewayModelVO[]>('/model-gateway/models', { params })
}

/** 可绑定模型（启用且上游可用） GET /api/v1/model-gateway/models/available */
export const listAvailableGatewayModelsApi = () => {
  return get<GatewayModelVO[]>('/model-gateway/models/available')
}

/** 网关模型详情 GET /api/v1/model-gateway/models/{id} */
export const getGatewayModelApi = (id: number) => {
  return get<GatewayModelVO>(`/model-gateway/models/${id}`)
}

/** 新建网关模型 POST /api/v1/model-gateway/models */
export const createGatewayModelApi = (data: GatewayModelUpsertDTO) => {
  return post<GatewayModelVO>('/model-gateway/models', data)
}

/** 修改网关模型 PUT /api/v1/model-gateway/models/{id} */
export const updateGatewayModelApi = (id: number, data: GatewayModelUpsertDTO) => {
  return put<GatewayModelVO>(`/model-gateway/models/${id}`, data)
}

/** 删除网关模型 DELETE /api/v1/model-gateway/models/{id} */
export const deleteGatewayModelApi = (id: number) => {
  return del(`/model-gateway/models/${id}`)
}

/** 设为默认模型 POST /api/v1/model-gateway/models/{id}/default */
export const setDefaultGatewayModelApi = (id: number) => {
  return post<GatewayModelVO>(`/model-gateway/models/${id}/default`)
}

/** 启用/停用 POST /api/v1/model-gateway/models/{id}/enable|disable */
export const changeGatewayModelStatusApi = (id: number, enabled: boolean) => {
  return post<GatewayModelVO>(`/model-gateway/models/${id}/${enabled ? 'enable' : 'disable'}`)
}

/** 端到端连通测试 POST /api/v1/model-gateway/models/{id}/test */
export const testGatewayModelApi = (id: number) => {
  return post<string>(`/model-gateway/models/${id}/test`)
}

/** 从上游批量导入 POST /api/v1/model-gateway/models/batch */
export const batchImportGatewayModelsApi = (data: {
  providerId: number
  modelType?: string
  models: GatewayModelImportItem[]
}) => {
  return post<GatewayModelVO[]>('/model-gateway/models/batch', data)
}

/** 额度策略列表 GET /api/v1/model-gateway/quotas */
export const listGatewayQuotasApi = (subjectType?: string) => {
  return get<GatewayQuotaVO[]>('/model-gateway/quotas', {
    params: subjectType ? { subjectType } : undefined,
  })
}

/** 新建额度策略 POST /api/v1/model-gateway/quotas */
export const createGatewayQuotaApi = (data: GatewayQuotaUpsertDTO) => {
  return post<GatewayQuotaVO>('/model-gateway/quotas', data)
}

/** 修改额度策略 PUT /api/v1/model-gateway/quotas/{id} */
export const updateGatewayQuotaApi = (id: number, data: GatewayQuotaUpsertDTO) => {
  return put<GatewayQuotaVO>(`/model-gateway/quotas/${id}`, data)
}

/** 删除额度策略 DELETE /api/v1/model-gateway/quotas/{id} */
export const deleteGatewayQuotaApi = (id: number) => {
  return del(`/model-gateway/quotas/${id}`)
}

/** 清零已用量 POST /api/v1/model-gateway/quotas/{id}/reset */
export const resetGatewayQuotaApi = (id: number) => {
  return post<GatewayQuotaVO>(`/model-gateway/quotas/${id}/reset`)
}

/** 调用台账分页 GET /api/v1/model-gateway/usages */
export const pageGatewayUsagesApi = (params: {
  username?: string
  modelId?: string
  channel?: string
  status?: string
  from?: string
  to?: string
  current?: number
  size?: number
}) => {
  return get<GatewayPageResult<GatewayUsageVO>>('/model-gateway/usages', { params })
}

/** 调用汇总 GET /api/v1/model-gateway/usages/summary */
export const summaryGatewayUsagesApi = (params?: { from?: string; to?: string }) => {
  return get<GatewayUsageSummaryVO>('/model-gateway/usages/summary', { params })
}
