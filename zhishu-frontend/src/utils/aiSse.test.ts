import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  AI_SSE_FIRST_BYTE_TIMEOUT_MS,
  AI_SSE_IDLE_TIMEOUT_MS,
  AiSseAbortError,
  isAiSseTimeout,
  isAiSseUserAbort,
  postAiSse,
  type AiSseHandlers,
} from './aiSse'

interface ReadResult {
  done: boolean
  value?: Uint8Array
}

interface ControllableStream {
  body: unknown
  push: (text: string) => void
  end: () => void
}

/** 持有 fetch 内部创建的流，便于测试用例控制数据到达时机 */
interface StreamContext {
  stream?: ControllableStream
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

/**
 * 构造可控响应体：由测试决定每个 chunk 的到达时机，
 * 并模拟真实 reader 在 abort 时以 AbortError 拒绝的行为。
 */
function createControllableStream(signal?: AbortSignal | null): ControllableStream {
  const encoder = new TextEncoder()
  const queue: Uint8Array[] = []
  let closed = false
  let aborted = signal?.aborted ?? false
  let pendingResolve: ((result: ReadResult) => void) | null = null
  let pendingReject: ((error: Error) => void) | null = null

  const abortError = () => new DOMException('The operation was aborted.', 'AbortError')

  const settle = () => {
    if (!pendingResolve) return
    const resolve = pendingResolve
    if (queue.length > 0) {
      const value = queue.shift() as Uint8Array
      pendingResolve = null
      pendingReject = null
      resolve({ done: false, value })
      return
    }
    if (closed) {
      pendingResolve = null
      pendingReject = null
      resolve({ done: true })
    }
  }

  const failPending = () => {
    const reject = pendingReject
    pendingResolve = null
    pendingReject = null
    reject?.(abortError())
  }

  if (!aborted) {
    signal?.addEventListener(
      'abort',
      () => {
        aborted = true
        failPending()
      },
      { once: true },
    )
  }

  const reader = {
    read: () =>
      new Promise<ReadResult>((resolve, reject) => {
        if (aborted) {
          reject(abortError())
          return
        }
        pendingResolve = resolve
        pendingReject = reject
        settle()
      }),
    cancel: () => {
      closed = true
      failPending()
      return Promise.resolve()
    },
  }

  return {
    body: { getReader: () => reader },
    push: (text: string) => {
      queue.push(encoder.encode(text))
      settle()
    },
    end: () => {
      closed = true
      settle()
    },
  }
}

/** 注入 fetch 桩，并把内部创建的流回写到 ctx 供用例驱动 */
function stubFetch(ctx: StreamContext) {
  const fetchMock = vi.fn(async (_input: unknown, init?: { signal?: AbortSignal | null }) => {
    const stream = createControllableStream(init?.signal)
    ctx.stream = stream
    return { ok: true, status: 200, body: stream.body } as unknown as Response
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** 立即挂载 handler，避免中断场景产生 unhandled rejection */
function settlePromise(promise: Promise<void>): Promise<unknown> {
  return promise.then(
    () => null,
    (error: unknown) => error,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('postAiSse 事件解析', () => {
  it('按 event 类型分发 progress / message / trace / done', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const handlers: AiSseHandlers = {
      onMessage: vi.fn(),
      onProgress: vi.fn(),
      onTrace: vi.fn(),
      onDone: vi.fn(),
    }

    const pending = settlePromise(postAiSse('/chat/stream', { message: 'hi' }, handlers))
    await sleep(1)
    ctx.stream?.push('event: progress\ndata: {"type":"NODE_START"}\n\n')
    ctx.stream?.push('data: 你好\n\n')
    ctx.stream?.push('event: trace\ndata: []\n\n')
    ctx.stream?.push('event: done\ndata: cid-1\n\n')
    ctx.stream?.end()

    expect(await pending).toBeNull()
    expect(handlers.onProgress).toHaveBeenCalledWith('{"type":"NODE_START"}')
    expect(handlers.onMessage).toHaveBeenCalledWith('你好')
    expect(handlers.onTrace).toHaveBeenCalledWith('[]')
    expect(handlers.onDone).toHaveBeenCalledWith('cid-1')
  })

  it('跨 chunk 拼接不完整的 data 行', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const onMessage = vi.fn()

    const pending = settlePromise(postAiSse('/chat/stream', {}, { onMessage }))
    await sleep(1)
    ctx.stream?.push('data: 前半段')
    ctx.stream?.push('后半段\n\n')
    ctx.stream?.end()

    expect(await pending).toBeNull()
    expect(onMessage).toHaveBeenCalledWith('前半段后半段')
  })

  it('HTTP 错误响应触发 onError 并抛出后端消息', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: false,
        status: 500,
        body: null,
        text: async () => '{"message":"后端异常"}',
      }) as unknown as Response),
    )
    const onError = vi.fn()

    const error = await settlePromise(postAiSse('/chat/stream', {}, { onError }))

    expect(error).toBeInstanceOf(Error)
    expect((error as Error).message).toBe('后端异常')
    expect(onError).toHaveBeenCalledWith('后端异常')
  })
})

