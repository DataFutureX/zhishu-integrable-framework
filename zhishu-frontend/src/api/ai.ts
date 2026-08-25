import axios from 'axios'
import { ElMessage } from 'element-plus'
import { isDemoMode } from '@/config/demo'
import {
  mockAiChat,
  mockAiDiagnose,
  mockAiHealth,
  mockChatStream,
  mockChatStructured,
  mockClearQaHistory,
  mockDocumentDelete,
  mockDocumentDetail,
  mockDocumentList,
  mockDocumentQa,
  mockDocumentQaStream,
  mockDocumentReprocess,
  mockDocumentUpload,
  mockListQaHistory,
  mockTruncateChatSession,
} from '@/mock/ai'
import type {
  ChatRequestDTO,
  ChatResponseVO,
  ChatStructuredRequestDTO,
} from '@/types/aiChat'
import type { AiModelConfigUpdateDTO, AiModelConfigVO } from '@/types/aiModelConfig'
import type {
  AgentCreateDTO,
  AgentPromptDraftDTO,
  AgentPromptDraftVO,
  AgentRunVO,
  AgentTrialDTO,
  AgentUpdateDTO,
  AgentVO,
  CapabilityVO,
  GraphValidationResult,
  WorkflowGraphDTO,
  WorkflowTemplateVO,
} from '@/types/aiAgent'
import type {
  KnowledgesCategoryCreateDTO,
  KnowledgesCategoryUpdateDTO,
  KnowledgesCategoryVO,
  DocumentQueryDTO,
  DocumentUploadParams,
  DocumentVO,
} from '@/types/aiDocument'
import type { QaHistoryScene, QaHistoryVO } from '@/types/qaHistory'
import type {
  ChatSessionCreateDTO,
  ChatSessionTitleDTO,
  ChatSessionVO,
} from '@/types/chatSession'
import type {
  KgHealthVO,
  KgNeighborResult,
  KgPathResult,
  KgSearchHit,
  KgStatsVO,
  KgSubgraphVO,
  KgSyncResult,
  KgSyncStatusVO,
  KgTopologySummary,
} from '@/types/knowledgeGraph'
import type {
  McpCallLogVO,
  McpClientCreateDTO,
  McpClientUpdateDTO,
  McpClientVO,
  McpOverviewVO,
  McpUpstreamToolVO,
  McpUpstreamUpsertDTO,
  McpUpstreamVO,
} from '@/types/mcp'
import { postAiSse, type AiSseHandlers } from '@/utils/aiSse'
import { resolveAiUserId } from '@/utils/aiUser'
import { parseJsonWithBigInt } from '@/utils/parseJson'

export type { ChatRequestDTO, ChatResponseVO, ChatStructuredRequestDTO }

/**
 * AI 助手相关接口（聊天 / 知识管理 / 知识问答）
 */

const aiService = axios.create({
  baseURL: import.meta.env.DEV ? '/api/v1' : import.meta.env.VITE_API_BASE_URL + '/v1',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
  // 雪花 ID 超出 JS 安全整数，需在 JSON.parse 前转为字符串（与主站 request 一致）
  transformResponse: [
    (data) => {
      if (typeof data === 'object' && data !== null) {
        return data
      }
      if (typeof data === 'string') {
        return parseJsonWithBigInt(data) ?? data
      }
      return data
    },
  ],
})

aiService.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.set?.('Authorization', `Bearer ${token}`)
      if (!config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    const userId = resolveAiUserId()
    if (userId) {
      config.headers.set?.('X-User-Id', userId)
      if (!config.headers['X-User-Id']) {
        config.headers['X-User-Id'] = userId
      }
    } else {
      console.warn('[AI] 缺少用户 ID，问答历史将无法按用户记录/加载')
    }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  },
)

aiService.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    console.error('Response error:', error)
    let message = '网络错误'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请重新登录'
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址不存在'
          break
        case 500:
          message = '服务器错误'
          break
        default:
          message = `连接错误${error.response.status}`
      }
    } else {
      message = '网络连接异常'
    }
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/** 同步聊天（多轮请传 conversationId） */
export function chat(data: ChatRequestDTO): Promise<ChatResponseVO> {
  if (isDemoMode) return mockAiChat(data)
  return aiService.post('/chat', data)
}

