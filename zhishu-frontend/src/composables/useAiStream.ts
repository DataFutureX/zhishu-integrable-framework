import { onScopeDispose, reactive } from 'vue'
import {
  AI_SSE_FIRST_BYTE_TIMEOUT_MS,
  AI_SSE_IDLE_TIMEOUT_MS,
  type AiSseOptions,
} from '@/utils/aiSse'

export interface UseAiStreamOptions {
  /** 首包超时（毫秒），默认 60s */
  firstByteTimeoutMs?: number
  /** 空闲超时（毫秒），默认 45s */
  idleTimeoutMs?: number
}

/**
 * AI 流式请求的中断与超时管理。
 *
 * 按 key（通常为 conversationId）维护独立 AbortController，多会话并发流式互不干扰；
 * 组件作用域销毁时自动中断全部在途请求，避免离开页面后残留幽灵连接与内存泄漏。
 */
export function useAiStream(options: UseAiStreamOptions = {}) {
  const {
    firstByteTimeoutMs = AI_SSE_FIRST_BYTE_TIMEOUT_MS,
    idleTimeoutMs = AI_SSE_IDLE_TIMEOUT_MS,
  } = options

  const controllers = new Map<string, AbortController>()
  /** 在途流式请求的 key 集合，用于驱动「停止生成」按钮的显隐 */
  const activeKeys = reactive<Record<string, boolean>>({})

  /** 中断指定 key 的流式请求（未在途时为空操作） */
  const stop = (key: string) => {
    const controller = controllers.get(key)
    controllers.delete(key)
    delete activeKeys[key]
    controller?.abort()
  }

  /** 开启一次流式请求，返回值可直接透传给 postAiSse / chatStream */
  const begin = (key: string): AiSseOptions => {
    // 同 key 重入时先中断上一次，避免两条流同时写入同一份消息
    stop(key)
    const controller = new AbortController()
    controllers.set(key, controller)
    activeKeys[key] = true
    return { firstByteTimeoutMs, idleTimeoutMs, signal: controller.signal }
  }

  /** 指定 key 是否有流式请求在途 */
  const isActive = (key?: string | null): boolean => !!key && !!activeKeys[key]

  /** 中断全部在途请求 */
  const stopAll = () => {
    for (const key of [...controllers.keys()]) {
      stop(key)
    }
  }

  onScopeDispose(stopAll)

  return { begin, stop, stopAll, isActive }
}