describe('postAiSse 超时守护', () => {
  it('首包超时抛出 first-byte-timeout 并提示', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const onError = vi.fn()

    const error = await settlePromise(
      postAiSse(
        '/chat/stream',
        {},
        { onError },
        { firstByteTimeoutMs: 20, idleTimeoutMs: 5000 },
      ),
    )

    expect(error).toBeInstanceOf(AiSseAbortError)
    expect((error as AiSseAbortError).reason).toBe('first-byte-timeout')
    expect(isAiSseTimeout(error)).toBe(true)
    expect(onError).toHaveBeenCalledWith('服务响应超时，请稍后重试')
  })

  it('流已开始后长时间静默触发 idle-timeout', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const onError = vi.fn()

    const pending = settlePromise(
      postAiSse(
        '/chat/stream',
        {},
        { onError },
        { firstByteTimeoutMs: 5000, idleTimeoutMs: 20 },
      ),
    )
    await sleep(1)
    ctx.stream?.push('data: 首段\n\n')

    const error = await pending

    expect(error).toBeInstanceOf(AiSseAbortError)
    expect((error as AiSseAbortError).reason).toBe('idle-timeout')
    expect(onError).toHaveBeenCalledWith('服务长时间未返回数据，连接已中断')
  })

  it('持续收到数据时会续期，不误判超时', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const messages: string[] = []

    // 总耗时（约 50ms）远超单次阈值 25ms，验证每次到达都会重置计时
    const feeder = (async () => {
      while (!ctx.stream) await sleep(1)
      for (let i = 0; i < 5; i++) {
        await sleep(10)
        ctx.stream?.push(`data: chunk-${i}\n\n`)
      }
      ctx.stream?.end()
    })()

    await postAiSse(
      '/chat/stream',
      {},
      { onMessage: (chunk) => messages.push(chunk) },
      { firstByteTimeoutMs: 25, idleTimeoutMs: 25 },
    )
    await feeder

    expect(messages).toEqual(['chunk-0', 'chunk-1', 'chunk-2', 'chunk-3', 'chunk-4'])
  })

  it('阈值传 0 表示不限制超时', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const onMessage = vi.fn()

    const pending = settlePromise(
      postAiSse('/chat/stream', {}, { onMessage }, { firstByteTimeoutMs: 0, idleTimeoutMs: 0 }),
    )
    // 静默时长远超默认阈值，未配置超时时不应被中断
    await sleep(40)
    ctx.stream?.push('data: 迟到但有效\n\n')
    ctx.stream?.end()

    expect(await pending).toBeNull()
    expect(onMessage).toHaveBeenCalledWith('迟到但有效')
  })
})