/** 流式聊天 SSE */
export function chatStream(
  data: ChatRequestDTO,
  handlers: AiSseHandlers,
  signal?: AbortSignal,
): Promise<void> {
  if (isDemoMode) return mockChatStream(data, handlers)
  return postAiSse('/chat/stream', data, handlers, signal)
}

/** 结构化输出（对比 / 趋势 / 告警） */
export function chatStructured(data: ChatStructuredRequestDTO): Promise<ChatResponseVO> {
  if (isDemoMode) return mockChatStructured(data)
  return aiService.post('/chat/structured', data)
}

export function healthCheck(): Promise<string> {
  if (isDemoMode) return mockAiHealth()
  return aiService.get('/chat/health')
}

export function diagnose(): Promise<string> {
  if (isDemoMode) return mockAiDiagnose()
  return aiService.get('/chat/diagnose')
}

/** 对话运行指标快照 */
export function chatMetrics(): Promise<Record<string, unknown>> {
  if (isDemoMode) {
    return Promise.resolve({ totalCalls: 0, successCalls: 0, failCalls: 0, demo: true })
  }
  return aiService.get('/chat/metrics')
}

/** Memory / Vector / Hybrid 冒烟评测 */
export function chatEval(): Promise<Record<string, unknown>> {
  if (isDemoMode) {
    return Promise.resolve({
      passed: true,
      passedCount: 3,
      totalCount: 3,
      checks: [
        { name: 'chatMemoryBean', passed: true, detail: 'demo' },
        { name: 'vectorStoreBean', passed: true, detail: 'demo' },
        { name: 'hybridRetrieval', passed: true, detail: 'demo' },
      ],
    })
  }
  return aiService.get('/chat/eval')
}

export function getKnowledgesCategoryList(includeDisabled = false): Promise<KnowledgesCategoryVO[]> {
  if (isDemoMode) {
    return import('@/mock/ai').then((m) => m.mockListKnowledgesCategories())
  }
  return aiService.get('/knowledges-categories', { params: { includeDisabled } })
}

/** @deprecated 使用 getKnowledgesCategoryList */
export const getDocumentCategoryList = getKnowledgesCategoryList

export function createKnowledgesCategory(data: KnowledgesCategoryCreateDTO): Promise<KnowledgesCategoryVO> {
  if (isDemoMode) {
    return import('@/mock/ai').then((m) => m.mockCreateKnowledgesCategory(data))
  }
  return aiService.post('/knowledges-categories', data)
}

/** @deprecated 使用 createKnowledgesCategory */
export const createDocumentCategory = createKnowledgesCategory

export function updateKnowledgesCategory(
  id: string,
  data: KnowledgesCategoryUpdateDTO,
): Promise<KnowledgesCategoryVO> {
  if (isDemoMode) {
    return import('@/mock/ai').then((m) => m.mockUpdateKnowledgesCategory(id, data))
  }
  return aiService.put(`/knowledges-categories/${encodeURIComponent(id)}`, data)
}

/** @deprecated 使用 updateKnowledgesCategory */
export const updateDocumentCategory = updateKnowledgesCategory

export function deleteKnowledgesCategory(id: string): Promise<void> {
  if (isDemoMode) {
    return import('@/mock/ai').then((m) => m.mockDeleteKnowledgesCategory(id))
  }
  return aiService.delete(`/knowledges-categories/${encodeURIComponent(id)}`)
}

/** @deprecated 使用 deleteKnowledgesCategory */
export const deleteDocumentCategory = deleteKnowledgesCategory

export function getDocumentList(categoryId?: string): Promise<DocumentVO[]> {
  if (isDemoMode) return mockDocumentList(categoryId)
  return aiService.get('/knowledges', {
    params: categoryId ? { categoryId } : undefined,
  })
}

export function getDocumentDetail(id: string): Promise<DocumentVO> {
  if (isDemoMode) return mockDocumentDetail(id)
  return aiService.get(`/knowledges/${encodeURIComponent(id)}`)
}

