/** 智能体定义 / 能力 / 工作流模板类型 */

export type AgentStatus = 'ENABLED' | 'DISABLED'
export type WorkflowType = 'REACT' | 'SEQUENTIAL' | 'ROUTING' | 'GRAPH'

/** 雪花 ID：超出 JS 安全整数范围，API 响应经 parseJsonWithBigInt 转为字符串 */
export type SnowflakeId = number | string

export type AgentCapabilityCode =
  | 'RAG'
  | 'MEMORY'
  | 'WORKFLOW_GRAPH'
  | 'MCP_TOOLS'
  | 'KNOWLEDGE_GRAPH'
  | 'BRIEFING'

export interface AgentTraceEvent {
  type: string
  name: string
  detail?: string | null
  durationMs?: number | null
  timestamp?: number | null
}

export interface AgentVO {
  id: number
  code: string
  name: string
  description?: string | null
  systemPrompt: string
  model?: string | null
  temperature?: number | null
  maxTokens?: number | null
  capabilities: string[]
  workflowType: WorkflowType | string
  workflowConfig?: string | null
  documentIds?: number[] | null
  mcpUpstreamIds?: number[] | null
  enableMemory: boolean
  modelProviderId?: SnowflakeId | null
  status: AgentStatus | string
  builtin: boolean
  defaultAgent: boolean
  createdBy?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface AgentCreateDTO {
  code: string
  name: string
  description?: string
  systemPrompt: string
  model?: string | null
  temperature?: number | null
  maxTokens?: number | null
  capabilities: string[]
  workflowType: string
  workflowConfig?: string | null
  documentIds?: number[] | null
  mcpUpstreamIds?: number[] | null
  enableMemory: boolean
  modelProviderId?: SnowflakeId | null
  status?: string
}

export interface AgentUpdateDTO {
  name: string
  description?: string
  systemPrompt: string
  model?: string | null
  temperature?: number | null
  maxTokens?: number | null
  capabilities: string[]
  workflowType: string
  workflowConfig?: string | null
  documentIds?: number[] | null
  mcpUpstreamIds?: number[] | null
  enableMemory: boolean
  modelProviderId?: SnowflakeId | null
  status: string
}

export interface AgentPromptDraftDTO {
  name?: string
  description?: string
  capabilities?: string[]
  workflowType?: string
  enableMemory?: boolean
  mcpUpstreamNames?: string[]
  documentNames?: string[]
  existingPrompt?: string
}

export interface AgentPromptDraftVO {
  prompt: string
  source: 'LLM' | 'TEMPLATE' | string
}

export interface ToolInfoVO {
  name: string
  description: string
}

export interface CapabilityVO {
  code: string
  label: string
  description: string
  toolBased: boolean
  toolNames: string[]
  /** 绑定 Tools（含 @Tool 描述） */
  tools?: ToolInfoVO[]
}

export interface WorkflowTemplateVO {
  code: string
  label: string
  description: string
}

export interface AgentTrialDTO {
  message: string
  enableRag?: boolean
  conversationId?: string
  enableMemory?: boolean
}

export interface AgentRunVO {
  id: number
  agentId: number
  conversationId?: string | null
  status: string
  currentNode?: string | null
  stateJson?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface GraphNodeDTO {
  id: string
  type: string
  label?: string
  data?: Record<string, unknown>
  positionX?: number | null
  positionY?: number | null
}

export interface GraphEdgeDTO {
  id: string
  source: string
  target: string
  condition?: string | null
  label?: string | null
}

export interface WorkflowGraphDTO {
  version: number
  nodes: GraphNodeDTO[]
  edges: GraphEdgeDTO[]
}

export interface GraphValidationResult {
  valid: boolean
  errors: string[]
}
