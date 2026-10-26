/**
 * 演示分支的两个约定：
 * 1) 用构建期常量 `__DEMO_MODE__` 判断，Rollup 才能看到 `if (false)` 并剔除分支；
 * 2) mock 模块一律惰性 import，避免 src/mock/** 进生产包（曾静态 import 导致演示数据随包发布）。
 */
import type { ChatRequestDTO, ChatResponseVO, ChatStructuredRequestDTO } from '@/types/aiChat'
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
import type { ChatSessionCreateDTO, ChatSessionTitleDTO, ChatSessionVO } from '@/types/chatSession'
import { postAiSse, type AiSseHandlers, type AiSseOptions } from '@/utils/aiSse'
import { aiService } from '@/api/aiClient'
import type {
  AgentExecutionVO,
  AgentExecutionDetailVO,
  AgentMonitorStatsVO,
  AgentMonitorQuery,
  PageResult,
  AgentStatsByAgentVO,
} from '@/types/agentMonitor'

export type { ChatRequestDTO, ChatResponseVO, ChatStructuredRequestDTO }

/**
 * AI 助手相关接口（聊天 / 知识管理 / 知识问答）
 */

/** 演示数据惰性加载：生产构建不会把 src/mock/ai.ts 编进任何 chunk */
const loadAiMock = () => import('@/mock/ai')

/** 同步聊天（多轮请传 conversationId） */
export function chat(data: ChatRequestDTO): Promise<ChatResponseVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockAiChat(data))
  return aiService.post('/chat', data)
}

/** 流式聊天 SSE（options 可传中断信号与超时阈值） */
export function chatStream(
  data: ChatRequestDTO,
  handlers: AiSseHandlers,
  options?: AiSseOptions,
): Promise<void> {
  if (__DEMO_MODE__)
    return loadAiMock().then((m) => m.mockChatStream(data, handlers, options?.signal))
  return postAiSse('/chat/stream', data, handlers, options)
}

/** 结构化输出（对比 / 趋势 / 告警） */
export function chatStructured(data: ChatStructuredRequestDTO): Promise<ChatResponseVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockChatStructured(data))
  return aiService.post('/chat/structured', data)
}

export function healthCheck(): Promise<string> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockAiHealth())
  return aiService.get('/chat/health')
}

export function diagnose(): Promise<string> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockAiDiagnose())
  return aiService.get('/chat/diagnose')
}

/** 对话运行指标快照 */
export function chatMetrics(): Promise<Record<string, unknown>> {
  if (__DEMO_MODE__) {
    return Promise.resolve({ totalCalls: 0, successCalls: 0, failCalls: 0, demo: true })
  }
  return aiService.get('/chat/metrics')
}

/** Memory / Vector / Hybrid 冒烟评测 */
export function chatEval(): Promise<Record<string, unknown>> {
  if (__DEMO_MODE__) {
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

export function getKnowledgesCategoryList(
  includeDisabled = false,
): Promise<KnowledgesCategoryVO[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/ai').then((m) => m.mockListKnowledgesCategories())
  }
  return aiService.get('/knowledges-categories', { params: { includeDisabled } })
}

/** @deprecated 使用 getKnowledgesCategoryList */
export const getDocumentCategoryList = getKnowledgesCategoryList

export function createKnowledgesCategory(
  data: KnowledgesCategoryCreateDTO,
): Promise<KnowledgesCategoryVO> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    return import('@/mock/ai').then((m) => m.mockUpdateKnowledgesCategory(id, data))
  }
  return aiService.put(`/knowledges-categories/${encodeURIComponent(id)}`, data)
}

/** @deprecated 使用 updateKnowledgesCategory */
export const updateDocumentCategory = updateKnowledgesCategory

export function deleteKnowledgesCategory(id: string): Promise<void> {
  if (__DEMO_MODE__) {
    return import('@/mock/ai').then((m) => m.mockDeleteKnowledgesCategory(id))
  }
  return aiService.delete(`/knowledges-categories/${encodeURIComponent(id)}`)
}

/** @deprecated 使用 deleteKnowledgesCategory */
export const deleteDocumentCategory = deleteKnowledgesCategory

