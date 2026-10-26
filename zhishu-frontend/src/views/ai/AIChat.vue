<template>
  <div class="ai-chat-page">
    <div class="ai-chat-shell" :class="{ 'is-maximized': isMaximized }">
      <!-- 左侧：会话列表 -->
      <aside class="session-rail">
        <div class="session-rail__brand">
          <span class="session-rail__eyebrow">智能中心</span>
          <h2 class="session-rail__title">Agent 会话</h2>
        </div>
        <el-button
          class="session-rail__new"
          type="primary"
          :icon="Plus"
          @click="handleCreateSession"
        >
          新建会话
        </el-button>
        <div v-loading="sessionsLoading" class="session-rail__list">
          <div v-if="!sessions.length && !sessionsLoading" class="session-rail__empty">
            暂无会话，点击上方新建开始对话
          </div>
          <div
            v-for="session in sessions"
            :key="session.conversationId"
            role="button"
            tabindex="0"
            :class="[
              'session-rail__item',
              {
                'is-active': session.conversationId === conversationId,
                'is-pending': isSessionPending(session.conversationId),
              },
            ]"
            @click="handleSelectSession(session)"
            @keydown.enter="handleSelectSession(session)"
          >
            <el-icon class="session-rail__item-icon"><ChatDotRound /></el-icon>
            <div class="session-rail__item-body">
              <div class="session-rail__item-title" :title="session.title">
                {{ session.title || '新会话' }}
              </div>
              <div class="session-rail__item-meta">
                <span v-if="isSessionPending(session.conversationId)" class="session-rail__pending">
                  回复中…
                </span>
                <span v-else>{{ formatSessionTime(session.updateTime) }}</span>
              </div>
            </div>
            <div class="session-rail__item-actions" @click.stop>
              <el-button
                link
                type="primary"
                size="small"
                :icon="Edit"
                title="重命名"
                @click="handleRenameSession(session)"
              />
              <el-button
                link
                type="danger"
                size="small"
                :icon="Delete"
                title="删除会话"
                @click="handleDeleteSession(session)"
              />
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧：对话主区 -->
      <section class="chat-main">
        <header class="chat-topbar">
          <div class="chat-topbar__left">
            <div class="chat-topbar__title-row">
              <h1 class="chat-topbar__title">{{ currentSessionTitle || '新会话' }}</h1>
              <el-button
                v-if="conversationId"
                link
                type="primary"
                :icon="Edit"
                title="重命名"
                :disabled="loading"
                @click="handleRenameCurrentSession"
              />
              <span class="chat-topbar__agent-line">
                {{
                  currentAgent?.name
                    ? `当前智能体 · ${currentAgent.name}`
                    : '请选择智能体后开始对话'
                }}
                <template v-if="currentAgent?.workflowType">
                  · {{ currentAgent.workflowType }}
                </template>
              </span>
            </div>
          </div>
          <div class="chat-topbar__right">
            <el-button
              size="small"
              plain
              :icon="isMaximized ? ScaleToOriginal : FullScreen"
              :title="isMaximized ? '退出全屏 (Esc)' : '全屏最大化'"
              @click="toggleMaximize"
            >
              {{ isMaximized ? '退出全屏' : '全屏' }}
            </el-button>
            <el-button size="small" plain :disabled="loading" :icon="Delete" @click="handleClear">
              清空
            </el-button>
            <el-button size="small" plain :icon="Connection" @click="handleHealthCheck">
              健康检查
            </el-button>
          </div>
        </header>

        <div class="chat-config">
          <div class="chat-config__group">
            <span class="chat-config__label">智能体</span>
            <el-select
              v-model="selectedAgentId"
              size="small"
              class="chat-config__agent"
              placeholder="选择智能体"
              :disabled="loading || !agents.length"
              @change="handleAgentChange"
            >
              <el-option
                v-for="agent in agents"
                :key="agent.id"
                :label="agent.name"
                :value="agent.id"
              />
            </el-select>
            <div v-if="agentCapabilityTags.length" class="chat-config__caps">
              <el-tag
                v-for="tag in agentCapabilityTags"
                :key="tag"
                size="small"
                effect="plain"
                type="info"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>
          <div class="chat-config__group chat-config__group--end">
            <span class="chat-config__label">输出</span>
            <el-select
              v-model="outputMode"
              size="small"
              class="chat-config__mode"
              :disabled="loading"
            >
              <el-option label="流式对话" value="STREAM" />
              <el-option label="对比表格" value="COMPARE" />
              <el-option label="趋势分析" value="TREND" />
              <el-option label="告警摘要" value="ALARM" />
            </el-select>
            <div class="chat-config__rag" :class="{ 'is-disabled': !agentSupportsRag }">
              <span>知识库</span>
              <el-switch
                v-model="enableRag"
                size="small"
                :disabled="loading || !agentSupportsRag"
              />
            </div>
          </div>
        </div>

        <div ref="messagesRef" v-loading="historyLoading" class="chat-messages">
          <div class="chat-messages__inner">
            <div v-if="messages.length === 0" class="chat-empty">
              <el-empty :description="emptyDescription" :image-size="88" />
            </div>

            <ChatMessageItem
              v-for="(msg, index) in messages"
              :key="index"
              :message="msg"
              :index="index"
              :is-editing="editingIndex === index"
              :loading="loading"
              :agent-name="currentAgent?.name || 'Agent'"
              :user-initial="getUserInitial"
              :user-avatar-color="getUserAvatarColor"
              @copy="copyMessage"
              @edit="startEdit"
              @example="handleExampleClick"
            />

            <div v-if="loading && !streamingStarted" class="typing-row">
              <el-avatar :size="36" class="typing-row__avatar">
                <el-icon><Service /></el-icon>
              </el-avatar>
              <div class="typing-row__body">
                <div class="typing-row__role">{{ currentAgent?.name || 'Agent' }}</div>
                <div class="typing-indicator"><span /><span /><span /></div>
              </div>
            </div>
          </div>
        </div>

        <footer class="chat-composer">
          <div class="chat-composer__inner" :class="{ 'is-editing': editingIndex != null }">
            <p v-if="editingIndex != null" class="chat-composer__edit-hint">
              正在编辑此前的问题，发送后将替换该轮及之后的对话
              <el-button link type="primary" size="small" @click="cancelEdit">取消编辑</el-button>
            </p>
            <el-input
              :key="composerKey"
              ref="composerInputRef"
              v-model="inputMessage"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 5 }"
              resize="none"
              :placeholder="
                editingIndex != null
                  ? '修改问题后点击重新发送… Ctrl+Enter 发送'
                  : '点上方示例，或直接输入监测相关问题… Ctrl+Enter 发送'
              "
              :disabled="loading"
              @keydown.enter.ctrl="handleSend"
              @keydown.esc="editingIndex != null ? cancelEdit() : undefined"
            />
            <div class="chat-composer__bar">
              <div class="chat-composer__tips">
                <template v-if="editingIndex != null">Ctrl + Enter 重新发送</template>
                <template v-else>Ctrl + Enter 发送</template>
                <span v-if="enableRag"> · 知识库已开启</span>
                <span v-if="outputMode !== 'STREAM'"> · {{ outputModeLabel }}</span>
              </div>
              <el-button v-if="loading" type="danger" plain :icon="VideoPause" @click="handleStop">
                停止生成
              </el-button>
              <el-button
                v-else
                type="primary"
                :disabled="!inputMessage.trim()"
                :icon="Promotion"
                @click="handleSend"
              >
                {{ editingIndex != null ? '重新发送' : '发送' }}
              </el-button>
            </div>
          </div>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIChat' })