describe('postAiSse 外部中断', () => {
  it('用户主动停止标记为 user 且不触发 onError', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const controller = new AbortController()
    const onError = vi.fn()

    const pending = settlePromise(
      postAiSse(
        '/chat/stream',
        {},
        { onError },
        { signal: controller.signal, firstByteTimeoutMs: 5000, idleTimeoutMs: 5000 },
      ),
    )
    await sleep(5)
    controller.abort()

    const error = await pending

    expect(error).toBeInstanceOf(AiSseAbortError)
    expect((error as AiSseAbortError).reason).toBe('user')
    expect(isAiSseUserAbort(error)).toBe(true)
    expect(isAiSseTimeout(error)).toBe(false)
    // 主动停止属于预期行为，不应弹错误提示
    expect(onError).not.toHaveBeenCalled()
  })

  it('流式输出中途停止同样标记为 user', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const controller = new AbortController()
    const messages: string[] = []

    const pending = settlePromise(
      postAiSse(
        '/chat/stream',
        {},
        { onMessage: (chunk) => messages.push(chunk) },
        { signal: controller.signal, firstByteTimeoutMs: 5000, idleTimeoutMs: 5000 },
      ),
    )
    await sleep(1)
    ctx.stream?.push('data: 已输出部分\n\n')
    await sleep(1)
    controller.abort()

    const error = await pending

    expect(isAiSseUserAbort(error)).toBe(true)
    // 中断前已到达的内容仍应正常派发，供界面保留
    expect(messages).toEqual(['已输出部分'])
  })

  it('传入已中断的 signal 时立即结束', async () => {
    const ctx: StreamContext = {}
    stubFetch(ctx)
    const controller = new AbortController()
    controller.abort()

    const error = await settlePromise(
      postAiSse(
        '/chat/stream',
        {},
        {},
        { signal: controller.signal, firstByteTimeoutMs: 5000, idleTimeoutMs: 5000 },
      ),
    )

    expect(isAiSseUserAbort(error)).toBe(true)
  })
})

describe('超时阈值配置', () => {
  it('默认阈值为 5 分钟，避免误杀 Agent 长链路执行', () => {
    // Agent 链路（意图识别 / MCP 工具调用 / 模型推理）静默期可达数分钟，后端又无 SSE 心跳，
    // 阈值若小于同项目非流式 AI 接口的 120s 基线就会误杀正常请求
    expect(AI_SSE_FIRST_BYTE_TIMEOUT_MS).toBe(300_000)
    expect(AI_SSE_IDLE_TIMEOUT_MS).toBe(300_000)
  })

  it('支持 .env 覆盖阈值', async () => {
    vi.stubEnv('VITE_AI_SSE_IDLE_TIMEOUT_MS', '1234')
    vi.stubEnv('VITE_AI_SSE_FIRST_BYTE_TIMEOUT_MS', '5678')
    vi.resetModules()

    const reloaded = await import('./aiSse')

    expect(reloaded.AI_SSE_IDLE_TIMEOUT_MS).toBe(1234)
    expect(reloaded.AI_SSE_FIRST_BYTE_TIMEOUT_MS).toBe(5678)
  })

  it('.env 为 0 时关闭超时限制', async () => {
    vi.stubEnv('VITE_AI_SSE_IDLE_TIMEOUT_MS', '0')
    vi.resetModules()

    const reloaded = await import('./aiSse')

    expect(reloaded.AI_SSE_IDLE_TIMEOUT_MS).toBe(0)
  })

  it('.env 为非法值时回落到默认阈值', async () => {
    vi.stubEnv('VITE_AI_SSE_IDLE_TIMEOUT_MS', 'not-a-number')
    vi.stubEnv('VITE_AI_SSE_FIRST_BYTE_TIMEOUT_MS', '-1')
    vi.resetModules()

    const reloaded = await import('./aiSse')

    expect(reloaded.AI_SSE_IDLE_TIMEOUT_MS).toBe(300_000)
    expect(reloaded.AI_SSE_FIRST_BYTE_TIMEOUT_MS).toBe(300_000)
  })
})
