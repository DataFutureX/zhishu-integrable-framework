export interface OperationLogVO {
  id: number | string
  userId?: number | string
  username?: string
  realName?: string
  module?: string
  operation?: string
  method?: string
  requestParams?: string
  responseCode?: number
  ipAddress?: string
  userAgent?: string
  durationMs?: number
  /** 1-成功，0-失败 */
  status?: number
  errorMessage?: string
  createTime?: string
}

export interface OperationLogQueryDTO {
  pageNum?: number
  pageSize?: number
  username?: string
  module?: string
  operation?: string
  status?: number
  startTime?: string
  endTime?: string
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