import { ref, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import {
  ChatDotRound,
  Delete,
  Connection,
  Service,
  Promotion,
  Edit,
  Plus,
  FullScreen,
  ScaleToOriginal,
  VideoPause,
} from '@element-plus/icons-vue'
import {
  chatStream,
  chatStructured,
  chatEval,
  healthCheck,
  listAgents,
  listChatSessions,
  createChatSession,
  renameChatSession,
  deleteChatSession,
  listChatSessionMessages,
  truncateChatSessionMessages,
} from '@/api/ai'
import type { StructuredChatType } from '@/types/aiChat'
import type { AgentVO } from '@/types/aiAgent'
import type { AgentTraceEvent } from '@/types/aiChat'
import type { QaHistoryVO } from '@/types/qaHistory'
import type { ChatSessionVO } from '@/types/chatSession'
import { useUserStore } from '@/stores/useUserStore'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { usePanelMaximize } from '@/composables/usePanelMaximize'
import { useAiStream } from '@/composables/useAiStream'
import { useChatSession } from '@/composables/useChatSession'
import { useUserAvatar } from '@/composables/useUserAvatar'
import { isAiSseTimeout, isAiSseUserAbort } from '@/utils/aiSse'
import { formatRelativeTime } from '@/utils/format'
import { isMessageBoxCancel } from '@/utils/uiMessage'
import ChatMessageItem from '@/components/ai/ChatMessageItem.vue'
import type { AgentChatMessage as Message } from '@/types/chatMessage'

type OutputMode = 'STREAM' | StructuredChatType

const SCENE = 'CHAT' as const
const { isMaximized, toggleMaximize } = usePanelMaximize()
/** 按会话维度管理 SSE 中断，组件卸载时自动断流 */
const aiStream = useAiStream()

/** 按会话缓存消息（含进行中的流式回复，切走后仍可回看），逻辑见 useChatSession */
const {
  conversationId,
  messages,
  loading,
  streamingStarted,
  loadingByCid,
  streamingByCid,
  messageCache,
  ensureMessageList,
  setSessionMessages,
  clearSessionRuntime,
  isSessionPending,
} = useChatSession<Message>({ useFallback: true })

const inputMessage = ref('')
const editingIndex = ref<number | null>(null)
const composerInputRef = ref<InputInstance>()
const composerKey = computed(() =>
  editingIndex.value == null ? 'composer-new' : `composer-edit-${editingIndex.value}`,
)
const historyLoading = ref(false)
const sessionsLoading = ref(false)
const messagesRef = ref<HTMLElement>()
const enableRag = ref(false)
const outputMode = ref<OutputMode>('STREAM')
const agents = ref<AgentVO[]>([])
const selectedAgentId = ref<number | undefined>(undefined)
const sessions = ref<ChatSessionVO[]>([])
const userStore = useUserStore()
const systemConfigStore = useSystemConfigStore()

const bindSessionAgent = (cid: string) => {
  const session = sessions.value.find((s) => s.conversationId === cid)
  if (session?.agentId != null) {
    selectedAgentId.value = session.agentId
    if (!agentSupportsRag.value) {
      enableRag.value = false
    }
  }
}

const currentSession = computed(() =>
  sessions.value.find((s) => s.conversationId === conversationId.value),
)

const currentSessionTitle = computed(() => currentSession.value?.title || '')

const currentAgent = computed(() => agents.value.find((a) => a.id === selectedAgentId.value))

const agentSupportsRag = computed(() => !!currentAgent.value?.capabilities?.includes('RAG'))

const CAPABILITY_LABELS: Record<string, string> = {
  RAG: '知识库',
  MEMORY: '多轮记忆',
  MCP_TOOLS: 'MCP 上游',
  KNOWLEDGE_GRAPH: '知识图谱',
  BRIEFING: 'AI 简报',
  WORKFLOW_GRAPH: '工作流',
}

/** 按能力给出的示例提问（新建会话欢迎语） */
const CAPABILITY_EXAMPLES: Record<string, string[]> = {
  MCP_TOOLS: ['查看全部遥测站在线状态概览（经万象 MCP）'],
  RAG: ['根据知识库解释一下水位超限该怎么处理'],
  KNOWLEDGE_GRAPH: ['在图谱里搜索某个工程或站点'],
  BRIEFING: ['帮我生成一份监测简报'],
}

const DEFAULT_EXAMPLES = [
  '查看全部遥测站在线状态概览',
  '查一下站号 0001 的最新水位',
  '最近几天有没有阈值告警？',
]

const agentCapabilityTags = computed(() => {
  const caps = currentAgent.value?.capabilities || []
  const tags = caps.map((c) => CAPABILITY_LABELS[c] || c)
  if (currentAgent.value?.enableMemory) {
    tags.unshift('Memory')
  }
  return tags.slice(0, 6)
})

const outputModeLabel = computed(() => {
  const map: Record<OutputMode, string> = {
    STREAM: '流式对话',
    COMPARE: '对比表格',
    TREND: '趋势分析',
    ALARM: '告警摘要',
  }
  return map[outputMode.value] || outputMode.value
})

const emptyDescription = computed(() => {
  if (currentAgent.value) {
    return `开始与「${currentAgent.value.name}」对话吧，可点欢迎语中的示例提问`
  }
  return '选择智能体后开始 Agent 会话'
})

/** 根据当前智能体能力挑选示例提问（最多 5 条，能力优先去重） */
const welcomeExamples = computed(() => {
  const caps = currentAgent.value?.capabilities || []
  const picked: string[] = []
  const seen = new Set<string>()
  for (const code of caps) {
    const list = CAPABILITY_EXAMPLES[code]
    if (!list) continue
    for (const ex of list) {
      if (seen.has(ex)) continue
      seen.add(ex)
      picked.push(ex)
      if (picked.length >= 5) return picked
    }
  }
  if (picked.length === 0) {
    return [...DEFAULT_EXAMPLES]
  }
  return picked
})

const welcomeText = computed(() => {
  const agent = currentAgent.value
  const examples = welcomeExamples.value
  const exampleLines = examples.map((ex, i) => `${i + 1}. 「${ex}」`).join('\n')
  const tip = '下方也可直接点击示例填入输入框。'

  if (agent) {
    const desc = agent.description?.trim() ? ` ${agent.description.trim()}` : ''
    return [
      `您好！我是「${agent.name}」。${desc}`,
      '',
      '你可以这样问我：',
      exampleLines,
      '',
      tip,
    ].join('\n')
  }
  return [
    `您好！我是${systemConfigStore.systemName}智能助手。`,
    '',
    '你可以这样问我：',
    exampleLines,
    '',
    tip,
  ].join('\n')
})

const handleExampleClick = (example: string) => {
  if (loading.value) return
  inputMessage.value = example
}

const { initial: getUserInitial, color: getUserAvatarColor } = useUserAvatar()

const toDate = (value?: string | Date) => {
  if (!value) return new Date()
  if (value instanceof Date) return value
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? new Date() : d
}

const historyToMessages = (records: QaHistoryVO[]): Message[] => {
  const list: Message[] = []
  records.forEach((item) => {
    const ts = toDate(item.createTime)
    list.push({ role: 'user', content: item.question, timestamp: ts })
    list.push({ role: 'assistant', content: item.answer, timestamp: ts })
  })
  return list
}

const formatSessionTime = (value?: string): string => formatRelativeTime(value)

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const applyWelcome = () => {
  setSessionMessages(conversationId.value, [
    {
      role: 'assistant',
      content: welcomeText.value,
      examples: [...welcomeExamples.value],
      timestamp: new Date(),
    },
  ])
}

const ensureUserReady = async () => {
  userStore.initUserInfo()
  if (!userStore.userInfo?.id && userStore.token) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      console.warn('获取用户信息失败，问答历史可能无法按用户隔离', error)
    }
  }
}