export function getDocumentList(categoryId?: string): Promise<DocumentVO[]> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentList(categoryId))
  return aiService.get('/knowledges', {
    params: categoryId ? { categoryId } : undefined,
  })
}

export function getDocumentDetail(id: string): Promise<DocumentVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentDetail(id))
  return aiService.get(`/knowledges/${encodeURIComponent(id)}`)
}

export function uploadDocument(params: DocumentUploadParams): Promise<DocumentVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentUpload(params))
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

export function updateDocumentCategoryBinding(id: string, categoryId: string): Promise<DocumentVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/ai').then((m) => m.mockBindDocumentCategory(id, categoryId))
  }
  return aiService.put(`/knowledges/${encodeURIComponent(id)}/category`, {
    categoryId: Number(categoryId),
  })
}

export function deleteDocument(id: string): Promise<void> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentDelete(id))
  return aiService.delete(`/knowledges/${encodeURIComponent(id)}`)
}

export function reprocessDocument(id: string): Promise<DocumentVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentReprocess(id))
  return aiService.post(`/knowledges/${encodeURIComponent(id)}/reprocess`, null, {
    timeout: 120000,
  })
}

export function documentQa(data: DocumentQueryDTO): Promise<ChatResponseVO> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockDocumentQa(data))
  return aiService.post('/knowledges/qa', data, { timeout: 120000 })
}

/** 知识库问答流式 SSE（options 可传中断信号与超时阈值） */
export function documentQaStream(
  data: DocumentQueryDTO,
  handlers: AiSseHandlers,
  options?: AiSseOptions,
): Promise<void> {
  if (__DEMO_MODE__)
    return loadAiMock().then((m) => m.mockDocumentQaStream(data, handlers, options?.signal))
  return postAiSse('/knowledges/qa/stream', data, handlers, options)
}

export function listQaHistory(scene: QaHistoryScene, limit = 200): Promise<QaHistoryVO[]> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockListQaHistory(scene, limit))
  return aiService.get('/qa-history', { params: { scene, limit } })
}

export function clearQaHistory(scene: QaHistoryScene): Promise<void> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockClearQaHistory(scene))
  return aiService.delete('/qa-history', { params: { scene } })
}

export function listChatSessions(scene: QaHistoryScene = 'CHAT'): Promise<ChatSessionVO[]> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    const found = demoChatSessions.find((s) => s.conversationId === conversationId)
    if (!found) return Promise.reject(new Error('会话不存在'))
    found.title = data.title
    found.updateTime = new Date().toISOString()
    return Promise.resolve({ ...found })
  }
  return aiService.put(`/chat-sessions/${encodeURIComponent(conversationId)}/title`, data)
}

export function deleteChatSession(conversationId: string): Promise<void> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    return loadAiMock().then((m) => m.mockTruncateChatSession(conversationId, keepUserTurns))
  }
  return aiService.post(`/chat-sessions/${encodeURIComponent(conversationId)}/truncate`, {
    keepUserTurns,
  })
}

export function listChatSessionMessages(
  conversationId: string,
  limit = 200,
): Promise<QaHistoryVO[]> {
  if (__DEMO_MODE__) {
    return loadAiMock().then((m) =>
      m
        .mockListQaHistory('DOCUMENT_QA', 500)
        .then((docRows) =>
          m
            .mockListQaHistory('CHAT', 500)
            .then((chatRows) =>
              [...docRows, ...chatRows]
                .filter((r) => r.conversationId === conversationId)
                .slice(0, limit),
            ),
        ),
    )
  }
  return aiService.get(`/chat-sessions/${encodeURIComponent(conversationId)}/messages`, {
    params: { limit },
  })
}

/** 门户演示：智能问答最近 N 条历史（公开，默认 2） */
export function listPortalQaDemo(
  limit = 2,
  scene: QaHistoryScene = 'CHAT',
): Promise<QaHistoryVO[]> {
  if (__DEMO_MODE__) return loadAiMock().then((m) => m.mockListQaHistory(scene, limit))
  return aiService.get('/qa-history/portal-demo', { params: { scene, limit } })
}

