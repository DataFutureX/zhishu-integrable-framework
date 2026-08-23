export interface KgHealthVO {
  connected: boolean
  available?: boolean
  message?: string
}

export interface KgNodeVO {
  id: string
  label: string
  bizId?: string | number | null
  name?: string | null
  projectId?: string | number | null
  properties?: Record<string, unknown> | null
}

export interface KgEdgeVO {
  id?: string
  type?: string
  source: string
  target: string
  projectId?: string | number | null
}

export interface KgSubgraphVO {
  nodes: KgNodeVO[]
  edges: KgEdgeVO[]
}

export interface KgStatsVO {
  connected: boolean
  nodeCount: number
  edgeCount: number
  nodesByLabel?: Record<string, number>
  edgesByType?: Record<string, number>
  message?: string | null
}

export interface KgSearchHit {
  label: string
  bizId: string | number
  name?: string | null
  projectId?: string | number | null
  code?: string | null
}

export interface KgNeighborResult {
  found: boolean
  message?: string | null
  subgraph: KgSubgraphVO
}

export interface KgPathResult {
  found: boolean
  message?: string | null
  nodes: KgNodeVO[]
  edges: KgEdgeVO[]
}

export interface KgTopologySummary {
  found: boolean
  message?: string | null
  projectId: string | number
  projectName?: string | null
  terminalCount?: number
  openAlertCount?: number
  planCount?: number
  taskCount?: number
  openIssueCount?: number
}

export interface KgSyncResult {
  success: boolean
  full?: boolean
  message?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  upserted?: Record<string, number>
  deleted?: number
}

export interface KgSyncWatermark {
  sourceTable: string
  lastSyncAt?: string | null
  maxSourceTime?: string | null
  lastStatus?: string | null
  lastMessage?: string | null
}

export interface KgSyncStatusVO {
  enabled: boolean
  neo4jConnected?: boolean
  lastSuccessAt?: string | null
  lastMessage?: string | null
  watermarks?: KgSyncWatermark[]
}

export interface KgPathEndpoint {
  label: string
  bizId: string | number
  name?: string | null
}
