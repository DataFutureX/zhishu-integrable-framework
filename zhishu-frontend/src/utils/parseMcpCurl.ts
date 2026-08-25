export interface ParsedMcpUpstreamSnippet {
  protocol: 'STREAMABLE_HTTP' | 'SSE'
  baseUrl: string
  endpoint: string
  authHeader?: string
  requestTimeoutMs?: number
  code: string
  name: string
}

function slugFromHost(host: string): string {
  const raw = host.split('.')[0] || 'mcp'
  const slug = raw.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '')
  const code = slug.replace(/^[^a-z]+/, '') || 'mcp'
  return code.slice(0, 32)
}

function extractHeader(text: string, name: string): string | undefined {
  const re = new RegExp(`(?:^|[\\s\\\\])-H\\s+["']${name}\\s*:\\s*([^"']+)["']`, 'i')
  const match = text.match(re)
  return match?.[1]?.trim()
}

function extractUrl(text: string): string | undefined {
  const quoted = text.match(/https?:\/\/[^\s"'\\]+/i)
  if (quoted?.[0]) return quoted[0]
  const plain = text.trim()
  if (/^https?:\/\//i.test(plain)) return plain.split(/\s/)[0]
  return undefined
}

/**
 * 从 curl / 纯 URL 解析 MCP 上游登记字段。
 */
export function parseMcpUpstreamSnippet(raw: string): ParsedMcpUpstreamSnippet | null {
  const text = raw.trim()
  if (!text) return null
  const urlText = extractUrl(text)
  if (!urlText) return null
  let url: URL
  try {
    url = new URL(urlText)
  } catch {
    return null
  }
  const endpoint = url.pathname && url.pathname !== '/' ? url.pathname : '/mcp'
  const baseUrl = `${url.protocol}//${url.host}`
  const auth =
    extractHeader(text, 'Authorization') ||
    extractHeader(text, 'authorization')
  const protocol = /sse/i.test(endpoint) || /sse/i.test(text) ? 'SSE' : 'STREAMABLE_HTTP'
  const code = slugFromHost(url.hostname)
  return {
    protocol,
    baseUrl,
    endpoint,
    authHeader: auth,
    code,
    name: url.hostname,
  }
}
