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

const now = () => new Date().toISOString()

const demoSubgraph: KgSubgraphVO = {
  nodes: [
    { id: 'Project:1', label: 'Project', bizId: 1, name: '青溪防洪工程', projectId: 1, properties: {} },
    { id: 'Terminal:1001', label: 'Terminal', bizId: 1001, name: '青溪水文站', projectId: 1, properties: { status: 'ONLINE' } },
    { id: 'Alert:8001', label: 'Alert', bizId: 8001, name: '东区雨量超阈', projectId: 1, properties: { terminalId: 1003 } },
    { id: 'Region:east', label: 'Region', bizId: 'east', name: '东区', projectId: 1, properties: {} },
  ],
  edges: [
    { id: 'e1', type: 'HAS_TERMINAL', source: 'Project:1', target: 'Terminal:1001', projectId: 1 },
    { id: 'e2', type: 'HAS_ALERT', source: 'Terminal:1001', target: 'Alert:8001', projectId: 1 },
    { id: 'e3', type: 'IN_REGION', source: 'Terminal:1001', target: 'Region:east', projectId: 1 },
  ],
}

export async function mockKgHealth(): Promise<KgHealthVO> {
  return { connected: true, available: true, message: 'demo' }
}

export async function mockKgStats(): Promise<KgStatsVO> {
  return {
    connected: true,
    nodeCount: demoSubgraph.nodes.length,
    edgeCount: demoSubgraph.edges.length,
    nodesByLabel: { Project: 1, Terminal: 1, Alert: 1, Region: 1 },
    edgesByType: { HAS_TERMINAL: 1, HAS_ALERT: 1, IN_REGION: 1 },
    message: 'demo',
  }
}

export async function mockKgSubgraph(_params: {
  projectId?: number
  depth?: number
  types?: string
}): Promise<KgSubgraphVO> {
  return structuredClone(demoSubgraph)
}

export async function mockKgSearch(q: string, limit = 20): Promise<KgSearchHit[]> {
  const kw = q.trim().toLowerCase()
  return demoSubgraph.nodes
    .filter((n) => (n.name || '').toLowerCase().includes(kw) || String(n.bizId).includes(kw))
    .slice(0, limit)
    .map((n) => ({
      label: n.label,
      bizId: n.bizId ?? n.id,
      name: n.name,
      projectId: n.projectId,
      code: String(n.bizId ?? ''),
    }))
}

export async function mockKgNeighbors(
  label: string,
  bizId: number | string,
  _depth = 1,
): Promise<KgNeighborResult> {
  const node = demoSubgraph.nodes.find((n) => n.label === label && String(n.bizId) === String(bizId))
  if (!node) {
    return { found: false, message: '演示图谱中未找到该节点', subgraph: { nodes: [], edges: [] } }
  }
  return { found: true, message: 'ok', subgraph: structuredClone(demoSubgraph) }
}

export async function mockKgPath(_params: {
  fromLabel: string
  fromBizId: number | string
  toLabel: string
  toBizId: number | string
}): Promise<KgPathResult> {
  return {
    found: true,
    message: 'ok',
    nodes: structuredClone(demoSubgraph.nodes),
    edges: structuredClone(demoSubgraph.edges),
  }
}

export async function mockKgTopology(projectId: number | string): Promise<KgTopologySummary> {
  return {
    found: true,
    message: 'ok',
    projectId,
    projectName: '青溪防洪工程',
    terminalCount: 6,
    openAlertCount: 2,
    planCount: 1,
    taskCount: 3,
    openIssueCount: 1,
  }
}

export async function mockKgAlertImpact(_bizId: number | string, _depth = 2): Promise<KgNeighborResult> {
  return { found: true, message: '演示影响面', subgraph: structuredClone(demoSubgraph) }
}

export async function mockKgSync(full = false): Promise<KgSyncResult> {
  return {
    success: true,
    full,
    message: '演示模式未连接 Neo4j',
    startedAt: now(),
    finishedAt: now(),
    upserted: { Project: 1, Terminal: 1, Alert: 1 },
    deleted: 0,
  }
}

export async function mockKgSyncStatus(): Promise<KgSyncStatusVO> {
  return {
    enabled: false,
    neo4jConnected: false,
    lastSuccessAt: now(),
    lastMessage: '演示模式',
    watermarks: [],
  }
}
