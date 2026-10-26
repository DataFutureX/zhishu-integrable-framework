import { aiService } from '@/api/aiClient'
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

/** MCP Hub：上游接入、对外 Client 与调用日志 */
export function getMcpOverview(): Promise<McpOverviewVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockGetMcpOverview())
  }
  return aiService.get('/mcp/overview')
}

export function listMcpClients(): Promise<McpClientVO[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockListMcpClients())
  }
  return aiService.get('/mcp/clients')
}

export function createMcpClient(data: McpClientCreateDTO): Promise<McpClientVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockCreateMcpClient(data))
  }
  return aiService.post('/mcp/clients', data)
}

export function updateMcpClient(id: number, data: McpClientUpdateDTO): Promise<McpClientVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockUpdateMcpClient(id, data))
  }
  return aiService.put(`/mcp/clients/${id}`, data)
}

export function rotateMcpClientKey(id: number): Promise<McpClientVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockRotateMcpClientKey(id))
  }
  return aiService.post(`/mcp/clients/${id}/rotate-key`)
}

export function deleteMcpClient(id: number): Promise<void> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockDeleteMcpClient(id))
  }
  return aiService.delete(`/mcp/clients/${id}`)
}

export function listMcpUpstreams(): Promise<McpUpstreamVO[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockListMcpUpstreams())
  }
  return aiService.get('/mcp/upstreams')
}

export function createMcpUpstream(data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockCreateMcpUpstream(data))
  }
  return aiService.post('/mcp/upstreams', data)
}

export function updateMcpUpstream(id: number, data: McpUpstreamUpsertDTO): Promise<McpUpstreamVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockUpdateMcpUpstream(id, data))
  }
  return aiService.put(`/mcp/upstreams/${id}`, data)
}

export function deleteMcpUpstream(id: number): Promise<void> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockDeleteMcpUpstream(id))
  }
  return aiService.delete(`/mcp/upstreams/${id}`)
}

export function probeMcpUpstream(id: number): Promise<McpUpstreamVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockProbeMcpUpstream(id))
  }
  return aiService.post(`/mcp/upstreams/${id}/probe`)
}

export function listMcpUpstreamTools(id: number): Promise<McpUpstreamToolVO[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockListMcpUpstreamTools(id))
  }
  return aiService.get(`/mcp/upstreams/${id}/tools`)
}

export function patchMcpUpstreamTool(
  id: number,
  data: { originalName: string; enabled: boolean },
): Promise<McpUpstreamToolVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockPatchMcpUpstreamTool(id, data))
  }
  return aiService.put(`/mcp/upstreams/${id}/tools`, data)
}

export function listMcpCalls(direction?: string, limit = 50): Promise<McpCallLogVO[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/mcp').then((m) => m.mockListMcpCalls(direction, limit))
  }
  return aiService.get('/mcp/calls', { params: { direction, limit } })
}