const demoAgents: AgentVO[] = [
  {
    id: 1,
    code: 'monitor_default',
    name: '水利监测智能体',
    description: '演示默认监测智能体：遥测、多站对比、在线、工程与告警趋势',
    systemPrompt: '你是水利监测智能体，基于只读监测 Tools 回答水雨情与告警问题。',
    capabilities: ['RAG', 'MCP_TOOLS'],
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
    capabilities: ['MCP_TOOLS', 'RAG'],
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
  if (__DEMO_MODE__) {
    return Promise.resolve(status ? demoAgents.filter((a) => a.status === status) : [...demoAgents])
  }
  return aiService.get('/agents', { params: status ? { status } : undefined })
}

export function getAgent(id: number): Promise<AgentVO> {
  if (__DEMO_MODE__) {
    const found = demoAgents.find((a) => a.id === id)
    if (!found) return Promise.reject(new Error('智能体不存在'))
    return Promise.resolve(found)
  }
  return aiService.get(`/agents/${id}`)
}

export function createAgent(data: AgentCreateDTO): Promise<AgentVO> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    const idx = demoAgents.findIndex((a) => a.id === id)
    if (idx < 0) return Promise.reject(new Error('智能体不存在'))
    demoAgents[idx] = { ...demoAgents[idx], ...data }
    return Promise.resolve(demoAgents[idx])
  }
  return aiService.put(`/agents/${id}`, data)
}

export function draftAgentSystemPrompt(data: AgentPromptDraftDTO): Promise<AgentPromptDraftVO> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    const idx = demoAgents.findIndex((a) => a.id === id)
    if (idx >= 0) demoAgents.splice(idx, 1)
    return Promise.resolve()
  }
  return aiService.delete(`/agents/${id}`)
}

export function setDefaultAgent(id: number): Promise<void> {
  if (__DEMO_MODE__) {
    demoAgents.forEach((a) => {
      a.defaultAgent = a.id === id
    })
    return Promise.resolve()
  }
  return aiService.put(`/agents/${id}/default`)
}

export function listAgentCapabilities(): Promise<CapabilityVO[]> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    return Promise.resolve({
      content: `【演示】智能体 ${id} 收到：${data.message}`,
      timestamp: new Date().toISOString(),
      model: 'demo',
      agentId: id,
      conversationId: data.conversationId || `demo-${Date.now()}`,
      traces: [
        {
          type: 'NODE_START',
          name: 'REACT',
          detail: 'TOOL_AGENT',
          durationMs: null,
          timestamp: Date.now(),
        },
        { type: 'NODE_END', name: 'REACT', detail: '完成', durationMs: 12, timestamp: Date.now() },
      ],
    })
  }
  return aiService.post(`/agents/${id}/trial`, data, { timeout: 120000 })
}

export function getAgentGraph(id: number): Promise<WorkflowGraphDTO> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    return Promise.resolve(graph)
  }
  return aiService.put(`/agents/${id}/graph`, graph)
}

export function validateAgentGraph(
  id: number,
  graph: WorkflowGraphDTO,
): Promise<GraphValidationResult> {
  if (__DEMO_MODE__) {
    return Promise.resolve({ valid: true, errors: [] })
  }
  return aiService.post(`/agents/${id}/graph/validate`, graph)
}

export function compileWorkflowTemplate(type: string, agentId?: number): Promise<WorkflowGraphDTO> {
  if (__DEMO_MODE__) {
    return getAgentGraph(agentId || 1)
  }
  return aiService.post(`/agents/workflow-templates/${type}/compile`, null, {
    params: agentId ? { agentId } : undefined,
  })
}

