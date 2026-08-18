/** 公告优先级 */
export enum AnnouncementPriority {
  NORMAL = 0,
  IMPORTANT = 1,
  URGENT = 2,
}

/** 公告状态 */
export enum AnnouncementStatus {
  DRAFT = 0,
  PUBLISHED = 1,
  REVOKED = 2,
}

export const ANNOUNCEMENT_PRIORITY_LABEL: Record<number, string> = {
  [AnnouncementPriority.NORMAL]: '普通',
  [AnnouncementPriority.IMPORTANT]: '重要',
  [AnnouncementPriority.URGENT]: '紧急',
}

export const ANNOUNCEMENT_STATUS_LABEL: Record<number, string> = {
  [AnnouncementStatus.DRAFT]: '草稿',
  [AnnouncementStatus.PUBLISHED]: '已发布',
  [AnnouncementStatus.REVOKED]: '已撤回',
}

export interface AnnouncementVO {
  id: number | string
  title?: string
  content?: string
  priority?: number
  status?: number
  publishTime?: string
  publisherId?: number | string
  publisherName?: string
  read?: boolean
  createTime?: string
  updateTime?: string
}

export interface AnnouncementQueryDTO {
  pageNum?: number
  pageSize?: number
  title?: string
  priority?: number
  status?: number
  unreadOnly?: boolean
  startTime?: string
  endTime?: string
}

export interface AnnouncementCreateDTO {
  title: string
  content: string
  priority?: number
  publishImmediately?: boolean
}

export interface AnnouncementUpdateDTO {
  id: number | string
  title?: string
  content?: string
  priority?: number
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
