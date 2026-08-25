import { resolveAiUserId } from '@/utils/aiUser'
import { parseJsonWithBigInt } from '@/utils/parseJson'

export interface AiSseHandlers {
  onMessage?: (chunk: string) => void
  onProgress?: (json: string) => void
  onTrace?: (json: string) => void
  onDone?: (conversationId?: string) => void
  onError?: (message: string) => void
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
 */
export async function postAiSse(
  path: string,
  body: unknown,
  handlers: AiSseHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const url = `${aiApiBase()}${path.startsWith('/') ? path : `/${path}`}`
  const token = localStorage.getItem('token')
  const userId = resolveAiUserId()
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(userId ? { 'X-User-Id': userId } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok || !response.body) {
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

  while (true) {
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
}
