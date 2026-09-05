<template>
  <div class="doc-qa-page">
    <div class="doc-qa-shell" :class="{ 'is-maximized': isMaximized }">
      <!-- 左侧：检索记录（会话） -->
      <aside class="session-rail">
        <div class="session-rail__brand">
          <span class="session-rail__eyebrow">智能中心</span>
          <h2 class="session-rail__title">检索记录</h2>
        </div>
        <el-button
          class="session-rail__new"
          type="primary"
          :icon="Plus"
          :disabled="asking"
          @click="handleCreateSession()"
        >
          新建检索
        </el-button>
        <div v-loading="sessionsLoading" class="session-rail__list">
          <div v-if="!sessions.length && !sessionsLoading" class="session-rail__empty">
            暂无检索记录，点击上方新建开始问答
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
            <el-icon class="session-rail__item-icon"><Reading /></el-icon>
            <div class="session-rail__item-body">
              <div class="session-rail__item-title" :title="session.title">
                {{ session.title || '新检索' }}
              </div>
              <div class="session-rail__item-meta">
                <span v-if="isSessionPending(session.conversationId)" class="session-rail__pending">
                  检索中…
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
                title="删除"
                @click="handleDeleteSession(session)"
              />
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧：问答主区 -->
      <section class="qa-main">
        <header class="qa-header">
          <div class="header-title">
            <el-icon :size="22" color="var(--el-color-primary)"><Reading /></el-icon>
            <div class="header-title__text">
              <h1>{{ currentSessionTitle || '知识检索' }}</h1>
              <span class="header-title__sub">基于已上传知识库文档的 RAG 智能问答</span>
            </div>
            <el-button
              v-if="conversationId"
              link
              type="primary"
              :icon="Edit"
              title="重命名"
              :disabled="asking"
              @click="handleRenameCurrentSession"
            />
          </div>
          <div class="header-actions">
            <el-button
              size="small"
              plain
              :icon="isMaximized ? ScaleToOriginal : FullScreen"
              :title="isMaximized ? '退出全屏 (Esc)' : '全屏最大化'"
              @click="toggleMaximize"
            >
              {{ isMaximized ? '退出全屏' : '全屏' }}
            </el-button>
            <el-select
              v-model="selectedDocumentId"
              clearable
              filterable
              placeholder="全部已上传文档"
              style="width: 220px"
              :disabled="asking"
            >
              <el-option
                v-for="doc in documentOptions"
                :key="doc.id"
                :label="doc.fileName"
                :value="doc.id"
                :disabled="!doc.processed"
              >
                <span>{{ doc.fileName }}</span>
                <el-tag v-if="!doc.processed" size="small" type="warning" style="margin-left: 8px">
                  未处理
                </el-tag>
              </el-option>
            </el-select>
            <el-input-number
              v-model="topK"
              :min="1"
              :max="10"
              controls-position="right"
              :disabled="asking"
              style="width: 100px"
            />
            <span class="topk-label">片段数</span>
            <el-button size="small" :icon="Refresh" :loading="docsLoading" @click="loadDocuments">
              刷新文档
            </el-button>
            <el-button size="small" :icon="Delete" :disabled="asking" @click="handleClear">
              清空本会话
            </el-button>
          </div>
        </header>

        <div ref="messagesRef" v-loading="historyLoading" class="qa-messages">
          <div v-if="messages.length === 0" class="empty-state">
            <el-empty description="选择或新建检索记录后提问，支持多轮与流式回答" />
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-item', msg.role]"
          >
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :size="40" :style="{ backgroundColor: userAvatarColor }">
                {{ userInitial }}
              </el-avatar>
              <el-avatar v-else :size="40" style="background-color: var(--el-color-primary)">
                <el-icon><Reading /></el-icon>
              </el-avatar>
            </div>
            <div class="message-content">
              <template v-if="msg.role === 'assistant'">
                <div class="message-reply-block">
                  <div class="message-text markdown-body" v-html="renderMarkdown(msg.content)" />
                  <div v-if="msg.stopped" class="message-stopped">
                    <el-icon><VideoPause /></el-icon>
                    回复已中断，以上为已接收的部分内容
                  </div>
                  <div v-if="msg.content" class="message-actions">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      :icon="DocumentCopy"
                      @click="copyMessage(msg.content)"
                    >
                      复制
                    </el-button>
                  </div>
                </div>
              </template>
              <div v-else class="message-user-block">
                <div
                  class="message-text"
                  :class="{ 'is-editing': editingIndex === index }"
                >
                  {{ msg.content }}
                </div>
                <div class="message-actions">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :icon="DocumentCopy"
                    @click="copyMessage(msg.content)"
                  >
                    复制
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :icon="EditPen"
                    :disabled="asking"
                    @click.stop="startEdit(index, msg.content)"
                  >
                    编辑
                  </el-button>
                </div>
              </div>
              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>
          </div>

          <div v-if="asking && !streamingStarted" class="message-item assistant">
            <div class="message-avatar">
              <el-avatar :size="40" style="background-color: var(--el-color-primary)">
                <el-icon><Reading /></el-icon>
              </el-avatar>
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span /><span /><span />
              </div>
            </div>
          </div>
        </div>

        <div class="qa-input-area">
          <div class="scope-tip">
            当前检索范围：
            <strong>{{ scopeLabel }}</strong>
            <span class="stream-tip"> · 流式多轮</span>
          </div>
          <div class="qa-composer" :class="{ 'is-editing': editingIndex != null }">
            <p v-if="editingIndex != null" class="qa-composer__edit-hint">
              正在编辑此前的问题，发送后将替换该轮及之后的对话
              <el-button link type="primary" size="small" @click="cancelEdit">取消编辑</el-button>
            </p>
            <el-input
              :key="composerKey"
              ref="composerInputRef"
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              :placeholder="
                editingIndex != null
                  ? '修改问题后点击重新提问… Ctrl+Enter 发送'
                  : '请输入关于文档的问题…'
              "
              :disabled="asking || !conversationId"
              @keydown.enter.ctrl="handleSend"
              @keydown.esc="editingIndex != null ? cancelEdit() : undefined"
            />
          </div>
          <div class="input-actions">
            <div class="input-tips">
              <template v-if="!conversationId">请先新建或选择左侧检索记录</template>
              <template v-else-if="editingIndex != null">Ctrl + Enter 重新提问</template>
              <template v-else>Ctrl + Enter 发送</template>
            </div>
            <el-button v-if="asking" type="danger" plain @click="handleStop">
              <el-icon><VideoPause /></el-icon>
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              :disabled="!inputMessage.trim() || !conversationId"
              @click="handleSend"
            >
              <el-icon><Promotion /></el-icon>
              {{ editingIndex != null ? '重新提问' : '提问' }}
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIDocumentQA' })