export function uploadDocument(params: DocumentUploadParams): Promise<DocumentVO> {
  if (isDemoMode) return mockDocumentUpload(params)
  const formData = new FormData()
  formData.append('file', params.file)
  return aiService.post('/knowledges/upload', formData, {
    params: {
      title: params.title,
      categoryId: params.categoryId || undefined,
    },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export function updateDocumentCategoryBinding(
  id: string,
  categoryId: string,
): Promise<DocumentVO> {
  if (isDemoMode) {
    return import('@/mock/ai').then((m) => m.mockBindDocumentCategory(id, categoryId))
  }
  return aiService.put(`/knowledges/${encodeURIComponent(id)}/category`, { categoryId: Number(categoryId) })
}

export function deleteDocument(id: string): Promise<void> {
  if (isDemoMode) return mockDocumentDelete(id)
  return aiService.delete(`/knowledges/${encodeURIComponent(id)}`)
}

export function reprocessDocument(id: string): Promise<DocumentVO> {
  if (isDemoMode) return mockDocumentReprocess(id)
  return aiService.post(`/knowledges/${encodeURIComponent(id)}/reprocess`, null, { timeout: 120000 })
}

export function documentQa(data: DocumentQueryDTO): Promise<ChatResponseVO> {
  if (isDemoMode) return mockDocumentQa(data)
  return aiService.post('/knowledges/qa', data, { timeout: 120000 })
}

/** 知识库问答流式 SSE */
export function documentQaStream(
  data: DocumentQueryDTO,
  handlers: AiSseHandlers,
  signal?: AbortSignal,
): Promise<void> {
  if (isDemoMode) return mockDocumentQaStream(data, handlers)
  return postAiSse('/knowledges/qa/stream', data, handlers, signal)
}

export function listQaHistory(scene: QaHistoryScene, limit = 200): Promise<QaHistoryVO[]> {
  if (isDemoMode) return mockListQaHistory(scene, limit)
  return aiService.get('/qa-history', { params: { scene, limit } })
}

export function clearQaHistory(scene: QaHistoryScene): Promise<void> {
  if (isDemoMode) return mockClearQaHistory(scene)
  return aiService.delete('/qa-history', { params: { scene } })
}

export function listChatSessions(scene: QaHistoryScene = 'CHAT'): Promise<ChatSessionVO[]> {
  if (isDemoMode) {
    return Promise.resolve(
      demoChatSessions
        .filter((s) => s.scene === scene)
        .slice()
        .sort((a, b) => String(b.updateTime).localeCompare(String(a.updateTime))),
    )
  }
  return aiService.get('/chat-sessions', { params: { scene } })
}

export function createChatSession(data?: ChatSessionCreateDTO): Promise<ChatSessionVO> {
  if (isDemoMode) {
    const created: DemoChatSession = {
      conversationId: `demo-session-${Date.now()}`,
      scene: data?.scene || 'CHAT',
      title: data?.title || '新会话',
      agentId: data?.agentId,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }
    demoChatSessions.unshift(created)
    return Promise.resolve({ ...created })
  }
  return aiService.post('/chat-sessions', data ?? {})
}

export function renameChatSession(
  conversationId: string,
  data: ChatSessionTitleDTO,
): Promise<ChatSessionVO> {
  if (isDemoMode) {
    const found = demoChatSessions.find((s) => s.conversationId === conversationId)
    if (!found) return Promise.reject(new Error('会话不存在'))
    found.title = data.title
    found.updateTime = new Date().toISOString()
    return Promise.resolve({ ...found })
  }
  return aiService.put(`/chat-sessions/${encodeURIComponent(conversationId)}/title`, data)
}

export function deleteChatSession(conversationId: string): Promise<void> {
  if (isDemoMode) {
    const idx = demoChatSessions.findIndex((s) => s.conversationId === conversationId)
    if (idx >= 0) demoChatSessions.splice(idx, 1)
    return Promise.resolve()
  }
  return aiService.delete(`/chat-sessions/${encodeURIComponent(conversationId)}`)
}

export function truncateChatSessionMessages(
  conversationId: string,
  keepUserTurns: number,
): Promise<void> {
  if (isDemoMode) {
    return mockTruncateChatSession(conversationId, keepUserTurns)
  }
  return aiService.post(`/chat-sessions/${encodeURIComponent(conversationId)}/truncate`, {
    keepUserTurns,
  })
}

export function listChatSessionMessages(
  conversationId: string,
  limit = 200,
): Promise<QaHistoryVO[]> {
  if (isDemoMode) {
    return mockListQaHistory('DOCUMENT_QA', 500).then((docRows) =>
      mockListQaHistory('CHAT', 500).then((chatRows) =>
        [...docRows, ...chatRows]
          .filter((r) => r.conversationId === conversationId)
          .slice(0, limit),
      ),
    )
  }
  return aiService.get(`/chat-sessions/${encodeURIComponent(conversationId)}/messages`, {
    params: { limit },
  })
}

/** 门户演示：智能问答最近 N 条历史（公开，默认 2） */
export function listPortalQaDemo(limit = 2, scene: QaHistoryScene = 'CHAT'): Promise<QaHistoryVO[]> {
  if (isDemoMode) return mockListQaHistory(scene, limit)
  return aiService.get('/qa-history/portal-demo', { params: { scene, limit } })
}

/** 获取 AI 模型配置 */
export function getAiModelConfig(): Promise<AiModelConfigVO> {
  if (isDemoMode) {
    return Promise.resolve({
      chatModel: 'qwen-plus',
      embeddingModel: 'qwen3.7-text-embedding',
      temperature: 0.7,
      maxTokens: 2000,
      topP: 0.9,
      enableRagDefault: false,
      memoryWindowSize: 20,
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      apiKeyMasked: 'sk-****demo',
      apiKeyConfigured: true,
      remark: '演示模式配置',
      chatModelOptions: ['qwen-plus', 'qwen-turbo', 'qwen-max'],
      embeddingModelOptions: ['qwen3.7-text-embedding', 'text-embedding-v3'],
      updateTime: new Date().toISOString(),
    })
  }
  return aiService.get('/model-config')
}

/** 更新 AI 模型配置 */
export function updateAiModelConfig(data: AiModelConfigUpdateDTO): Promise<AiModelConfigVO> {
  if (isDemoMode) {
    return Promise.resolve({
      ...data,
      baseUrl: data.baseUrl || 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      apiKeyMasked: data.apiKey ? 'sk-****demo' : 'sk-****demo',
      apiKeyConfigured: true,
      chatModelOptions: ['qwen-plus', 'qwen-turbo', 'qwen-max'],
      embeddingModelOptions: ['qwen3.7-text-embedding', 'text-embedding-v3'],
      updateTime: new Date().toISOString(),
    })
  }
  return aiService.put('/model-config', data)
}

const demoAgents: AgentVO[] = [
  {
    id: 1,
    code: 'monitor_default',
    name: '水利监测智能体',
    description: '演示默认监测智能体：遥测、多站对比、在线、工程与告警趋势',
    systemPrompt: '你是水利监测智能体，基于只读监测 Tools 回答水雨情与告警问题。',
    capabilities: [
      'RAG',
      'MCP_TOOLS',
    ],
    workflowType: 'REACT',
    enableMemory: true,
    status: 'ENABLED',
    builtin: true,
    defaultAgent: true,
  },
  {
    id: 2,
    code: 'inspection_agent',
    name: '巡检智能体',
    description: '演示巡检只读智能体：计划、任务、异常与巡检摘要',
    systemPrompt: '你是巡检智能体，基于只读巡检 Tools 回答计划、任务与异常问题。',
    capabilities: [
      'MCP_TOOLS',
      'RAG',
    ],
    workflowType: 'SEQUENTIAL',
    enableMemory: true,
    status: 'ENABLED',
    builtin: true,
    defaultAgent: false,
  },
  {
    id: 3,
    code: 'nl2sql_agent',
    name: '数据分析智能体',
    description: '演示 NL2SQL：白名单只读 SQL 查数（模拟）',
    systemPrompt: '你是数据分析智能体，仅通过白名单只读 SQL 回答统计与明细问题。',
    capabilities: ['MCP_TOOLS'],
    workflowType: 'REACT',
    enableMemory: true,
    status: 'ENABLED',
    builtin: true,
    defaultAgent: false,
  },
  {
    id: 4,
    code: 'kg_agent',
    name: '知识图谱智能体',
    description: '演示业务拓扑 GraphRAG：实体、邻居、路径、工程拓扑与告警影响面',
    systemPrompt: '你是知识图谱智能体，基于业务拓扑图回答关联与影响面问题。',
    capabilities: ['KNOWLEDGE_GRAPH'],
    workflowType: 'REACT',
    enableMemory: true,
    status: 'ENABLED',
    builtin: true,
    defaultAgent: false,
  },
]

type DemoChatSession = ChatSessionVO

const demoChatSessions: DemoChatSession[] = [
  {
    conversationId: 'demo-chat-seed',
    scene: 'CHAT',
    title: '监测态势问答',
    agentId: 1,
    updateTime: new Date().toISOString(),
    createTime: new Date().toISOString(),
  },
  {
    conversationId: 'demo-inspection-seed',
    scene: 'CHAT',
    title: '巡检进度问答',
    agentId: 2,
    updateTime: new Date().toISOString(),
    createTime: new Date().toISOString(),
  },
  {
    conversationId: 'demo-doc-seed',
    scene: 'DOCUMENT_QA',
    title: '水位警戒处置',
    agentId: undefined,
    updateTime: new Date().toISOString(),
    createTime: new Date().toISOString(),
  },
]

export function listAgents(status?: string): Promise<AgentVO[]> {
  if (isDemoMode) {
    return Promise.resolve(
      status ? demoAgents.filter((a) => a.status === status) : [...demoAgents],
    )
  }
  return aiService.get('/agents', { params: status ? { status } : undefined })
}

export function getAgent(id: number): Promise<AgentVO> {
  if (isDemoMode) {
    const found = demoAgents.find((a) => a.id === id)
    if (!found) return Promise.reject(new Error('智能体不存在'))
    return Promise.resolve(found)
  }
  return aiService.get(`/agents/${id}`)
}

export function createAgent(data: AgentCreateDTO): Promise<AgentVO> {
  if (isDemoMode) {
    const created: AgentVO = {
      id: Date.now(),
      ...data,
      status: data.status || 'ENABLED',
      builtin: false,
      defaultAgent: false,
      enableMemory: data.enableMemory,
      capabilities: data.capabilities,
    }
    demoAgents.push(created)
    return Promise.resolve(created)
  }
  return aiService.post('/agents', data)
}

export function updateAgent(id: number, data: AgentUpdateDTO): Promise<AgentVO> {
  if (isDemoMode) {
    const idx = demoAgents.findIndex((a) => a.id === id)
    if (idx < 0) return Promise.reject(new Error('智能体不存在'))
    demoAgents[idx] = { ...demoAgents[idx], ...data }
    return Promise.resolve(demoAgents[idx])
  }
  return aiService.put(`/agents/${id}`, data)
}

export function draftAgentSystemPrompt(data: AgentPromptDraftDTO): Promise<AgentPromptDraftVO> {
  if (isDemoMode) {
    const name = data.name?.trim() || '智能助手'
    const desc = data.description?.trim()
    const caps = (data.capabilities || []).join('、') || '未勾选'
    const prompt = [
      `你是「${name}」${desc ? `，${desc}` : '，服务万象监测平台'}。`,
      '',
      `已配置能力：${caps}。`,
      data.mcpUpstreamNames?.length ? `外部 MCP：${data.mcpUpstreamNames.join('、')}。` : '',
      '优先用工具取真实数据，禁止编造；只读，不确定时明确说明。',
    ]
      .filter(Boolean)
      .join('\n')
    return Promise.resolve({ prompt, source: 'TEMPLATE' })
  }
  return aiService.post('/agents/system-prompt-draft', data, { timeout: 60000 })
}

export function deleteAgent(id: number): Promise<void> {
  if (isDemoMode) {
    const idx = demoAgents.findIndex((a) => a.id === id)
    if (idx >= 0) demoAgents.splice(idx, 1)
    return Promise.resolve()
  }
  return aiService.delete(`/agents/${id}`)
}

export function setDefaultAgent(id: number): Promise<void> {
  if (isDemoMode) {
    demoAgents.forEach((a) => {
      a.defaultAgent = a.id === id
    })
    return Promise.resolve()
  }
  return aiService.put(`/agents/${id}/default`)
}

export function listAgentCapabilities(): Promise<CapabilityVO[]> {
  if (isDemoMode) {
    return Promise.resolve([
      {
        code: 'RAG',
        label: '知识库增强',
        description: '向量检索 + Hybrid',
        toolBased: false,
        toolNames: [],
      },
      {
        code: 'MEMORY',
        label: '多轮记忆',
        description: '会话窗口记忆',
        toolBased: false,
        toolNames: [],
      },
      {
        code: 'MCP_TOOLS',
        label: 'MCP 上游工具',
        description: '调用已绑定的外部 MCP（如万象监测）',
        toolBased: false,
        toolNames: [],
      },
      {
        code: 'KNOWLEDGE_GRAPH',
        label: '知识图谱引擎',
        description: '图谱检索骨架',
        toolBased: false,
        toolNames: [],
      },
      {
        code: 'WORKFLOW_GRAPH',
        label: '工作流 Graph',
        description: '可视化编排',
        toolBased: false,
        toolNames: [],
      },
    ])
  }
  return aiService.get('/agents/capabilities')
}

export function listWorkflowTemplates(): Promise<WorkflowTemplateVO[]> {
  if (isDemoMode) {
    return Promise.resolve([
      { code: 'REACT', label: '单智能体 ReAct', description: '推理-行动循环' },
      { code: 'SEQUENTIAL', label: '顺序多步', description: '澄清 → 执行 → 润色' },
      { code: 'ROUTING', label: '路由分发', description: '数据 / 知识路由' },
      { code: 'GRAPH', label: '可视化 Graph', description: '自定义节点编排' },
    ])
  }
  return aiService.get('/agents/workflow-templates')
}

export function trialAgent(id: number, data: AgentTrialDTO): Promise<ChatResponseVO> {
  if (isDemoMode) {
    return Promise.resolve({
      content: `【演示】智能体 ${id} 收到：${data.message}`,
      timestamp: new Date().toISOString(),
      model: 'demo',
      agentId: id,
      conversationId: data.conversationId || `demo-${Date.now()}`,
      traces: [
        { type: 'NODE_START', name: 'REACT', detail: 'TOOL_AGENT', durationMs: null, timestamp: Date.now() },
        { type: 'NODE_END', name: 'REACT', detail: '完成', durationMs: 12, timestamp: Date.now() },
      ],
    })
  }
  return aiService.post(`/agents/${id}/trial`, data, { timeout: 120000 })
}

export function getAgentGraph(id: number): Promise<WorkflowGraphDTO> {
  if (isDemoMode) {
    return Promise.resolve({
      version: 1,
      nodes: [
        { id: 'start', type: 'START', label: '开始', positionX: 80, positionY: 160, data: {} },
        {
          id: 'worker',
          type: 'TOOL_AGENT',
          label: '执行',
          positionX: 320,
          positionY: 160,
          data: { capabilities: ['ONLINE'], systemPrompt: '' },
        },
        { id: 'end', type: 'END', label: '结束', positionX: 560, positionY: 160, data: {} },
      ],
      edges: [
        { id: 'e1', source: 'start', target: 'worker' },
        { id: 'e2', source: 'worker', target: 'end' },
      ],
    })
  }
  return aiService.get(`/agents/${id}/graph`)
}

export function saveAgentGraph(id: number, graph: WorkflowGraphDTO): Promise<WorkflowGraphDTO> {
  if (isDemoMode) {
    return Promise.resolve(graph)
  }
  return aiService.put(`/agents/${id}/graph`, graph)
}

export function validateAgentGraph(id: number, graph: WorkflowGraphDTO): Promise<GraphValidationResult> {
  if (isDemoMode) {
    return Promise.resolve({ valid: true, errors: [] })
  }
  return aiService.post(`/agents/${id}/graph/validate`, graph)
}

export function compileWorkflowTemplate(
  type: string,
  agentId?: number,
): Promise<WorkflowGraphDTO> {
  if (isDemoMode) {
    return getAgentGraph(agentId || 1)
  }
  return aiService.post(`/agents/workflow-templates/${type}/compile`, null, {
    params: agentId ? { agentId } : undefined,
  })
}

export function listAgentRuns(id: number, limit = 10): Promise<AgentRunVO[]> {
  if (isDemoMode) {
    return Promise.resolve(
      [
        {
          id: 1,
          agentId: id,
          conversationId: id === 2 ? 'demo-inspection-seed' : 'demo-chat-seed',
          status: 'SUCCESS',
          currentNode: id === 2 ? 'SEQUENTIAL' : 'REACT',
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString(),
        },
      ].slice(0, limit),
    )
  }
  return aiService.get(`/agents/${id}/runs`, { params: { limit } })
}

export function getAgentRuntimeHealth(): Promise<Record<string, unknown>> {
  if (isDemoMode) {
    return Promise.resolve({ configured: 'chatclient', active: 'chatclient', engines: [] })
  }
  return aiService.get('/agents/runtime-health')
}

// ==================== 知识图谱 Knowledge Graph ====================

export function getKgHealth(): Promise<KgHealthVO> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgHealth())
  }
  return aiService.get('/kg/health')
}

