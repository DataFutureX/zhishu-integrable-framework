import { describe, expect, it, vi } from 'vitest'
import { openSseStream } from '@/utils/sseStream'

/** 构造一个只按 chunk 吐字节的假 Response，避免依赖 jsdom 的 fetch / ReadableStream */
function mockResponse(chunks: string[], init: { ok?: boolean; status?: number } = {}) {
  const encoder = new TextEncoder()
  let index = 0
  const reader = {
    read: async () => {
      if (index >= chunks.length) return { done: true, value: undefined }
      const value = encoder.encode(chunks[index])
      index += 1
      return { done: false, value }
    },
    cancel: async () => undefined,
  }
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    body: { getReader: () => reader },
  }
}

describe('openSseStream', () => {
  it('按空行切分事件并解析 event 名', async () => {
    const chunks = ['event: announcement\ndata: {"id":"1"}\n\n', 'data: second\n\n']
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(chunks)))

    const events: Array<{ event: string; data: string }> = []
    await openSseStream({ url: '/api/v1/announcements/stream', onEvent: (e) => events.push(e) })

    expect(events).toEqual([
      { event: 'announcement', data: '{"id":"1"}' },
      { event: 'message', data: 'second' },
    ])
    vi.unstubAllGlobals()
  })

  it('跨 chunk 的半个事件会被拼接后再派发', async () => {
    const chunks = ['data: {"a":', '1}\n\n']
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(chunks)))

    const events: Array<{ event: string; data: string }> = []
    await openSseStream({ url: '/sse', onEvent: (e) => events.push(e) })

    expect(events).toHaveLength(1)
    expect(events[0]?.data).toBe('{"a":1}')
    vi.unstubAllGlobals()
  })

  it('多行 data 用换行合并', async () => {
    const chunks = ['data: line1\ndata: line2\n\n']
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(chunks)))

    const events: Array<{ event: string; data: string }> = []
    await openSseStream({ url: '/sse', onEvent: (e) => events.push(e) })

    expect(events[0]?.data).toBe('line1\nline2')
    vi.unstubAllGlobals()
  })

  it('携带 Authorization 头，不把 token 放进 URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(mockResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await openSseStream({
      url: '/api/v1/announcements/stream',
      headers: { Authorization: 'Bearer token-123' },
      onEvent: () => undefined,
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, { headers: Record<string, string> }]
    expect(url).not.toContain('token=')
    expect(init.headers.Authorization).toBe('Bearer token-123')
    expect(init.headers.Accept).toBe('text/event-stream')
    vi.unstubAllGlobals()
  })

  it('非 2xx 响应抛出错误并由调用方决定重连', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse([], { ok: false, status: 401 })))

    await expect(openSseStream({ url: '/sse', onEvent: () => undefined })).rejects.toThrow('401')
    vi.unstubAllGlobals()
  })

  it('连接建立后触发 onOpen', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse([])))
    const onOpen = vi.fn()

    await openSseStream({ url: '/sse', onOpen, onEvent: () => undefined })

    expect(onOpen).toHaveBeenCalledTimes(1)
    vi.unstubAllGlobals()
  })
})