import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import {
  Delete,
  DocumentCopy,
  Edit,
  EditPen,
  Plus,
  Promotion,
  Reading,
  Refresh,
  FullScreen,
  ScaleToOriginal,
  VideoPause,
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import {
  createChatSession,
  deleteChatSession,
  documentQaStream,
  getDocumentList,
  listChatSessionMessages,
  listChatSessions,
  renameChatSession,
  truncateChatSessionMessages,
} from '@/api/ai'
import type { ChatSessionVO } from '@/types/chatSession'
import type { DocumentVO } from '@/types/aiDocument'
import type { QaHistoryVO } from '@/types/qaHistory'
import { useUserStore } from '@/stores/useUserStore'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { usePanelMaximize } from '@/composables/usePanelMaximize'
import { useAiStream } from '@/composables/useAiStream'
import { isAiSseTimeout, isAiSseUserAbort } from '@/utils/aiSse'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  /** 流式被中断（用户停止或超时），已输出的部分回答予以保留 */
  stopped?: boolean
}

const SCENE = 'DOCUMENT_QA' as const
const WELCOME_TEXT = '您好！请选择检索范围后提问，我将基于知识库文档进行流式多轮回答。'
const { isMaximized, toggleMaximize } = usePanelMaximize()
/** 按会话维度管理 SSE 中断，组件卸载时自动断流 */
const aiStream = useAiStream()

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const userStore = useUserStore()
const sessions = ref<ChatSessionVO[]>([])
const sessionsLoading = ref(false)
const conversationId = ref<string | undefined>(undefined)
const messageCache = reactive<Record<string, Message[]>>({})
const loadingByCid = reactive<Record<string, boolean>>({})
const streamingByCid = reactive<Record<string, boolean>>({})

