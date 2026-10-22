/** 模型网关（Model Gateway）类型定义 */

/** 供应商（只含连接与凭据） */
export interface GatewayProviderVO {
  id: number
  name: string
  providerKey: string
  baseUrl: string
  apiKeyMasked?: string | null
  apiKeyConfigured?: boolean | null
  isDefault?: boolean | null
  status: 'ENABLED' | 'DISABLED' | string
  sortOrder?: number | null
  remark?: string | null
  chatModelCount: number
  embeddingModelCount: number
  updateTime?: string | null
}

export interface GatewayProviderUpsertDTO {
  name: string
  providerKey?: string
  baseUrl: string
  apiKey?: string | null
  isDefault?: boolean
  status?: string
  sortOrder?: number
  remark?: string | null
}

export type GatewayModelType = 'CHAT' | 'EMBEDDING'

export interface GatewayModelVO {
  id: number
  modelId: string
  displayName: string
  providerId: number
  providerName?: string | null
  providerKey?: string | null
  upstreamModel: string
  modelType?: GatewayModelType | string | null
  contextWindow?: number | null
  maxOutputTokens?: number | null
  inputModalities?: string | null
  systemPromptUpdate?: string | null
  reasoningEfforts?: string | null
  defaultReasoningEffort?: string | null
  clientRequirements?: string | null
  extraParams?: string | null
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  isDefault?: boolean | null
  status: 'ENABLED' | 'DISABLED' | string
  sortOrder?: number | null
  remark?: string | null
  upstreamUsable?: boolean | null
  createTime?: string | null
  updateTime?: string | null
}

export interface GatewayModelUpsertDTO {
  modelId: string
  displayName: string
  providerId: number
  upstreamModel: string
  modelType?: GatewayModelType | string
  contextWindow?: number | null
  maxOutputTokens?: number | null
  inputModalities?: string | null
  systemPromptUpdate?: string | null
  reasoningEfforts?: string | null
  defaultReasoningEffort?: string | null
  clientRequirements?: string | null
  extraParams?: string | null
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  isDefault?: boolean
  status?: string
  sortOrder?: number
  remark?: string | null
}

export interface GatewayQuotaVO {
  id: number
  subjectType: 'USER' | 'ROLE' | 'DEFAULT' | string
  subjectKey: string
  periodKind: string
  tokenLimit?: number | null
  usedTokens?: number | null
  remaining?: number | null
  periodStart?: string | null
  periodEnd?: string | null
  status: string
  remark?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface GatewayQuotaUpsertDTO {
  subjectType: string
  subjectKey: string
  periodKind?: string
  tokenLimit?: number | null
  status?: string
  remark?: string | null
}

export interface GatewayUsageVO {
  id: number
  requestId: string
  userId?: string | null
  username?: string | null
  channel: string
  modelId: string
  providerId?: number | null
  upstreamModel?: string | null
  stream?: boolean | null
  status: string
  httpStatus?: number | null
  reservedTokens?: number | null
  promptTokens?: number | null
  completionTokens?: number | null
  totalTokens?: number | null
  cachedTokens?: number | null
  reasoningTokens?: number | null
  estimated?: boolean | null
  latencyMs?: number | null
  ttftMs?: number | null
  clientName?: string | null
  clientVersion?: string | null
  ip?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  createTime?: string | null
}

export interface GatewayUsageBucket {
  key: string
  requests: number
  tokens: number
}

export interface GatewayUsageSummaryVO {
  totalRequests: number
  totalTokens: number
  successCount: number
  errorCount: number
  estimatedCount: number
  byModel: GatewayUsageBucket[]
  byUser: GatewayUsageBucket[]
  byDay: GatewayUsageBucket[]
}

export interface GatewayPageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