const loadAgents = async () => {
  try {
    const list = await listAgents('ENABLED')
    agents.value = list || []
    const defaultAgent = agents.value.find((a) => a.defaultAgent) || agents.value[0]
    if (defaultAgent && selectedAgentId.value == null) {
      selectedAgentId.value = defaultAgent.id
    }
    if (!agentSupportsRag.value) {
      enableRag.value = false
    }
  } catch (error) {
    console.error('加载智能体列表失败:', error)
    agents.value = []
  }
}

/** 切换智能体仅影响后续新建会话偏好，不清空已有会话与当前消息 */
const handleAgentChange = () => {
  if (!agentSupportsRag.value) {
    enableRag.value = false
  }
}

const refreshSessionList = async () => {
  const list = await listChatSessions(SCENE)
  sessions.value = list || []
}

const softSyncSessionFromApi = async (cid: string) => {
  try {
    const records = await listChatSessionMessages(cid)
    if (conversationId.value !== cid || loadingByCid[cid]) return
    if (!records?.length) return
    const fromApi = historyToMessages(records)
    const cached = messageCache[cid]
    if (!cached?.length || fromApi.length >= cached.length) {
      messageCache[cid] = fromApi
    }
  } catch (error) {
    console.warn('后台同步会话消息失败:', error)
  }
}

