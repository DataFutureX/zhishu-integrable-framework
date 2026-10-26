import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

/**
 * 多会话聊天运行时状态（AIChat / DocumentQA 共用）。
 *
 * 两个页面原先各自维护一份 messageCache / loadingByCid / streamingByCid，
 * 逻辑逐字重复且容易漂移；这里统一为按 conversationId 维度隔离的运行时缓存，
 * 支持会话切换后回看进行中的流式回复。
 */

export interface ChatMessageBase {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  /** 流式被中断（用户停止或超时），已输出的部分正文予以保留 */
  stopped?: boolean
}

export interface UseChatSessionOptions {
  /** 是否在无 conversationId 时返回 fallback 列表（AIChat 新建会话用），默认 false */
  useFallback?: boolean
}

export interface ChatSessionRuntime<T extends ChatMessageBase> {
  conversationId: Ref<string | undefined>
  messages: ComputedRef<T[]>
  loading: ComputedRef<boolean>
  streamingStarted: ComputedRef<boolean>
  loadingByCid: Record<string, boolean>
  streamingByCid: Record<string, boolean>
  /** 直接访问按会话隔离的消息缓存（需要原地替换历史记录时使用） */
  messageCache: Record<string, T[]>
  ensureMessageList: (cid: string) => T[]
  setSessionMessages: (cid: string | undefined, list: T[]) => void
  clearSessionRuntime: (cid: string) => void
  isSessionPending: (cid: string) => boolean
  setSessionLoading: (cid: string, pending: boolean) => void
  setSessionStreaming: (cid: string, streaming: boolean) => void
  resetRuntime: () => void
}

export function useChatSession<T extends ChatMessageBase>(
  options: UseChatSessionOptions = {},
): ChatSessionRuntime<T> {
  const useFallback = options.useFallback === true

  const conversationId = ref<string | undefined>(undefined)
  const messageCache = reactive<Record<string, T[]>>({})
  const loadingByCid = reactive<Record<string, boolean>>({})
  const streamingByCid = reactive<Record<string, boolean>>({})
  const fallbackMessages = ref([]) as Ref<T[]>

  const messages = computed<T[]>(() => {
    const cid = conversationId.value
    if (!cid) return useFallback ? fallbackMessages.value : []
    return messageCache[cid] ?? []
  }) as ComputedRef<T[]>

  const loading = computed(() => {
    const cid = conversationId.value
    return !!cid && !!loadingByCid[cid]
  })

  const streamingStarted = computed(() => {
    const cid = conversationId.value
    return !!cid && !!streamingByCid[cid]
  })

  const ensureMessageList = (cid: string): T[] => {
    if (!messageCache[cid]) {
      messageCache[cid] = []
    }
    return messageCache[cid]
  }

  const setSessionMessages = (cid: string | undefined, list: T[]) => {
    if (!cid) {
      fallbackMessages.value = list
      return
    }
    messageCache[cid] = list
  }

  const clearSessionRuntime = (cid: string) => {
    delete messageCache[cid]
    delete loadingByCid[cid]
    delete streamingByCid[cid]
  }

  const isSessionPending = (cid: string) => !!loadingByCid[cid]

  const setSessionLoading = (cid: string, pending: boolean) => {
    loadingByCid[cid] = pending
  }

  const setSessionStreaming = (cid: string, streaming: boolean) => {
    streamingByCid[cid] = streaming
  }

  const resetRuntime = () => {
    Object.keys(messageCache).forEach((cid) => delete messageCache[cid])
    Object.keys(loadingByCid).forEach((cid) => delete loadingByCid[cid])
    Object.keys(streamingByCid).forEach((cid) => delete streamingByCid[cid])
    fallbackMessages.value = []
  }

  return {
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
    setSessionLoading,
    setSessionStreaming,
    resetRuntime,
  }
}
