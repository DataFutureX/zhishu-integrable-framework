export interface McpOverviewVO {
  serverEnabled: boolean
  endpoint: string
  clientCount: number
  upstreamCount: number
  enabledUpstreamCount: number
  cryptoConfigured: boolean
}

export interface McpClientVO {
  id: number
  name: string
  keyPrefix: string
  boundUserId: number
  boundUsername?: string | null
  capabilities?: string[] | null
  rpmLimit: number
  status: string
  remark?: string | null
  lastUsedAt?: string | null
  createdBy?: string | null
  createTime?: string | null
  apiKey?: string | null
}

export interface McpClientCreateDTO {
  name: string
  boundUserId: number
  boundUsername?: string
  capabilities?: string[]
  rpmLimit?: number
  remark?: string
}

export interface McpClientUpdateDTO {
  name: string
  boundUserId: number
  boundUsername?: string
  capabilities?: string[]
  rpmLimit?: number
  status: string
  remark?: string
}

export interface McpUpstreamVO {
  id: number
  code: string
  name: string
  protocol: string
  baseUrl: string
  endpoint?: string | null
  hasAuthHeader?: boolean
  requestTimeoutMs?: number | null
  status: string
  healthStatus?: string | null
  healthMessage?: string | null
  lastProbeAt?: string | null
  remark?: string | null
  toolCount?: number
  createTime?: string | null
}

export interface McpUpstreamUpsertDTO {
  code: string
  name: string
  protocol?: string
  baseUrl: string
  endpoint?: string
  authHeader?: string
  requestTimeoutMs?: number
  status?: string
  remark?: string
}

export interface McpUpstreamToolVO {
  originalName: string
  exposedName: string
  description?: string | null
  enabled: boolean
}

export interface McpCallLogVO {
  id?: number
  direction?: string | null
  clientId?: number | null
  upstreamId?: number | null
  agentId?: number | null
  toolName?: string | null
  success?: boolean
  errorMessage?: string | null
  durationMs?: number | null
  userId?: string | null
  createTime?: string | null
}