export function getKgStats(): Promise<KgStatsVO> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgStats())
  }
  return aiService.get('/kg/stats')
}

export function getKgSubgraph(params: {
  projectId?: number
  depth?: number
  types?: string
}): Promise<KgSubgraphVO> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgSubgraph(params))
  }
  return aiService.get('/kg/subgraph', { params })
}

export function searchKgEntities(q: string, limit = 20): Promise<KgSearchHit[]> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgSearch(q, limit))
  }
  return aiService.get('/kg/search', { params: { q, limit } })
}

export function getKgNeighbors(
  label: string,
  bizId: number | string,
  depth = 1,
): Promise<KgNeighborResult> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgNeighbors(label, bizId, depth))
  }
  return aiService.get(`/kg/neighbors/${encodeURIComponent(label)}/${bizId}`, {
    params: { depth },
  })
}

export function getKgPath(params: {
  fromLabel: string
  fromBizId: number | string
  toLabel: string
  toBizId: number | string
}): Promise<KgPathResult> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgPath(params))
  }
  return aiService.get('/kg/path', { params })
}

export function getKgTopology(projectId: number | string): Promise<KgTopologySummary> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgTopology(projectId))
  }
  return aiService.get(`/kg/topology/${projectId}`)
}

export function getKgAlertImpact(bizId: number | string, depth = 2): Promise<KgNeighborResult> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgAlertImpact(bizId, depth))
  }
  return aiService.get(`/kg/impact/alert/${bizId}`, { params: { depth } })
}

