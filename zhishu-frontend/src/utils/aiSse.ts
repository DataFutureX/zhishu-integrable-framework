import { resolveAiUserId } from '@/utils/aiUser'
import { parseJsonWithBigInt } from '@/utils/parseJson'

export interface AiSseHandlers {
  onMessage?: (chunk: string) => void
  onProgress?: (json: string) => void
  onTrace?: (json: string) => void
  onDone?: (conversationId?: string) => void
  onError?: (message: string) => void
}

/** 流式请求被中断的原因：用户主动停止 / 首包超时 / 空闲超时 */
export type AiSseAbortReason = 'user' | 'first-byte-timeout' | 'idle-timeout'

/**
 * 内置默认阈值：5 分钟。
 * Agent 链路含意图识别、MCP 工具调用、RAG 检索与模型推理，且后端无 SSE 心跳，
 * 单环节静默期可达数分钟；阈值必须显著大于同项目非流式 AI 接口的 120s 基线，
 * 否则会误杀正常执行中的请求。
 */
const DEFAULT_SSE_TIMEOUT_MS = 300_000

/** 解析 .env 中的毫秒阈值；未配置、非法或为负时回落到内置默认值（0 表示不限制） */
function resolveTimeoutMs(raw: string | undefined, fallback: number): number {
  if (raw == null || raw === '') return fallback
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed < 0) return fallback
  return parsed
}

/** 首包超时：等待后端建立 SSE 并下发第一个事件，可用 VITE_AI_SSE_FIRST_BYTE_TIMEOUT_MS 覆盖 */
export const AI_SSE_FIRST_BYTE_TIMEOUT_MS = resolveTimeoutMs(
  import.meta.env.VITE_AI_SSE_FIRST_BYTE_TIMEOUT_MS,
  DEFAULT_SSE_TIMEOUT_MS,
)
/** 空闲超时：流已开始后相邻两个事件的最大静默间隔，可用 VITE_AI_SSE_IDLE_TIMEOUT_MS 覆盖 */
export const AI_SSE_IDLE_TIMEOUT_MS = resolveTimeoutMs(
  import.meta.env.VITE_AI_SSE_IDLE_TIMEOUT_MS,
  DEFAULT_SSE_TIMEOUT_MS,
)

export interface AiSseOptions {
  /** 外部中断信号（如「停止生成」按钮触发的 AbortController.signal） */
  signal?: AbortSignal
  /** 首包超时（毫秒），默认 5 分钟，传 0 表示不限制 */
  firstByteTimeoutMs?: number
  /** 空闲超时（毫秒），默认 5 分钟，传 0 表示不限制 */
  idleTimeoutMs?: number
}

/** SSE 中断错误，reason 用于区分用户主动停止与超时，调用方据此决定是否提示 */
export class AiSseAbortError extends Error {
  readonly reason: AiSseAbortReason

  constructor(reason: AiSseAbortReason, message: string) {
    super(message)
    this.name = 'AiSseAbortError'
    this.reason = reason
  }
}

/** 是否为用户主动停止（预期行为，不应作为错误提示） */
export function isAiSseUserAbort(error: unknown): boolean {
  return error instanceof AiSseAbortError && error.reason === 'user'
}

/** 是否为流式超时中断（需提示用户并允许重试） */
export function isAiSseTimeout(error: unknown): boolean {
  return error instanceof AiSseAbortError && error.reason !== 'user'
}

const ABORT_MESSAGES: Record<AiSseAbortReason, string> = {
  user: '已停止生成',
  'first-byte-timeout': '服务响应超时，请稍后重试',
  'idle-timeout': '服务长时间未返回数据，连接已中断',
}

/**
 * 构造「用户主动中断」错误。
 * 供演示模式的本地流式模拟复用，保证与真实 SSE 的中断行为一致。
 */
export function createUserAbortError(): AiSseAbortError {
  return new AiSseAbortError('user', ABORT_MESSAGES.user)
}

/**
 * 超时守护：串联「首包超时 → 空闲超时」两个阶段，并合并外部中断信号。
 * fetch 无原生超时能力，需在应用层用 AbortController 兜底，避免请求无限挂起。
 */
