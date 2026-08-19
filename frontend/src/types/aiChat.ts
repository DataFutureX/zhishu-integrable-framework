/** AI 聊天 / 结构化输出类型（对齐后端 P0/P1） */

export interface ChatRequestDTO {
  message: string
  maxTokens?: number
  temperature?: number
  /** 多轮会话 ID，后续请求需回传 */
  conversationId?: string
  /** 是否启用向量知识库增强（RAG Advisor） */
  enableRag?: boolean
  /** 智能体 ID；为空则使用默认智能体 */
  agentId?: number
}

export interface AgentTraceEvent {
  type: string
  name: string
  detail?: string | null
  durationMs?: number | null
  timestamp?: number | null
}

export interface ChatResponseVO {
  content: string
  timestamp: string
  model: string
  conversationId?: string
  structured?: StationCompareResult | TrendAnalysisResult | AlarmSummary | null
  agentId?: number | null
  traces?: AgentTraceEvent[] | null
}

export type StructuredChatType = 'COMPARE' | 'TREND' | 'ALARM'

export interface ChatStructuredRequestDTO {
  message: string
  type: StructuredChatType
  conversationId?: string
  maxTokens?: number
  temperature?: number
  agentId?: number
}

export interface StationCompareItem {
  stationAddress: string
  observeTime?: string | null
  value?: number | null
  remark?: string | null
}

export interface StationCompareResult {
  element: string
  summary: string
  items: StationCompareItem[]
}

export interface TrendPoint {
  observeTime: string
  value: number
}

export interface TrendAnalysisResult {
  stationAddress: string
  element: string
  startTime: string
  endTime: string
  sampleCount: number
  min?: number | null
  max?: number | null
  avg?: number | null
  sum?: number | null
  trend: string
  summary: string
  points: TrendPoint[]
}

export interface AlarmItem {
  stationAddress: string
  element: string
  currentValue: number
  threshold: number
  observeTime?: string | null
  message: string
}

export interface AlarmSummary {
  level: string
  totalCount: number
  summary: string
  items: AlarmItem[]
}

export function isStationCompareResult(v: unknown): v is StationCompareResult {
  return !!v && typeof v === 'object' && 'items' in v && 'element' in v && !('level' in v) && !('trend' in v && 'points' in v)
}

export function isTrendAnalysisResult(v: unknown): v is TrendAnalysisResult {
  return !!v && typeof v === 'object' && 'trend' in v && 'points' in v && 'stationAddress' in v
}

export function isAlarmSummary(v: unknown): v is AlarmSummary {
  return !!v && typeof v === 'object' && 'level' in v && 'items' in v && 'totalCount' in v
}
