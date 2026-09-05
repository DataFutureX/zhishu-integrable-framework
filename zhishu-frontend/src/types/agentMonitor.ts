/** Agent 执行监控相关类型 */

import type { AgentTraceEvent } from '@/types/aiAgent'

/** 执行记录列表 VO */
export interface AgentExecutionVO {
  id: number
  agentId: number
  agentName: string
  userMessage?: string | null
  responseSummary?: string | null
  status: 'SUCCESS' | 'FAILED' | 'RUNNING' | string
  durationMs?: number | null
  modelName?: string | null
  workflowType?: string | null
  runType: 'CHAT' | 'TRIAL' | string
  ttftMs?: number | null
  tpotMs?: number | null
  tokenCount?: number | null
  userId?: string | null
  createTime?: string | null
}

/** 执行详情 VO（含轨迹） */
export interface AgentExecutionDetailVO extends AgentExecutionVO {
  errorMessage?: string | null
  traces: AgentTraceEvent[]
  updateTime?: string | null
}

/** 统计概览 VO */
export interface AgentMonitorStatsVO {
  totalCount: number
  successCount: number
  failedCount: number
  runningCount: number
  successRate: number
  avgDurationMs: number
  todayCount: number
}

/** 按智能体聚合统计 */
export interface AgentStatsByAgentVO {
  agentId: number
  agentName: string
  totalCount: number
  successCount: number
  successRate: number
  avgDurationMs: number
}

/** 执行列表查询参数 */
export interface AgentMonitorQuery {
  agentId?: number | null
  status?: string | null
  runType?: string | null
  keyword?: string | null
  startTime?: string | null
  endTime?: string | null
  page?: number
  size?: number
}

/** 分页结果 */
export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
