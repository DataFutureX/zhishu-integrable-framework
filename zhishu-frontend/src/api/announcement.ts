import { get, post, put, del } from '@/utils/request'
import type {
  AnnouncementCreateDTO,
  AnnouncementQueryDTO,
  AnnouncementUpdateDTO,
  AnnouncementVO,
  PageResult,
} from '@/types/announcement'

/** 管理员分页查询 GET /api/v1/announcements/page */
export function getAnnouncementPageApi(params: AnnouncementQueryDTO) {
  return get<PageResult<AnnouncementVO>>('/announcements/page', { params })
}

/** 已发布公告分页查询 GET /api/v1/announcements/published/page */
export function getPublishedAnnouncementPageApi(params: AnnouncementQueryDTO) {
  return get<PageResult<AnnouncementVO>>('/announcements/published/page', { params })
}

/** 最近公告 GET /api/v1/announcements/recent */
export function getRecentAnnouncementsApi(limit = 10) {
  return get<AnnouncementVO[]>('/announcements/recent', { params: { limit } })
}

/** 未读数量 GET /api/v1/announcements/unread-count */
export function getAnnouncementUnreadCountApi() {
  return get<number>('/announcements/unread-count')
}

/** 公告详情 GET /api/v1/announcements/{id} */
export function getAnnouncementDetailApi(id: number | string) {
  return get<AnnouncementVO>(`/announcements/${id}`)
}

/** 创建公告 POST /api/v1/announcements */
export function createAnnouncementApi(data: AnnouncementCreateDTO) {
  return post<AnnouncementVO>('/announcements', data)
}

/** 更新公告 PUT /api/v1/announcements */
export function updateAnnouncementApi(data: AnnouncementUpdateDTO) {
  return put<AnnouncementVO>('/announcements', data)
}

/** 删除公告 DELETE /api/v1/announcements/{id} */
export function deleteAnnouncementApi(id: number | string) {
  return del<void>(`/announcements/${id}`)
}

/** 发布公告 PUT /api/v1/announcements/{id}/publish */
export function publishAnnouncementApi(id: number | string) {
  return put<AnnouncementVO>(`/announcements/${id}/publish`)
}

/** 撤回公告 PUT /api/v1/announcements/{id}/revoke */
export function revokeAnnouncementApi(id: number | string) {
  return put<AnnouncementVO>(`/announcements/${id}/revoke`)
}

/** 标记已读 PUT /api/v1/announcements/{id}/read */
export function markAnnouncementReadApi(id: number | string) {
  return put<void>(`/announcements/${id}/read`)
}

/** 全部标记已读 PUT /api/v1/announcements/read-all */
export function markAllAnnouncementsReadApi() {
  return put<number>('/announcements/read-all')
}

/** 构建公告 SSE 订阅地址 */
export function buildAnnouncementStreamUrl(token: string): string {
  const encodedToken = encodeURIComponent(token)
  if (import.meta.env.DEV) {
    return `/api/v1/announcements/stream?token=${encodedToken}`
  }
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return `${base}/v1/announcements/stream?token=${encodedToken}`
}

export type { AnnouncementVO }