const inputMessage = ref('')
const editingIndex = ref<number | null>(null)
const composerInputRef = ref<InputInstance>()
const composerKey = computed(() =>
  editingIndex.value == null ? 'composer-new' : `composer-edit-${editingIndex.value}`,
)
const docsLoading = ref(false)
const historyLoading = ref(false)
const documents = ref<DocumentVO[]>([])
const selectedDocumentId = ref<string | undefined>(undefined)
const topK = ref(5)
const messagesRef = ref<HTMLElement>()

const documentOptions = computed(() => documents.value)

const messages = computed(() => {
  const cid = conversationId.value
  if (!cid) return []
  return messageCache[cid] ?? []
})

const asking = computed(() => {
  const cid = conversationId.value
  return !!cid && !!loadingByCid[cid]
})

const streamingStarted = computed(() => {
  const cid = conversationId.value
  return !!cid && !!streamingByCid[cid]
})

const currentSessionTitle = computed(() => {
  const cid = conversationId.value
  if (!cid) return ''
  return sessions.value.find((s) => s.conversationId === cid)?.title || '新检索'
})

const userInitial = computed(() => {
  const name = userStore.userName || '管理员'
  return name.charAt(0).toUpperCase()
})

const userAvatarColor = computed(() => {
  const name = userStore.userName || 'admin'
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  const hue = Math.abs(hash) % 360
  return `hsl(${hue}, 65%, 45%)`
})

const scopeLabel = computed(() => {
  if (selectedDocumentId.value === undefined) return '全部已上传文档'
  const doc = documents.value.find((item) => item.id === selectedDocumentId.value)
  return doc?.fileName || `文档 #${selectedDocumentId.value}`
})

function ensureMessageList(cid: string): Message[] {
  if (!messageCache[cid]) messageCache[cid] = []
  return messageCache[cid]
}

function clearSessionRuntime(cid: string) {
  delete messageCache[cid]
  delete loadingByCid[cid]
  delete streamingByCid[cid]
}

function isSessionPending(cid: string) {
  return !!loadingByCid[cid]
}

const renderMarkdown = (content: string) => md.render(content || '')

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

const formatTime = (date: Date) => {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

const formatSessionTime = (value?: string) => {
  if (!value) return ''
  const d = toDate(value)
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const applyWelcome = (cid: string) => {
  messageCache[cid] = [
    {
      role: 'assistant',
      content: WELCOME_TEXT,
      timestamp: new Date(),
    },
  ]
}

const ensureUserReady = async () => {
  userStore.initUserInfo()
  if (!userStore.userInfo?.id && userStore.token) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      console.warn('获取用户信息失败，知识问答历史可能无法按用户隔离', error)
    }
  }
}

const loadDocuments = async () => {
  docsLoading.value = true
  try {
    documents.value = await getDocumentList()
  } catch (error) {
    console.error('加载文档列表失败:', error)
  } finally {
    docsLoading.value = false
  }
}

const refreshSessionList = async () => {
  sessions.value = await listChatSessions(SCENE)
}

const loadSessionMessages = async (cid: string, opts?: { force?: boolean }) => {
  if (!opts?.force && messageCache[cid]?.length) {
    // 后台轻量同步
    try {
      const records = await listChatSessionMessages(cid)
      if (records?.length && !loadingByCid[cid]) {
        messageCache[cid] = historyToMessages(records)
      }
    } catch (error) {
      console.warn('后台同步检索消息失败:', error)
    }
    return
  }

  historyLoading.value = true
  try {
    const records = await listChatSessionMessages(cid)
    if (records?.length) {
      messageCache[cid] = historyToMessages(records)
    } else {
      applyWelcome(cid)
    }
  } catch (error) {
    console.error('加载检索消息失败:', error)
    if (!messageCache[cid]?.length) applyWelcome(cid)
    ElMessage.error('加载检索消息失败')
  } finally {
    historyLoading.value = false
  }
}

