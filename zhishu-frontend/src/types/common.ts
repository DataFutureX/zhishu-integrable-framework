/**
 * 跨模块共用类型。
 *
 * PageResult 原先在 api/user.ts、types/announcement.ts、types/agentMonitor.ts 各写一份，
 * 后端分页结构变化时容易只改一处；ApiResponse 也曾在 types/index.ts 与 utils/request.ts 重复定义。
 */

/** 后端统一响应包（request 拦截器会解包 data） */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 后端分页结构 */
export interface PageResult<T = unknown> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
