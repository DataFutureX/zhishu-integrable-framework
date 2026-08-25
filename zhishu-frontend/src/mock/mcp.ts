import type {
  McpCallLogVO,
  McpClientCreateDTO,
  McpClientUpdateDTO,
  McpClientVO,
  McpOverviewVO,
  McpUpstreamToolVO,
  McpUpstreamUpsertDTO,
  McpUpstreamVO,
} from '@/types/mcp'
import { nowStr } from './utils'

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function randomApiKey(): string {
  const hex = Array.from({ length: 24 }, () => Math.floor(Math.random() * 16).toString(16)).join('')
  return `mcp_live_${hex}`
}

function keyPrefix(apiKey: string): string {
  return apiKey.slice(0, 12)
}

const seedClients = (): McpClientVO[] => [
  {
    id: 1,
    name: 'Cursor 监测只读',
    keyPrefix: 'mcp_live_a1b2',
    boundUserId: 1,
    boundUsername: 'admin',
    capabilities: ['MCP_TOOLS', 'KNOWLEDGE_GRAPH'],
    rpmLimit: 60,
    status: 'ENABLED',
    remark: '演示：他方 IDE 通过 MCP Key 调用本平台只读 Tool',
    lastUsedAt: nowStr(),
    createdBy: 'demo',
    createTime: nowStr(),
  },
  {
    id: 2,
    name: '伙伴简报 Agent',
    keyPrefix: 'mcp_live_c3d4',
    boundUserId: 1,
    boundUsername: 'admin',
    capabilities: ['MCP_TOOLS'],
    rpmLimit: 120,
    status: 'ENABLED',
    remark: '演示：伙伴系统拉取站点与告警摘要',
    lastUsedAt: nowStr(),
    createdBy: 'demo',
    createTime: nowStr(),
  },
]

const seedUpstreams = (): McpUpstreamVO[] => [
  {
    id: 1,
    code: 'weather',
    name: '演示气象 MCP',
    protocol: 'STREAMABLE_HTTP',
    baseUrl: 'https://mcp.example.com',
    endpoint: '/mcp',
    hasAuthHeader: true,
    requestTimeoutMs: 20000,
    status: 'ENABLED',
    healthStatus: 'UP',
    healthMessage: '演示探测成功',
    lastProbeAt: nowStr(),
    remark: '演示上游，供智能中心 Agent 调用',
    toolCount: 3,
    createTime: nowStr(),
  },
  {
    id: 2,
    code: 'hydrology',
    name: '演示水情 MCP',
    protocol: 'SSE',
    baseUrl: 'https://hydro.example.com',
    endpoint: '/mcp',
    hasAuthHeader: false,
    requestTimeoutMs: 15000,
    status: 'ENABLED',
    healthStatus: 'UP',
    healthMessage: '演示探测成功',
    lastProbeAt: nowStr(),
    remark: '水位 / 雨量只读工具',
    toolCount: 2,
    createTime: nowStr(),
  },
]

const seedTools = (): Record<number, McpUpstreamToolVO[]> => ({
  1: [
    {
      originalName: 'get_weather',
      exposedName: 'weather.get_weather',
      description: '按城市查询当前天气',
      enabled: true,
    },
    {
      originalName: 'get_forecast',
      exposedName: 'weather.get_forecast',
      description: '查询未来 24 小时预报',
      enabled: true,
    },
    {
      originalName: 'list_alerts',
      exposedName: 'weather.list_alerts',
      description: '气象预警列表',
      enabled: false,
    },
  ],
  2: [
    {
      originalName: 'get_water_level',
      exposedName: 'hydro.get_water_level',
      description: '查询站点水位',
      enabled: true,
    },
    {
      originalName: 'get_rainfall',
      exposedName: 'hydro.get_rainfall',
      description: '查询站点雨量',
      enabled: true,
    },
  ],
})

const seedCalls = (): McpCallLogVO[] => [
  {
    id: 1,
    direction: 'IN',
    upstreamId: 1,
    agentId: 1,
    toolName: 'weather.get_weather',
    success: true,
    durationMs: 86,
    userId: '1',
    createTime: nowStr(),
  },
  {
    id: 2,
    direction: 'OUT',
    clientId: 1,
    toolName: 'zhishu.list_stations',
    success: true,
    durationMs: 42,
    userId: '1',
    createTime: nowStr(),
  },
  {
    id: 3,
    direction: 'IN',
    upstreamId: 2,
    agentId: 1,
    toolName: 'hydro.get_water_level',
    success: true,
    durationMs: 64,
    userId: '1',
    createTime: nowStr(),
  },
  {
    id: 4,
    direction: 'OUT',
    clientId: 2,
    toolName: 'zhishu.list_alerts',
    success: false,
    errorMessage: '演示：RPM 限流示例（未真实限流）',
    durationMs: 12,
    userId: '1',
    createTime: nowStr(),
  },
]

let clients = seedClients()
let upstreams = seedUpstreams()
let tools = seedTools()
let calls = seedCalls()
let nextClientId = 3
let nextUpstreamId = 3

function syncToolCount(id: number) {
  const upstream = upstreams.find((item) => item.id === id)
  if (upstream) upstream.toolCount = (tools[id] || []).length
}

export function resetMcpDemoState() {
  clients = seedClients()
  upstreams = seedUpstreams()
  tools = seedTools()
  calls = seedCalls()
  nextClientId = 3
  nextUpstreamId = 3
}