export function triggerKgSync(full = false): Promise<KgSyncResult> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgSync(full))
  }
  return aiService.post('/kg/sync', null, { params: { full } })
}

export function getKgSyncStatus(): Promise<KgSyncStatusVO> {
  if (isDemoMode) {
    return import('@/mock/kg').then((m) => m.mockKgSyncStatus())
  }
  return aiService.get('/kg/sync/status')
}

export function getMcpOverview(): Promise<McpOverviewVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockGetMcpOverview())
  }
  return aiService.get('/mcp/overview')
}

export function listMcpClients(): Promise<McpClientVO[]> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockListMcpClients())
  }
  return aiService.get('/mcp/clients')
}

export function createMcpClient(data: McpClientCreateDTO): Promise<McpClientVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockCreateMcpClient(data))
  }
  return aiService.post('/mcp/clients', data)
}

export function updateMcpClient(id: number, data: McpClientUpdateDTO): Promise<McpClientVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockUpdateMcpClient(id, data))
  }
  return aiService.put(`/mcp/clients/${id}`, data)
}

export function rotateMcpClientKey(id: number): Promise<McpClientVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockRotateMcpClientKey(id))
  }
  return aiService.post(`/mcp/clients/${id}/rotate-key`)
}

export function deleteMcpClient(id: number): Promise<void> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockDeleteMcpClient(id))
  }
  return aiService.delete(`/mcp/clients/${id}`)
}