function createTimeoutGuard(options: AiSseOptions = {}) {
  const {
    signal,
    firstByteTimeoutMs = AI_SSE_FIRST_BYTE_TIMEOUT_MS,
    idleTimeoutMs = AI_SSE_IDLE_TIMEOUT_MS,
  } = options

  const controller = new AbortController()
  let reason: AiSseAbortReason | null = null
  let timer: ReturnType<typeof setTimeout> | null = null

  const clearTimer = () => {
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  const abortWith = (next: AiSseAbortReason) => {
    if (reason) return
    reason = next
    clearTimer()
    controller.abort()
  }

  /** 重新计时；ms <= 0 表示该阶段不做超时限制 */
  const arm = (next: AiSseAbortReason, ms: number) => {
    clearTimer()
    if (reason || ms <= 0) return
    timer = setTimeout(() => abortWith(next), ms)
  }

  const onExternalAbort = () => abortWith('user')
  if (signal) {
    if (signal.aborted) {
      abortWith('user')
    } else {
      signal.addEventListener('abort', onExternalAbort, { once: true })
    }
  }

  return {
    signal: controller.signal,
    get reason(): AiSseAbortReason | null {
      return reason
    },
    /** 进入等待首包阶段 */
    waitForFirstByte: () => arm('first-byte-timeout', firstByteTimeoutMs),
    /** 收到数据，切入空闲监听阶段并续期 */
    touch: () => arm('idle-timeout', idleTimeoutMs),
    dispose: () => {
      clearTimer()
      signal?.removeEventListener('abort', onExternalAbort)
    },
  }
}

/** 将 fetch / reader 抛出的 AbortError 归一化为带原因的错误 */
function toAbortError(
  error: unknown,
  reason: AiSseAbortReason | null,
  handlers: AiSseHandlers,
): Error {
  // 非守护触发的失败（断网、DNS、CORS 等）保留原始错误，交由调用方统一处理
  if (!reason) return error instanceof Error ? error : new Error(String(error))
  const message = ABORT_MESSAGES[reason]
  // 用户主动停止属于预期行为，不触发错误回调
  if (reason !== 'user') handlers.onError?.(message)
  return new AiSseAbortError(reason, message)
}

function aiApiBase(): string {
  if (import.meta.env.DEV) return '/api/v1'
  const base = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return `${base}/v1`
}

function dispatchEvent(event: string, data: string, handlers: AiSseHandlers) {
  if (event === 'progress') {
    handlers.onProgress?.(data)
    return
  }
  if (event === 'trace') {
    handlers.onTrace?.(data)
    return
  }
  if (event === 'done') {
    handlers.onDone?.(data)
    return
  }
  handlers.onMessage?.(data)
}

/**
 * 解析 SSE 文本流（event: message|progress|trace|done）
 *
 * 内置首包超时与空闲超时，超时或外部 signal 中断时抛出 AiSseAbortError。
 */
export async function postAiSse(
  path: string,
  body: unknown,
  handlers: AiSseHandlers,
  options: AiSseOptions = {},
): Promise<void> {
  const url = `${aiApiBase()}${path.startsWith('/') ? path : `/${path}`}`
  const token = localStorage.getItem('token')
  const userId = resolveAiUserId()
  const guard = createTimeoutGuard(options)
  guard.waitForFirstByte()

  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(userId ? { 'X-User-Id': userId } : {}),
      },
      body: JSON.stringify(body),
      signal: guard.signal,
    })
  } catch (error) {
    const normalized = toAbortError(error, guard.reason, handlers)
    guard.dispose()
    throw normalized
  }

  if (!response.ok || !response.body) {
    guard.dispose()
    let detail = `SSE 请求失败（${response.status}）`
    try {
      const text = await response.text()
      const parsed = parseJsonWithBigInt<{ message?: string }>(text)
      if (parsed?.message) detail = parsed.message
    } catch {
      /* ignore */
    }
    handlers.onError?.(detail)
    throw new Error(detail)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  const flush = () => {
    if (!dataLines.length) {
      eventName = 'message'
      return
    }
    const data = dataLines.join('\n')
    dataLines = []
    dispatchEvent(eventName || 'message', data, handlers)
    eventName = 'message'
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      // 收到任意字节即续期：首包阶段切入空闲阶段，空闲阶段重置计时
      guard.touch()
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (line === '') {
          flush()
          continue
        }
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim() || 'message'
          continue
        }
        if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
    }
    flush()
  } catch (error) {
    // 中断后 reader 会抛 AbortError，主动释放底层连接再归一化错误
    await reader.cancel().catch(() => undefined)
    const normalized = toAbortError(error, guard.reason, handlers)
    guard.dispose()
    throw normalized
  }
  guard.dispose()
}