export function mockGetMcpOverview(): Promise<McpOverviewVO> {
  return Promise.resolve({
    serverEnabled: true,
    endpoint: '/mcp',
    clientCount: clients.length,
    upstreamCount: upstreams.length,
    enabledUpstreamCount: upstreams.filter((item) => item.status === 'ENABLED').length,
    cryptoConfigured: true,
  })
}

export function mockListMcpClients(): Promise<McpClientVO[]> {
  return Promise.resolve(clone(clients).map((item) => ({ ...item, apiKey: undefined })))
}

export function mockCreateMcpClient(data: McpClientCreateDTO): Promise<McpClientVO> {
  const apiKey = randomApiKey()
  const created: McpClientVO = {
    id: nextClientId++,
    name: data.name,
    keyPrefix: keyPrefix(apiKey),
    boundUserId: data.boundUserId,
    boundUsername: data.boundUsername,
    capabilities: data.capabilities || [],
    rpmLimit: data.rpmLimit ?? 60,
    status: 'ENABLED',
    remark: data.remark,
    createdBy: 'demo',
    createTime: nowStr(),
    apiKey,
  }
  clients.unshift(created)
  return Promise.resolve(clone(created))
}

export function mockUpdateMcpClient(id: number, data: McpClientUpdateDTO): Promise<McpClientVO> {
  const found = clients.find((item) => item.id === id)
  if (!found) return Promise.reject(new Error('Client 不存在'))
  Object.assign(found, {
    name: data.name,
    boundUserId: data.boundUserId,
    boundUsername: data.boundUsername,
    capabilities: data.capabilities,
    rpmLimit: data.rpmLimit,
    status: data.status,
    remark: data.remark,
  })
  return Promise.resolve(clone({ ...found, apiKey: undefined }))
}

export function mockRotateMcpClientKey(id: number): Promise<McpClientVO> {
  const found = clients.find((item) => item.id === id)
  if (!found) return Promise.reject(new Error('Client 不存在'))
  const apiKey = randomApiKey()
  found.keyPrefix = keyPrefix(apiKey)
  found.lastUsedAt = nowStr()
  return Promise.resolve(clone({ ...found, apiKey }))
}

export function mockDeleteMcpClient(id: number): Promise<void> {
  clients = clients.filter((item) => item.id !== id)
  return Promise.resolve()
}

export function mockListMcpUpstreams(): Promise<McpUpstreamVO[]> {
  return Promise.resolve(clone(upstreams))
}

export function mockCreateMcpUpstream(data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  const created: McpUpstreamVO = {
    id: nextUpstreamId++,
    code: data.code,
    name: data.name,
    protocol: data.protocol || 'STREAMABLE_HTTP',
    baseUrl: data.baseUrl,
    endpoint: data.endpoint || '/mcp',
    hasAuthHeader: Boolean(data.authHeader),
    requestTimeoutMs: data.requestTimeoutMs ?? 20000,
    status: data.status || 'ENABLED',
    healthStatus: 'UNKNOWN',
    healthMessage: '待探测',
    remark: data.remark,
    toolCount: 0,
    createTime: nowStr(),
  }
  upstreams.unshift(created)
  tools[created.id] = []
  return Promise.resolve(clone(created))
}

export function mockUpdateMcpUpstream(id: number, data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  const found = upstreams.find((item) => item.id === id)
  if (!found) return Promise.reject(new Error('上游不存在'))
  Object.assign(found, {
    code: data.code,
    name: data.name,
    protocol: data.protocol || found.protocol,
    baseUrl: data.baseUrl,
    endpoint: data.endpoint || found.endpoint,
    hasAuthHeader: data.authHeader != null ? Boolean(data.authHeader) : found.hasAuthHeader,
    requestTimeoutMs: data.requestTimeoutMs ?? found.requestTimeoutMs,
    status: data.status || found.status,
    remark: data.remark,
  })
  return Promise.resolve(clone(found))
}

export function mockDeleteMcpUpstream(id: number): Promise<void> {
  upstreams = upstreams.filter((item) => item.id !== id)
  delete tools[id]
  return Promise.resolve()
}

export function mockProbeMcpUpstream(id: number): Promise<McpUpstreamVO> {
  const found = upstreams.find((item) => item.id === id)
  if (!found) return Promise.reject(new Error('上游不存在'))
  found.healthStatus = 'UP'
  found.healthMessage = '演示探测成功'
  found.lastProbeAt = nowStr()
  if (!tools[id]?.length) {
    tools[id] = [
      {
        originalName: 'ping',
        exposedName: `${found.code}.ping`,
        description: '演示探测工具',
        enabled: true,
      },
    ]
    syncToolCount(id)
  }
  return Promise.resolve(clone(found))
}

export function mockListMcpUpstreamTools(id: number): Promise<McpUpstreamToolVO[]> {
  return Promise.resolve(clone(tools[id] || []))
}

export function mockPatchMcpUpstreamTool(
  id: number,
  data: { originalName: string; enabled: boolean },
): Promise<McpUpstreamToolVO> {
  const list = tools[id] || []
  const found = list.find((item) => item.originalName === data.originalName)
  if (!found) return Promise.reject(new Error('Tool 不存在'))
  found.enabled = data.enabled
  return Promise.resolve(clone(found))
}

export function mockListMcpCalls(direction?: string, limit = 50): Promise<McpCallLogVO[]> {
  const rows = calls.filter((item) => !direction || item.direction === direction).slice(0, limit)
  return Promise.resolve(clone(rows))
}