export function listAgentRuns(id: number, limit = 10): Promise<AgentRunVO[]> {
  if (__DEMO_MODE__) {
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
  if (__DEMO_MODE__) {
    return Promise.resolve({ configured: 'chatclient', active: 'chatclient', engines: [] })
  }
  return aiService.get('/agents/runtime-health')
}

// 知识图谱 / MCP 已按域拆分，此处保留 re-export 兼容既有 import 路径
export * from '@/api/kg'
export * from '@/api/mcp'

const demoAgentExecutions: AgentExecutionVO[] = [
  {
    id: 1,
    agentId: 1,
    agentName: '演示默认监测智能体',
    userMessage: '当前各站点水位情况',
    responseSummary: '当前东区水位 12.3m，西区 8.7m...',
    status: 'SUCCESS',
    durationMs: 1520,
    modelName: 'gpt-4o',
    workflowType: 'REACT',
    runType: 'CHAT',
    ttftMs: 320,
    tpotMs: 45,
    tokenCount: 856,
    userId: 'demo',
    createTime: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 2,
    agentId: 2,
    agentName: '巡检智能体',
    userMessage: '今日巡检任务完成情况',
    responseSummary: '今日共 8 项巡检任务，已完成 6 项...',
    status: 'SUCCESS',
    durationMs: 980,
    modelName: 'gpt-4o',
    workflowType: 'SEQUENTIAL',
    runType: 'CHAT',
    ttftMs: 280,
    tpotMs: 38,
    tokenCount: 542,
    userId: 'demo',
    createTime: new Date(Date.now() - 7200000).toISOString(),
  },
]

/** 执行历史列表（分页） */
export function getAgentExecutions(
  params: AgentMonitorQuery,
): Promise<PageResult<AgentExecutionVO>> {
  if (__DEMO_MODE__) {
    const page = params.page || 1
    const size = params.size || 10
    let filtered = [...demoAgentExecutions]
    if (params.agentId) filtered = filtered.filter((e) => e.agentId === params.agentId)
    if (params.status) filtered = filtered.filter((e) => e.status === params.status)
    return Promise.resolve({
      current: page,
      size,
      total: filtered.length,
      pages: Math.ceil(filtered.length / size),
      records: filtered.slice((page - 1) * size, page * size),
    })
  }
  return aiService.get('/agent-monitor/executions', { params })
}

/** 执行详情（含轨迹） */
export function getAgentExecutionDetail(id: number): Promise<AgentExecutionDetailVO> {
  if (__DEMO_MODE__) {
    const exec = demoAgentExecutions.find((e) => e.id === id)
    if (!exec) return Promise.reject(new Error('执行记录不存在'))
    return Promise.resolve({
      ...exec,
      errorMessage: null,
      traces: [],
      updateTime: exec.createTime,
    })
  }
  return aiService.get(`/agent-monitor/executions/${id}`)
}

/** 统计概览 */
export function getAgentMonitorStats(period: string = 'TODAY'): Promise<AgentMonitorStatsVO> {
  if (__DEMO_MODE__) {
    return Promise.resolve({
      totalCount: demoAgentExecutions.length,
      successCount: demoAgentExecutions.filter((e) => e.status === 'SUCCESS').length,
      failedCount: demoAgentExecutions.filter((e) => e.status === 'FAILED').length,
      runningCount: demoAgentExecutions.filter((e) => e.status === 'RUNNING').length,
      successRate: 100,
      avgDurationMs: 1250,
      todayCount: demoAgentExecutions.length,
    })
  }
  return aiService.get('/agent-monitor/stats', { params: { period } })
}

/** 按智能体聚合统计 */
export function getAgentStatsByAgent(): Promise<AgentStatsByAgentVO[]> {
  if (__DEMO_MODE__) {
    return Promise.resolve([
      {
        agentId: 1,
        agentName: '演示默认监测智能体',
        totalCount: 1,
        successCount: 1,
        successRate: 100,
        avgDurationMs: 1520,
      },
      {
        agentId: 2,
        agentName: '巡检智能体',
        totalCount: 1,
        successCount: 1,
        successRate: 100,
        avgDurationMs: 980,
      },
    ])
  }
  return aiService.get('/agent-monitor/stats/agents')
}

/** 当前运行中的执行 */
export function getAgentRunningExecutions(): Promise<AgentExecutionVO[]> {
  if (__DEMO_MODE__) {
    return Promise.resolve(demoAgentExecutions.filter((e) => e.status === 'RUNNING'))
  }
  return aiService.get('/agent-monitor/running')
}