export function listMcpUpstreams(): Promise<McpUpstreamVO[]> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockListMcpUpstreams())
  }
  return aiService.get('/mcp/upstreams')
}

export function createMcpUpstream(data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockCreateMcpUpstream(data))
  }
  return aiService.post('/mcp/upstreams', data)
}

export function updateMcpUpstream(id: number, data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockUpdateMcpUpstream(id, data))
  }
  return aiService.put(`/mcp/upstreams/${id}`, data)
}

export function deleteMcpUpstream(id: number): Promise<void> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockDeleteMcpUpstream(id))
  }
  return aiService.delete(`/mcp/upstreams/${id}`)
}

export function probeMcpUpstream(id: number): Promise<McpUpstreamVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockProbeMcpUpstream(id))
  }
  return aiService.post(`/mcp/upstreams/${id}/probe`)
}

export function listMcpUpstreamTools(id: number): Promise<McpUpstreamToolVO[]> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockListMcpUpstreamTools(id))
  }
  return aiService.get(`/mcp/upstreams/${id}/tools`)
}

export function patchMcpUpstreamTool(
  id: number,
  data: { originalName: string; enabled: boolean },
): Promise<McpUpstreamToolVO> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockPatchMcpUpstreamTool(id, data))
  }
  return aiService.put(`/mcp/upstreams/${id}/tools`, data)
}

export function listMcpCalls(direction?: string, limit = 50): Promise<McpCallLogVO[]> {
  if (isDemoMode) {
    return import('@/mock/mcp').then((m) => m.mockListMcpCalls(direction, limit))
  }
  return aiService.get('/mcp/calls', { params: { direction, limit } })
}