const loadSessionMessages = async (cid: string) => {
  if (conversationId.value !== cid) {
    cancelEdit()
  }
  conversationId.value = cid
  bindSessionAgent(cid)

  // 进行中的回复：保留本地流式缓存，避免被历史接口覆盖
  if (loadingByCid[cid] && messageCache[cid]?.length) {
    await scrollToBottom()
    return
  }

  // 已有本地缓存：先展示，再后台与服务端对齐（规避刚回复完切回时的落库延迟）
  if (messageCache[cid]?.length) {
    await scrollToBottom()
    void softSyncSessionFromApi(cid)
    return
  }

  historyLoading.value = true
  try {
    const records = await listChatSessionMessages(cid)
    if (conversationId.value !== cid || loadingByCid[cid]) return
    if (records?.length) {
      messageCache[cid] = historyToMessages(records)
    } else {
      applyWelcome()
    }
    await scrollToBottom()
  } catch (error) {
    console.error('加载会话消息失败:', error)
    if (conversationId.value === cid) applyWelcome()
  } finally {
    if (conversationId.value === cid) {
      historyLoading.value = false
    }
  }
}

const createAndSelectSession = async (title?: string) => {
  const created = await createChatSession({
    scene: SCENE,
    title: title || '新会话',
    agentId: selectedAgentId.value,
  })
  await refreshSessionList()
  conversationId.value = created.conversationId
  applyWelcome()
  await scrollToBottom()
  return created
}

const handleSelectSession = async (session: ChatSessionVO) => {
  if (session.conversationId === conversationId.value) return
  await loadSessionMessages(session.conversationId)
}

const handleCreateSession = async () => {
  try {
    sessionsLoading.value = true
    await createAndSelectSession()
    ElMessage.success('已新建会话')
  } catch (error) {
    console.error('新建会话失败:', error)
  } finally {
    sessionsLoading.value = false
  }
}