const handleSelectSession = async (session: ChatSessionVO) => {
  if (session.conversationId === conversationId.value) return
  cancelEdit()
  conversationId.value = session.conversationId
  await loadSessionMessages(session.conversationId)
  await scrollToBottom()
}

const handleCreateSession = async (opts?: { title?: string; silent?: boolean }) => {
  try {
    cancelEdit()
    const created = await createChatSession({
      scene: SCENE,
      title: opts?.title?.trim() || '新检索',
    })
    await refreshSessionList()
    conversationId.value = created.conversationId
    applyWelcome(created.conversationId)
    if (!opts?.silent) {
      ElMessage.success('已新建检索记录')
    }
    await scrollToBottom()
  } catch (error) {
    console.error('新建检索失败:', error)
    ElMessage.error(error instanceof Error ? error.message : '新建检索失败')
  }
}

const renameSessionById = async (cid: string, currentTitle?: string) => {
  const { value } = await ElMessageBox.prompt('请输入新的检索标题', '重命名', {
    inputValue: currentTitle || '新检索',
    confirmButtonText: '保存',
    cancelButtonText: '取消',
    inputValidator: (v) => (v?.trim() ? true : '标题不能为空'),
  })
  await renameChatSession(cid, { title: value.trim() })
  await refreshSessionList()
  ElMessage.success('已重命名')
}

const handleRenameSession = async (session: ChatSessionVO) => {
  try {
    await renameSessionById(session.conversationId, session.title)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '重命名失败')
    }
  }
}

const handleRenameCurrentSession = async () => {
  const cid = conversationId.value
  if (!cid) return
  try {
    await renameSessionById(cid, currentSessionTitle.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '重命名失败')
    }
  }
}

