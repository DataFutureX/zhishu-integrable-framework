import type { PageResult } from '@/types/announcement'

/** 简报投递 VO */
export interface BriefingDeliveryVO {
  id: number
  scheduleId?: number | null
  triggerType?: string | null
  triggerRef?: string | null
  userId?: string | null
  agentId?: number | null
  runId?: number | null
  title?: string | null
  contentMd?: string | null
  status?: string | null
  errorMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  readAt?: string | null
  emailStatus?: string | null
  createTime?: string | null
}

/** 简报调度 VO */
export interface BriefingScheduleVO {
  id: number
  name: string
  agentId?: number | null
  promptTemplate?: string | null
  scopeType?: string | null
  scheduleType: string
  scheduleTime?: string | null
  scheduleDays?: string | null
  cronExpr?: string | null
  timezone?: string | null
  nextRunAt?: string | null
  lastRunAt?: string | null
  notifyBell?: boolean | null
  notifyEmail?: boolean | null
  emailToMode?: string | null
  emailExtraTo?: string | null
  emailSubjectTemplate?: string | null
  enabled?: boolean | null
  createdBy?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** 简报投递统计 */
export interface BriefingStatsVO {
  total: number
  success: number
  failed: number
  unread: number
  pendingOrRunning: number
}

/** 创建/更新简报调度 */
export interface BriefingScheduleUpsertDTO {
  name: string
  agentId?: number | null
  promptTemplate?: string | null
  scopeType?: string | null
  scheduleType: 'DAILY' | 'WEEKLY' | 'CRON' | string
  scheduleTime?: string | null
  scheduleDays?: string | null
  cronExpr?: string | null
  timezone?: string | null
  notifyBell?: boolean
  notifyEmail?: boolean
  emailToMode?: string | null
  emailExtraTo?: string | null
  emailSubjectTemplate?: string | null
  enabled?: boolean
}

export type BriefingDeliveryPageResult = PageResult<BriefingDeliveryVO>

export const BRIEFING_STATUS_LABEL: Record<string, string> = {
  PENDING: '待生成',
  RUNNING: '生成中',
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '已跳过',
}

export const BRIEFING_EMAIL_STATUS_LABEL: Record<string, string> = {
  NONE: '未发送',
  PENDING: '待发送',
  SENT: '已发送',
  FAILED: '发送失败',
  SKIPPED: '已跳过',
}

export const BRIEFING_SCHEDULE_TYPE_LABEL: Record<string, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  CRON: 'Cron',
}