const promptRename = async (session: ChatSessionVO) => {
  const { value } = await ElMessageBox.prompt('请输入新的会话标题', '重命名', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: session.title || '',
    inputPattern: /\S+/,
    inputErrorMessage: '标题不能为空',
  })
  const title = value.trim()
  const updated = await renameChatSession(session.conversationId, { title })
  await refreshSessionList()
  return updated
}

const handleRenameSession = async (session: ChatSessionVO) => {
  try {
    await promptRename(session)
    ElMessage.success('已重命名')
  } catch (error) {
    if (isMessageBoxCancel(error)) return
    console.error('重命名失败:', error)
  }
}

const handleRenameCurrentSession = async () => {
  const session = currentSession.value
  if (!session) return
  await handleRenameSession(session)
}

const handleDeleteSession = async (session: ChatSessionVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除会话「${session.title || '新会话'}」？此操作不可恢复。`,
      '删除会话',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    const wasActive = session.conversationId === conversationId.value
    await deleteChatSession(session.conversationId)
    clearSessionRuntime(session.conversationId)
    await refreshSessionList()
    if (wasActive) {
      if (sessions.value.length) {
        await loadSessionMessages(sessions.value[0].conversationId)
      } else {
        conversationId.value = undefined
        applyWelcome()
      }
    }
    ElMessage.success('已删除会话')
  } catch (error) {
    console.error('删除会话失败:', error)
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  sessionsLoading.value = true
  try {
    await Promise.all([ensureUserReady(), systemConfigStore.ensureConfigLoaded(), loadAgents()])
    // 默认 RAG 开关来自系统配置（参数配置 · AI 对话）
    enableRag.value = systemConfigStore.chatEnableRagDefault && agentSupportsRag.value

    await refreshSessionList()

    if (sessions.value.length) {
      await loadSessionMessages(sessions.value[0].conversationId)
    } else {
      try {
        await createAndSelectSession()
      } catch (error) {
        console.warn('自动新建会话失败，展示欢迎页', error)
        conversationId.value = undefined
        applyWelcome()
      }
    }
  } catch (error) {
    console.error('初始化会话失败:', error)
    if (!messages.value.length) applyWelcome()
  } finally {
    historyLoading.value = false
    sessionsLoading.value = false
  }
}

const ensureActiveSession = async () => {
  if (conversationId.value) return conversationId.value
  const created = await createAndSelectSession()
  return created.conversationId
}

/** 根据进度事件生成先行展示文案（最终 message 到达后会被替换） */
const formatProgressInterim = (events: AgentTraceEvent[]): string => {
  const lines: string[] = []
  const intent = [...events]
    .reverse()
    .find((e) => e.type === 'NODE_END' && (e.name?.includes('意图') || e.name?.includes('澄清')))
  if (intent?.detail?.trim()) {
    lines.push(`**已理解意图**\n\n${intent.detail.trim()}`)
  }
  const lastTool = [...events]
    .reverse()
    .find((e) => e.type === 'TOOL_CALL' || e.type === 'TOOL_RESULT')
  const lastStart = [...events].reverse().find((e) => e.type === 'NODE_START')
  if (lastTool?.type === 'TOOL_CALL' && lastTool.name) {
    lines.push(`\n\n_正在查询数据：${lastTool.name}…_`)
  } else if (lastTool?.type === 'TOOL_RESULT') {
    lines.push('\n\n_正在整理最终回答…_')
  } else if (lastStart?.name) {
    if (lastStart.name.includes('意图') || lastStart.name.includes('澄清')) {
      if (!intent) lines.push('_正在理解意图…_')
    } else if (
      lastStart.name.includes('工具') ||
      lastStart.name.includes('查询') ||
      lastStart.name.includes('执行')
    ) {
      lines.push('\n\n_正在查询数据…_')
    } else if (lastStart.name.includes('润色') || lastStart.name.includes('生成')) {
      lines.push('\n\n_正在整理最终回答…_')
    } else if (lastStart.name.includes('路由')) {
      lines.push('_正在分析问题类型…_')
    } else if (lastStart.name.includes('RAG') || lastStart.name.includes('知识')) {
      lines.push('\n\n_正在检索知识库…_')
    } else {
      lines.push(`\n\n_正在执行：${lastStart.name}…_`)
    }
  } else if (intent) {
    lines.push('\n\n_正在查询数据与生成回答…_')
  }
  return lines.join('').trim()
}

const handleSend = async () => {
  await sendUserMessage()
}

/** 停止当前会话的流式生成（中断 SSE 连接，保留已输出内容） */
const handleStop = () => {
  const cid = conversationId.value
  if (!cid) return
  aiStream.stop(cid)
}

const copyMessage = async (text: string) => {
  const value = text?.trim()
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

const startEdit = async (index: number, content?: string) => {
  if (loading.value) return
  const msg = messages.value[index]
  const text = String(content ?? msg?.content ?? '')
  if (!text) {
    ElMessage.warning('这条问题没有可编辑的内容')
    return
  }
  inputMessage.value = text
  editingIndex.value = index
  await nextTick()
  await nextTick()
  const inst = composerInputRef.value
  const textarea =
    inst?.textarea ?? (inst?.$el instanceof HTMLElement ? inst.$el.querySelector('textarea') : null)
  inst?.focus()
  if (textarea) {
    if (textarea.value !== text) {
      textarea.value = text
      textarea.dispatchEvent(new Event('input', { bubbles: true }))
    }
    const len = textarea.value.length
    textarea.setSelectionRange(len, len)
    textarea.focus()
    textarea.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }
  inst?.resizeTextarea?.()
}

const cancelEdit = () => {
  editingIndex.value = null
}

const sendUserMessage = async (preset?: string) => {
  const message = (preset ?? inputMessage.value).trim()
  if (!message || loading.value) return

  const editIdx = editingIndex.value
  if (editIdx != null) {
    const cid = conversationId.value
    const keepUserTurns = messages.value.slice(0, editIdx).filter((m) => m.role === 'user').length
    if (cid) {
      try {
        await truncateChatSessionMessages(cid, keepUserTurns)
        ensureMessageList(cid).splice(editIdx)
      } catch (error) {
        console.warn('裁剪会话失败:', error)
        ElMessage.warning('未能裁剪历史，将作为新问题发送')
      }
    } else {
      setSessionMessages(undefined, messages.value.slice(0, editIdx))
    }
    editingIndex.value = null
  }

  const activeCid = await ensureActiveSession()
  const list = ensureMessageList(activeCid)

  list.push({
    role: 'user',
    content: message,
    timestamp: new Date(),
  })

  inputMessage.value = ''
  loadingByCid[activeCid] = true
  streamingByCid[activeCid] = false
  if (conversationId.value === activeCid) {
    await scrollToBottom()
  }

  /** 提升到 try 外，便于 catch 中区分「已有部分正文」与「完全无响应」 */
  let assistantIndex = -1

  try {
    if (outputMode.value === 'STREAM') {
      assistantIndex =
        list.push({
          role: 'assistant',
          content: '',
          timestamp: new Date(),
        }) - 1
      /** 收到正文 message 后不再用 progress 临时文案覆盖最终结果 */
      let gotFinalMessage = false
      let awaitingFinal = false

      await chatStream(
        {
          message,
          maxTokens: 2000,
          temperature: 0.7,
          conversationId: activeCid,
          enableRag: enableRag.value && agentSupportsRag.value,
          agentId: selectedAgentId.value,
        },
        {
          onMessage: (chunk) => {
            streamingByCid[activeCid] = true
            const msg = list[assistantIndex]
            if (!gotFinalMessage || awaitingFinal) {
              // 首次正文：替换意图/进度临时文案
              msg.content = chunk
              gotFinalMessage = true
              awaitingFinal = false
            } else {
              msg.content += chunk
            }
            if (conversationId.value === activeCid) {
              void scrollToBottom()
            }
          },
          onProgress: (json) => {
            streamingByCid[activeCid] = true
            try {
              const event = JSON.parse(json) as AgentTraceEvent
              const msg = list[assistantIndex]
              const prev = msg.traces ? [...msg.traces] : []
              prev.push(event)
              msg.traces = prev
              // 真流式结束后仍会推 NODE_END 等 progress，禁止覆盖已输出的正文
              if (gotFinalMessage) {
                return
              }
              const interim = formatProgressInterim(prev)
              if (interim) {
                msg.content = interim
                awaitingFinal = true
              }
            } catch {
              /* ignore */
            }
            if (conversationId.value === activeCid) {
              void scrollToBottom()
            }
          },
          onTrace: (json) => {
            try {
              list[assistantIndex].traces = JSON.parse(json)
            } catch {
              /* ignore */
            }
          },
          onDone: () => {
            // 触发一次内容引用更新，确保 Markdown / 轨迹面板刷新
            const msg = list[assistantIndex]
            if (msg) {
              msg.content = msg.content || ''
              msg.traces = msg.traces ? [...msg.traces] : msg.traces
            }
          },
        },
        // 携带中断信号与首包/空闲超时，避免后端卡死时前端无限挂起
        aiStream.begin(activeCid),
      )

      if (!list[assistantIndex].content) {
        list[assistantIndex].content = '（无回复内容）'
      }
    } else {
      const response = await chatStructured({
        message,
        type: outputMode.value,
        conversationId: activeCid,
        maxTokens: 4096,
        temperature: 0.2,
        agentId: selectedAgentId.value,
      })
      list.push({
        role: 'assistant',
        content: response.content || '',
        timestamp: new Date(response.timestamp || Date.now()),
        structured: response.structured,
      })
    }

    try {
      await refreshSessionList()
    } catch (error) {
      console.warn('刷新会话列表失败:', error)
    }

    if (conversationId.value === activeCid) {
      await scrollToBottom()
    }
  } catch (error) {
    const isCurrent = conversationId.value === activeCid
    const detail = error instanceof Error ? error.message : '发送失败，请稍后重试'
    const partial = assistantIndex >= 0 ? list[assistantIndex] : undefined

    if (isAiSseUserAbort(error)) {
      // 用户主动停止：保留已输出的部分正文并标记中断，不按错误处理
      if (partial) {
        partial.stopped = true
        if (!partial.content) partial.content = '（已停止，未收到回复内容）'
      }
      if (isCurrent) {
        ElMessage.info('已停止生成')
        await scrollToBottom()
      }
    } else {
      console.error('聊天失败:', error)
      const failureText = isAiSseTimeout(error) ? `抱歉，${detail}` : '抱歉，请求失败，请稍后重试。'
      if (partial?.content) {
        // 超时前已输出部分正文：保留内容并标记中断，避免整段回复丢失
        partial.stopped = true
      } else if (partial) {
        partial.content = failureText
      } else {
        list.push({
          role: 'assistant',
          content: failureText,
          timestamp: new Date(),
        })
      }
      if (isCurrent) {
        ElMessage.error(detail)
        await scrollToBottom()
      } else {
        ElMessage.warning('后台会话回复失败，切回该会话可查看详情')
      }
    }
  } finally {
    aiStream.stop(activeCid)
    loadingByCid[activeCid] = false
    streamingByCid[activeCid] = false
  }
}

/** 清空当前会话：删除后新建空会话 */
const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确认清空当前会话？将删除本会话记录并新建空白会话。', '清空对话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    const cid = conversationId.value
    if (cid) {
      await deleteChatSession(cid)
      clearSessionRuntime(cid)
    }
    await createAndSelectSession()
    await refreshSessionList()
    ElMessage.success('已清空对话')
  } catch (error) {
    console.error('清空对话失败:', error)
  }
}

const handleHealthCheck = async () => {
  try {
    const [health, evalResult] = await Promise.all([healthCheck(), chatEval().catch(() => null)])
    if (evalResult && typeof evalResult.passed === 'boolean') {
      ElMessage.success(
        `服务: ${health} | 评测: ${evalResult.passed ? '通过' : '未通过'} (${evalResult.passedCount}/${evalResult.totalCount})`,
      )
    } else {
      ElMessage.success(`服务状态: ${health}`)
    }
  } catch (error) {
    console.error('健康检查失败:', error)
  }
}

useRouteActivate(loadHistory)
</script>

<style lang="scss" scoped>
.ai-chat-page {
  height: calc(100vh - 120px);
  min-height: 560px;
}

.ai-chat-shell {
  height: 100%;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;

  &.is-maximized {
    position: fixed;
    inset: 0;
    z-index: 2100;
    width: 100vw;
    height: 100vh;
    border-radius: 0;
    border: none;
  }
}

.session-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #e8eef3;
  background: linear-gradient(180deg, #f7fafc 0%, #f3f6f9 100%);

  &__brand {
    padding: 18px 16px 10px;
  }

  &__eyebrow {
    display: block;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: #0891b2;
  }

  &__title {
    margin: 6px 0 0;
    font-size: 17px;
    font-weight: 700;
    color: #1d2129;
  }

  &__new {
    margin: 0 16px 12px;
    width: calc(100% - 32px);
  }

  &__list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 0 10px 14px;
  }

  &__empty {
    padding: 28px 12px;
    text-align: center;
    font-size: 13px;
    color: #909399;
    line-height: 1.6;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    margin-bottom: 4px;
    padding: 10px 8px;
    border: none;
    border-radius: 10px;
    background: transparent;
    text-align: left;
    cursor: pointer;
    transition: background-color 0.15s ease;

    &:hover {
      background: rgba(64, 158, 255, 0.08);

      .session-rail__item-actions {
        opacity: 1;
      }
    }

    &.is-active {
      background: #ecf5ff;

      .session-rail__item-title {
        color: var(--el-color-primary);
        font-weight: 600;
      }

      .session-rail__item-icon {
        color: var(--el-color-primary);
      }
    }

    &.is-pending {
      .session-rail__item-icon {
        color: #0891b2;
      }

      .session-rail__pending {
        color: #0891b2;
        font-weight: 600;
      }
    }
  }

  &__item-icon {
    flex-shrink: 0;
    font-size: 16px;
    color: #86909c;
  }

  &__item-body {
    flex: 1;
    min-width: 0;
  }

  &__item-title {
    font-size: 13px;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.4;
  }

  &__item-meta {
    margin-top: 3px;
    font-size: 11px;
    color: #909399;
  }

  &__pending {
    display: inline-flex;
    align-items: center;
    gap: 4px;

    &::before {
      content: '';
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #0891b2;
      animation: session-pending-pulse 1.2s ease-in-out infinite;
    }
  }

  &__item-actions {
    flex-shrink: 0;
    display: flex;
    opacity: 0;
    transition: opacity 0.15s ease;

    :deep(.el-button) {
      padding: 2px;
      margin-left: 0;
    }
  }
}

@keyframes session-pending-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid #eef2f6;

  &__left {
    min-width: 0;
    flex: 1;
  }

  &__title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    color: #1d2129;
    flex-shrink: 0;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__agent-line {
    min-width: 0;
    font-size: 12px;
    color: #86909c;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__right {
    flex-shrink: 0;
    display: flex;
    gap: 8px;
  }
}

.chat-config {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
  padding: 10px 24px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f6;

  &__group {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    min-width: 0;

    &--end {
      margin-left: auto;
    }
  }

  &__label {
    font-size: 12px;
    font-weight: 600;
    color: #86909c;
  }

  &__agent {
    width: 180px;
  }

  &__mode {
    width: 124px;
  }

  &__caps {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  &__rag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 2px 10px;
    border-radius: 999px;
    background: #fff;
    border: 1px solid #e4e7ed;
    font-size: 12px;
    color: #606266;

    &.is-disabled {
      opacity: 0.55;
    }
  }
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background:
    radial-gradient(ellipse 80% 50% at 50% 0%, rgba(64, 158, 255, 0.05), transparent 70%), #f5f7fa;

  &__inner {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px 28px 12px;
    width: 100%;
    box-sizing: border-box;
  }
}

.chat-empty {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.typing-row {
  display: flex;
  gap: 12px;
  margin-bottom: 22px;

  &__avatar {
    flex-shrink: 0;
    padding-top: 2px;
    background: linear-gradient(135deg, #409eff, #1a7fd4) !important;
  }

  &__body {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  &__role {
    margin-bottom: 4px;
    font-size: 12px;
    font-weight: 600;
    color: #86909c;
  }
}

.typing-indicator {
  display: inline-flex;
  gap: 4px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e7ed;

  span {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--el-color-primary);
    animation: chat-typing 1.4s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

.chat-composer {
  padding: 12px 24px 18px;
  background: linear-gradient(180deg, rgba(245, 247, 250, 0) 0%, #f5f7fa 28%);

  &__inner {
    max-width: 1200px;
    margin: 0 auto;
    width: 100%;
    box-sizing: border-box;
    padding: 12px 14px;
    border: 1px solid #dce3eb;
    border-radius: 14px;
    background: #fff;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);

    &.is-editing {
      border-color: rgba(64, 158, 255, 0.55);
      box-shadow: 0 8px 24px rgba(64, 158, 255, 0.12);
    }

    :deep(.el-textarea__inner) {
      box-shadow: none;
      border: none;
      padding: 4px 4px 8px;
      background: transparent;
    }
  }

  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-top: 4px;
  }

  &__tips {
    font-size: 12px;
    color: #909399;
  }

  &__edit-hint {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin: 0 0 8px;
    padding: 0 4px;
    font-size: 12px;
    color: #1d4f91;
  }
}

@keyframes chat-fade-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes chat-typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  30% {
    transform: translateY(-7px);
    opacity: 1;
  }
}

@media (max-width: 960px) {
  .ai-chat-shell {
    grid-template-columns: 200px minmax(0, 1fr);
  }

  .chat-config__caps {
    display: none;
  }

  .chat-topbar,
  .chat-config,
  .chat-composer {
    padding-left: 14px;
    padding-right: 14px;
  }

  .chat-messages__inner {
    padding-left: 14px;
    padding-right: 14px;
  }
}

@media (max-width: 720px) {
  .ai-chat-shell {
    grid-template-columns: 1fr;

    &.is-maximized {
      grid-template-columns: 200px minmax(0, 1fr);
    }
  }

  .session-rail {
    display: none;
  }

  .ai-chat-shell.is-maximized .session-rail {
    display: flex;
  }
}
</style>
