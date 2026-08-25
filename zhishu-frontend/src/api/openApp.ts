import { get, post, put, del } from '@/utils/request'
import type { OpenAppVO, GenerateAkSkResult } from '@/types/openApp'

/** 开放应用列表 GET /api/v1/open-apps */
export const listOpenAppsApi = () => {
  return get<OpenAppVO[]>('/open-apps')
}

/** 新建开放应用 POST /api/v1/open-apps */
export const createOpenAppApi = (data: {
  code: string
  name: string
  remark?: string
  allowedScopes?: string[]
}) => {
  return post<OpenAppVO>('/open-apps', data)
}

/** 编辑开放应用 PUT /api/v1/open-apps/{id} */
export const updateOpenAppApi = (
  id: number,
  data: {
    code: string
    name: string
    remark?: string
    allowedScopes?: string[]
  },
) => {
  return put<OpenAppVO>(`/open-apps/${id}`, data)
}

/** 移除开放应用 DELETE /api/v1/open-apps/{id} */
export const deleteOpenAppApi = (id: number) => {
  return del(`/open-apps/${id}`)
}

/** 开放应用详情 GET /api/v1/open-apps/{id} */
export const getOpenAppApi = (id: number) => {
  return get<OpenAppVO>(`/open-apps/${id}`)
}

/** 生成或重新生成 AK/SK POST /api/v1/open-apps/{id}/generate-aksk */
export const generateAkSkApi = (id: number) => {
  return post<GenerateAkSkResult>(`/open-apps/${id}/generate-aksk`)
}

/** 仅重新生成 SK POST /api/v1/open-apps/{id}/regenerate-sk */
export const regenerateSkApi = (id: number) => {
  return post<GenerateAkSkResult>(`/open-apps/${id}/regenerate-sk`)
}

/** 更新调用范围 PUT /api/v1/open-apps/{id}/scopes */
export const updateOpenAppScopesApi = (id: number, scopes: string[]) => {
  return put<OpenAppVO>(`/open-apps/${id}/scopes`, { scopes })
}

/** 启用/停用 PUT /api/v1/open-apps/{id}/status */
export const updateOpenAppStatusApi = (id: number, status: string) => {
  return put<OpenAppVO>(`/open-apps/${id}/status`, { status })
}