const handleDeleteSession = async (session: ChatSessionVO) => {
  try {
    await ElMessageBox.confirm(
      `确认删除检索记录「${session.title || '新检索'}」？此操作不可恢复。`,
      '删除检索',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteChatSession(session.conversationId)
    clearSessionRuntime(session.conversationId)
    await refreshSessionList()
    if (conversationId.value === session.conversationId) {
      if (sessions.value.length) {
        const nextId = sessions.value[0].conversationId
        conversationId.value = nextId
        await loadSessionMessages(nextId, { force: true })
      } else {
        conversationId.value = undefined
        await handleCreateSession({ silent: true })
      }
    }
    ElMessage.success('已删除')
  } catch (error) {
    console.error('删除检索失败:', error)
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

const handleClear = async () => {
  const cid = conversationId.value
  if (!cid) return
  try {
    await ElMessageBox.confirm('确认清空当前检索会话？将删除本会话记录并新建空白检索。', '清空本会话', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteChatSession(cid)
    clearSessionRuntime(cid)
    await refreshSessionList()
    await handleCreateSession({ silent: true })
    ElMessage.success('已清空并新建检索')
  } catch (error) {
    console.error('清空检索失败:', error)
    ElMessage.error(error instanceof Error ? error.message : '清空失败')
  }
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
  if (asking.value) return
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
    inst?.textarea ??
    (inst?.$el instanceof HTMLElement ? inst.$el.querySelector('textarea') : null)
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

/** 停止当前检索会话的流式回答（中断 SSE 连接，保留已输出内容） */
const handleStop = () => {
  const cid = conversationId.value
  if (!cid) return
  aiStream.stop(cid)
}

const handleSend = async () => {
  const question = inputMessage.value.trim()
  if (!question || asking.value) return

  let activeCid = conversationId.value
  if (!activeCid) {
    await handleCreateSession({ silent: true })
    activeCid = conversationId.value
  }
  if (!activeCid) return

  if (selectedDocumentId.value !== undefined) {
    const doc = documents.value.find((item) => item.id === selectedDocumentId.value)
    if (doc && !doc.processed) {
      ElMessage.warning('所选文档尚未完成向量化，请稍后再试或选择其他文档')
      return
    }
  }

  const editIdx = editingIndex.value
  if (editIdx != null) {
    const keepUserTurns = messages.value.slice(0, editIdx).filter((m) => m.role === 'user').length
    try {
      await truncateChatSessionMessages(activeCid, keepUserTurns)
      ensureMessageList(activeCid).splice(editIdx)
    } catch (error) {
      console.warn('裁剪检索会话失败:', error)
      ElMessage.warning('未能裁剪历史，将作为新问题发送')
    }
    editingIndex.value = null
  }

  const list = ensureMessageList(activeCid)
  list.push({ role: 'user', content: question, timestamp: new Date() })
  inputMessage.value = ''
  loadingByCid[activeCid] = true
  streamingByCid[activeCid] = false
  if (conversationId.value === activeCid) await scrollToBottom()

  const assistantIndex =
    list.push({
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    }) - 1

  try {
    await documentQaStream(
      {
        question,
        documentId: selectedDocumentId.value,
        topK: topK.value,
        conversationId: activeCid,
      },
      {
        onMessage: (chunk) => {
          streamingByCid[activeCid!] = true
          list[assistantIndex].content += chunk
          if (conversationId.value === activeCid) void scrollToBottom()
        },
        onDone: (cid) => {
          if (cid && cid !== '[DONE]' && cid !== activeCid) {
            // 后端若下发新 cid，保持本会话不变（已显式绑定）
          }
        },
      },
      // 携带中断信号与首包/空闲超时，避免后端卡死时前端无限挂起
      aiStream.begin(activeCid),
    )

    if (!list[assistantIndex].content) {
      list[assistantIndex].content = '（无回复内容）'
    }

    // 首问自动用问题作标题
    const session = sessions.value.find((s) => s.conversationId === activeCid)
    if (session && (!session.title || session.title === '新检索')) {
      const title = question.length > 24 ? `${question.slice(0, 24)}…` : question
      try {
        await renameChatSession(activeCid, { title })
      } catch {
        /* ignore */
      }
    }
    try {
      await refreshSessionList()
    } catch (error) {
      console.warn('刷新检索列表失败:', error)
    }
  } catch (error) {
    const detail = error instanceof Error ? error.message : '提问失败'
    const partial = list[assistantIndex]

    if (isAiSseUserAbort(error)) {
      // 用户主动停止：保留已检索到的部分回答并标记中断，不按错误处理
      partial.stopped = true
      if (!partial.content) partial.content = '（已停止，未接收到回答内容）'
      if (conversationId.value === activeCid) ElMessage.info('已停止回答')
    } else {
      console.error('知识问答失败:', error)
      if (partial.content) {
        // 超时前已输出部分回答：保留内容并标记中断，避免整段回答丢失
        partial.stopped = true
      } else {
        partial.content = isAiSseTimeout(error)
          ? `抱歉，${detail}`
          : '抱歉，知识问答请求失败，请稍后重试。'
      }
      if (conversationId.value === activeCid) ElMessage.error(detail)
    }
  } finally {
    aiStream.stop(activeCid)
    loadingByCid[activeCid] = false
    streamingByCid[activeCid] = false
    if (conversationId.value === activeCid) await scrollToBottom()
  }
}

const loadPage = async () => {
  await ensureUserReady()
  await loadDocuments()
  sessionsLoading.value = true
  try {
    await refreshSessionList()
    if (!sessions.value.length) {
      await handleCreateSession({ silent: true })
    } else if (!conversationId.value) {
      const nextId = sessions.value[0].conversationId
      conversationId.value = nextId
      await loadSessionMessages(nextId, { force: true })
    } else {
      await loadSessionMessages(conversationId.value, { force: true })
    }
    await scrollToBottom()
  } catch (error) {
    console.error('初始化知识检索失败:', error)
    ElMessage.error('加载检索记录失败')
  } finally {
    sessionsLoading.value = false
  }
}

useRouteActivate(loadPage)
</script>

<style lang="scss" scoped>
.doc-qa-page {
  height: calc(100vh - 120px);
  min-height: 560px;
}

.doc-qa-shell {
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
      .session-rail__pending {
        color: var(--el-color-warning);
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
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__item-meta {
    margin-top: 2px;
    font-size: 11px;
    color: #909399;
  }

  &__item-actions {
    display: flex;
    flex-shrink: 0;
    opacity: 0;
    transition: opacity 0.15s ease;
  }
}

.qa-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.qa-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .header-title {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;

    &__text {
      min-width: 0;

      h1 {
        margin: 0;
        font-size: 17px;
        font-weight: 700;
        color: var(--el-text-color-primary);
        line-height: 1.3;
      }
    }

    &__sub {
      display: block;
      margin-top: 2px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .topk-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-left: -4px;
  }
}

.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--el-fill-color-lighter);

  .empty-state {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .message-item {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    animation: fadeIn 0.3s ease-in;

    &.user {
      flex-direction: row-reverse;

      .message-content {
        align-items: flex-end;

        .message-text {
          background: var(--el-color-primary);
          color: #fff;
          white-space: pre-wrap;

          &.is-editing {
            outline: 2px solid rgba(255, 255, 255, 0.85);
            box-shadow: 0 0 0 3px color-mix(in srgb, var(--el-color-primary) 35%, transparent);
          }
        }
      }
    }

    &.assistant .message-content {
      align-items: flex-start;

      .message-text {
        background: var(--el-bg-color);
        color: var(--el-text-color-primary);
      }
    }

    .message-avatar {
      flex-shrink: 0;
    }

    .message-content {
      display: flex;
      flex-direction: column;
      max-width: min(720px, 78%);

      .message-text {
        padding: 12px 16px;
        border-radius: 8px;
        line-height: 1.6;
        word-wrap: break-word;
        box-shadow: 0 1px 2px rgb(0 0 0 / 5%);

        &.markdown-body {
          :deep(p) {
            margin-bottom: 8px;
            line-height: 1.8;

            &:last-child {
              margin-bottom: 0;
            }
          }

          :deep(ul),
          :deep(ol) {
            margin: 8px 0;
            padding-left: 20px;
          }

          :deep(code) {
            padding: 2px 6px;
            background: rgb(0 0 0 / 8%);
            border-radius: 3px;
            font-family: Consolas, Monaco, monospace;
            font-size: 0.9em;
          }

          :deep(pre) {
            margin: 8px 0;
            padding: 12px;
            background: #f6f8fa;
            border-radius: 6px;
            overflow-x: auto;

            code {
              padding: 0;
              background: transparent;
            }
          }
        }
      }

      .message-user-block,
      .message-reply-block {
        max-width: 100%;

        .message-actions {
          opacity: 0;
          pointer-events: none;
        }

        &:hover .message-actions,
        &:focus-within .message-actions {
          opacity: 1;
          pointer-events: auto;
        }
      }

      .message-user-block {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
      }

      .message-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 2px;
        margin-top: 4px;
        transition: opacity 0.15s ease;

        :deep(.el-button) {
          padding: 0 4px;
          height: 22px;
          font-size: 12px;
        }
      }

      .message-stopped {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-top: 6px;
        font-size: 12px;
        line-height: 1.4;
        color: var(--el-text-color-secondary);
      }

      .message-time {
        margin-top: 4px;
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 12px 16px;
    background: var(--el-bg-color);
    border-radius: 8px;
    box-shadow: 0 1px 2px rgb(0 0 0 / 5%);

    span {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--el-color-primary);
      animation: typing 1.4s infinite;

      &:nth-child(2) {
        animation-delay: 0.2s;
      }

      &:nth-child(3) {
        animation-delay: 0.4s;
      }
    }
  }
}

.qa-input-area {
  padding: 16px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);

  .scope-tip {
    margin-bottom: 10px;
    font-size: 13px;
    color: var(--el-text-color-secondary);

    strong {
      color: var(--el-color-primary);
      font-weight: 600;
    }

    .stream-tip {
      color: var(--el-text-color-placeholder);
    }
  }

  .qa-composer {
    &.is-editing {
      :deep(.el-textarea__inner) {
        border-color: var(--el-color-primary-light-5);
      }
    }

    &__edit-hint {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin: 0 0 8px;
      font-size: 12px;
      color: var(--el-color-primary);
    }
  }

  .input-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;

    .input-tips {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.7;
  }

  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

@media (max-width: 900px) {
  .doc-qa-shell {
    grid-template-columns: 1fr;

    &.is-maximized {
      grid-template-columns: 200px minmax(0, 1fr);
    }
  }

  .session-rail {
    display: none;
  }

  .doc-qa-shell.is-maximized .session-rail {
    display: flex;
  }
}
</style>
