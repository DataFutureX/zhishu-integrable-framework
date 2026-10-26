import { aiService } from '@/api/aiClient'
import type {
  KgHealthVO,
  KgNeighborResult,
  KgPathResult,
  KgSearchHit,
  KgStatsVO,
  KgSubgraphVO,
  KgSyncResult,
  KgSyncStatusVO,
  KgTopologySummary,
} from '@/types/knowledgeGraph'

/** 知识图谱（Neo4j）相关接口 */
export function getKgHealth(): Promise<KgHealthVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgHealth())
  }
  return aiService.get('/kg/health')
}

export function getKgStats(): Promise<KgStatsVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgStats())
  }
  return aiService.get('/kg/stats')
}

export function getKgSubgraph(params: {
  projectId?: number
  depth?: number
  types?: string
}): Promise<KgSubgraphVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgSubgraph(params))
  }
  return aiService.get('/kg/subgraph', { params })
}

export function searchKgEntities(q: string, limit = 20): Promise<KgSearchHit[]> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgSearch(q, limit))
  }
  return aiService.get('/kg/search', { params: { q, limit } })
}

export function getKgNeighbors(
  label: string,
  bizId: number | string,
  depth = 1,
): Promise<KgNeighborResult> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgNeighbors(label, bizId, depth))
  }
  return aiService.get(`/kg/neighbors/${encodeURIComponent(label)}/${bizId}`, {
    params: { depth },
  })
}

export function getKgPath(params: {
  fromLabel: string
  fromBizId: number | string
  toLabel: string
  toBizId: number | string
}): Promise<KgPathResult> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgPath(params))
  }
  return aiService.get('/kg/path', { params })
}

export function getKgTopology(projectId: number | string): Promise<KgTopologySummary> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgTopology(projectId))
  }
  return aiService.get(`/kg/topology/${projectId}`)
}

export function getKgAlertImpact(bizId: number | string, depth = 2): Promise<KgNeighborResult> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgAlertImpact(bizId, depth))
  }
  return aiService.get(`/kg/impact/alert/${bizId}`, { params: { depth } })
}

export function triggerKgSync(full = false): Promise<KgSyncResult> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgSync(full))
  }
  return aiService.post('/kg/sync', null, { params: { full } })
}

export function getKgSyncStatus(): Promise<KgSyncStatusVO> {
  if (__DEMO_MODE__) {
    return import('@/mock/kg').then((m) => m.mockKgSyncStatus())
  }
  return aiService.get('/kg/sync/status')
}
