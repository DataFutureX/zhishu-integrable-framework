/** 组件健康状态 */
export type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | string

export interface ComponentHealthDTO {
  name: string
  status: HealthStatus
  message?: string
  responseTimeMs?: number
}

export interface ApplicationMetricsDTO {
  name?: string
  version?: string
  javaVersion?: string
  springBootVersion?: string
  profile?: string
  uptimeMillis?: number
  startTime?: string
}

export interface JvmMetricsDTO {
  heapUsedMb?: number
  heapMaxMb?: number
  heapCommittedMb?: number
  heapUsagePercent?: number
  nonHeapUsedMb?: number
  nonHeapCommittedMb?: number
  activeThreads?: number
  peakThreads?: number
  daemonThreads?: number
  totalStartedThreads?: number
  gcCount?: number
  gcTimeMs?: number
}

export interface OsMetricsDTO {
  osName?: string
  osArch?: string
  osVersion?: string
  availableProcessors?: number
  systemCpuUsagePercent?: number
  processCpuUsagePercent?: number
  systemMemoryTotalMb?: number
  systemMemoryFreeMb?: number
  systemMemoryUsagePercent?: number
}

export interface DatabaseMetricsDTO {
  status?: HealthStatus
  databaseProduct?: string
  databaseVersion?: string
  poolName?: string
  activeConnections?: number
  idleConnections?: number
  totalConnections?: number
  maxConnections?: number
  threadsAwaitingConnection?: number
  connectionTimeoutMs?: number
  validationTimeMs?: number
}

export interface WebServerMetricsDTO {
  status?: HealthStatus
  port?: number
  servletContainer?: string
}

export interface BusinessMetricsDTO {
  userTotal?: number
}

export interface StorageMetricsDTO {
  diskTotalMb?: number
  diskFreeMb?: number
  diskUsagePercent?: number
  logFileSizeMb?: number
  uploadDirSizeMb?: number
}

export interface ShardingTableMetricsDTO {
  tableName?: string
  month?: string
  exists?: boolean
  approximateRows?: number
  dataLengthBytes?: number
  createTime?: string
}

export interface ShardingStrategyMetricsDTO {
  name?: string
  displayName?: string
  tablePrefix?: string
  status?: HealthStatus
  autoCreate?: boolean
  monthsBehind?: number
  monthsAhead?: number
  existingTableCount?: number
  expectedTableCount?: number
  missingTableCount?: number
  approximateRowTotal?: number
  dataLengthBytes?: number
  missingMonths?: string[]
  tables?: ShardingTableMetricsDTO[]
}

export interface ShardingMetricsDTO {
  status?: HealthStatus
  enabled?: boolean
  strategyCount?: number
  existingTableCount?: number
  expectedTableCount?: number
  missingTableCount?: number
  approximateRowTotal?: number
  strategies?: ShardingStrategyMetricsDTO[]
}

export interface SystemStatusDTO {
  status?: HealthStatus
  timestamp?: string
  application?: ApplicationMetricsDTO
  jvm?: JvmMetricsDTO
  os?: OsMetricsDTO
  database?: DatabaseMetricsDTO
  webServer?: WebServerMetricsDTO
  business?: BusinessMetricsDTO
  sharding?: ShardingMetricsDTO
  storage?: StorageMetricsDTO
  components?: ComponentHealthDTO[]
}

export interface SystemHealthDTO {
  status?: HealthStatus
  timestamp?: string
  components?: ComponentHealthDTO[]
}
