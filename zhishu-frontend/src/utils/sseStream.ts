/**
 * 基于 fetch 的 SSE 订阅（GET）。
 *
 * 为什么不用原生 EventSource：它无法自定义请求头，只能把 JWT 拼进 URL，
 * 会泄漏到 Nginx access log / 浏览器历史 / Referer。用 fetch + ReadableStream
 * 读同一条 text/event-stream，即可用标准 Authorization 头鉴权。
 */

export interface SseEvent {
  /** 事件名，缺省为 message */
  event: string
  /** data: 行合并后的内容 */
  data: string
}

export interface OpenSseStreamOptions {
  url: string
  headers?: Record<string, string>
  signal?: AbortSignal
  /** 连接建立（收到响应头） */
  onOpen?: () => void
  onEvent: (event: SseEvent) => void
}

/**
 * 订阅 SSE，直到连接结束或被 signal 中断。
 * 网络/鉴权错误通过 reject 抛出，重连策略由调用方决定。
 */
export async function openSseStream(options: OpenSseStreamOptions): Promise<void> {
  const { url, headers, signal, onOpen, onEvent } = options
  const response = await fetch(url, {
    method: 'GET',
    headers: { Accept: 'text/event-stream', ...headers },
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`SSE 连接失败（${response.status}）`)
  }

  onOpen?.()

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
    onEvent({ event: eventName || 'message', data })
    eventName = 'message'
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break

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
  } finally {
    await reader.cancel().catch(() => undefined)
  }
}
