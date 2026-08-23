<template>
  <div v-loading="loading" class="monitor">
    <!-- 总览 -->
    <section class="monitor-hero">
      <div class="monitor-hero__main">
        <div class="monitor-hero__status">
          <span
            class="monitor-pulse"
            :class="`monitor-pulse--${healthTone(overallStatus)}`"
          />
          <div>
            <div class="monitor-hero__title-row">
              <h1 class="monitor-hero__title">运维监控</h1>
              <el-tag :type="healthTagType(overallStatus)" effect="plain" size="small">
                {{ healthLabel(overallStatus) }}
              </el-tag>
            </div>
            <p class="monitor-hero__meta">
              <span>{{ appBrief }}</span>
              <span v-if="statusData?.timestamp">
                采集于 {{ formatDateTime(statusData.timestamp) }}
              </span>
            </p>
          </div>
        </div>
      </div>
      <div class="monitor-hero__actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自动"
          inactive-text="手动"
          inline-prompt
        />
        <el-button :icon="Refresh" :loading="loading" type="primary" @click="loadMonitorData">
          刷新
        </el-button>
      </div>
    </section>

    <!-- 关键指标 -->
    <section class="monitor-kpis">
      <div v-for="kpi in kpiCards" :key="kpi.key" class="monitor-kpi">
        <span class="monitor-kpi__label">{{ kpi.label }}</span>
        <span class="monitor-kpi__value">{{ kpi.value }}</span>
        <div v-if="kpi.percent != null" class="monitor-kpi__bar">
          <div
            class="monitor-kpi__fill"
            :class="meterToneClass(kpi.percent)"
            :style="{ width: `${clampPercent(kpi.percent)}%` }"
          />
        </div>
        <span v-if="kpi.sub" class="monitor-kpi__sub">{{ kpi.sub }}</span>
      </div>
    </section>

    <!-- 组件健康 -->
    <section v-if="healthComponents.length" class="monitor-panel">
      <header class="monitor-panel__head">
        <h2 class="monitor-panel__title">组件健康</h2>
        <span class="monitor-panel__hint">/system/health</span>
      </header>
      <div class="monitor-health-grid">
        <div
          v-for="item in healthComponents"
          :key="item.name"
          class="health-chip"
          :class="`health-chip--${healthTone(item.status)}`"
        >
          <div class="health-chip__top">
            <span class="health-chip__name">{{ componentDisplayName(item.name) }}</span>
            <el-tag :type="healthTagType(item.status)" size="small" effect="plain">
              {{ item.status || '-' }}
            </el-tag>
          </div>
          <p v-if="item.message" class="health-chip__msg">{{ item.message }}</p>
          <p v-if="item.responseTimeMs != null" class="health-chip__meta">
            {{ item.responseTimeMs }} ms
          </p>
        </div>
      </div>
    </section>

    <!-- JVM + OS -->
    <el-row :gutter="12" class="monitor-row">
      <el-col :xs="24" :lg="12">
        <section class="monitor-panel">
          <header class="monitor-panel__head">
            <h2 class="monitor-panel__title">JVM</h2>
          </header>
          <div class="meter-block">
            <div class="meter-block__head">
              <span>堆内存</span>
              <span>{{ formatPercent(statusData?.jvm?.heapUsagePercent) }}</span>
            </div>
            <div class="meter-block__track">
              <div
                class="meter-block__fill"
                :class="meterToneClass(statusData?.jvm?.heapUsagePercent)"
                :style="{ width: `${clampPercent(statusData?.jvm?.heapUsagePercent)}%` }"
              />
            </div>
            <p class="meter-block__sub">
              {{ formatMb(statusData?.jvm?.heapUsedMb) }} /
              {{ formatMb(statusData?.jvm?.heapMaxMb) }}
              · 已提交 {{ formatMb(statusData?.jvm?.heapCommittedMb) }}
            </p>
          </div>
          <div class="kv-grid">
            <div class="kv"><span>非堆已用</span><b>{{ formatMb(statusData?.jvm?.nonHeapUsedMb) }}</b></div>
            <div class="kv"><span>活跃线程</span><b>{{ statusData?.jvm?.activeThreads ?? '-' }}</b></div>
            <div class="kv"><span>峰值线程</span><b>{{ statusData?.jvm?.peakThreads ?? '-' }}</b></div>
            <div class="kv"><span>守护线程</span><b>{{ statusData?.jvm?.daemonThreads ?? '-' }}</b></div>
            <div class="kv"><span>累计启动</span><b>{{ statusData?.jvm?.totalStartedThreads ?? '-' }}</b></div>
            <div class="kv"><span>GC 次数</span><b>{{ statusData?.jvm?.gcCount ?? '-' }}</b></div>
            <div class="kv"><span>GC 耗时</span><b>{{ formatMs(statusData?.jvm?.gcTimeMs) }}</b></div>
            <div class="kv"><span>非堆提交</span><b>{{ formatMb(statusData?.jvm?.nonHeapCommittedMb) }}</b></div>
          </div>
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="monitor-panel">
          <header class="monitor-panel__head">
            <h2 class="monitor-panel__title">操作系统</h2>
          </header>
          <p class="os-summary">{{ osSummary }}</p>
          <div class="meter-block">
            <div class="meter-block__head">
              <span>系统内存</span>
              <span>{{ formatPercent(statusData?.os?.systemMemoryUsagePercent) }}</span>
            </div>
            <div class="meter-block__track">
              <div
                class="meter-block__fill"
                :class="meterToneClass(statusData?.os?.systemMemoryUsagePercent)"
                :style="{ width: `${clampPercent(statusData?.os?.systemMemoryUsagePercent)}%` }"
              />
            </div>
            <p class="meter-block__sub">
              可用 {{ formatMb(statusData?.os?.systemMemoryFreeMb) }} /
              总计 {{ formatMb(statusData?.os?.systemMemoryTotalMb) }}
            </p>
          </div>
          <div class="kv-grid">
            <div class="kv">
              <span>CPU 核心</span>
              <b>{{ statusData?.os?.availableProcessors ?? '-' }}</b>
            </div>
            <div class="kv">
              <span>系统 CPU</span>
              <b>{{ formatPercent(statusData?.os?.systemCpuUsagePercent) }}</b>
            </div>
            <div class="kv">
              <span>进程 CPU</span>
              <b>{{ formatPercent(statusData?.os?.processCpuUsagePercent) }}</b>
            </div>
            <div class="kv">
              <span>运行时长</span>
              <b>{{ formatUptime(statusData?.application?.uptimeMillis) }}</b>
            </div>
          </div>
        </section>
      </el-col>
    </el-row>

    <!-- 数据库 + Web/存储/应用 -->
    <el-row :gutter="12" class="monitor-row">
      <el-col :xs="24" :lg="12">
        <section class="monitor-panel">
          <header class="monitor-panel__head">
            <div class="monitor-panel__title-group">
              <h2 class="monitor-panel__title">数据库连接池</h2>
              <el-tag :type="healthTagType(statusData?.database?.status)" size="small" effect="plain">
                {{ statusData?.database?.status || '-' }}
              </el-tag>
            </div>
          </header>
          <p class="os-summary">{{ databaseSummary }}</p>
          <div class="meter-block">
            <div class="meter-block__head">
              <span>连接占用</span>
              <span>{{ connectionUsageLabel }}</span>
            </div>
            <div class="meter-block__track">
              <div
                class="meter-block__fill"
                :class="meterToneClass(connectionUsagePercent)"
                :style="{ width: `${clampPercent(connectionUsagePercent)}%` }"
              />
            </div>
            <p class="meter-block__sub">
              活跃 {{ statusData?.database?.activeConnections ?? '-' }}
              · 空闲 {{ statusData?.database?.idleConnections ?? '-' }}
              · 最大 {{ statusData?.database?.maxConnections ?? '-' }}
            </p>
          </div>
          <div class="kv-grid">
            <div class="kv"><span>连接池</span><b>{{ statusData?.database?.poolName || '-' }}</b></div>
            <div class="kv"><span>当前连接</span><b>{{ statusData?.database?.totalConnections ?? '-' }}</b></div>
            <div class="kv"><span>等待线程</span><b>{{ statusData?.database?.threadsAwaitingConnection ?? '-' }}</b></div>
            <div class="kv"><span>连接超时</span><b>{{ formatMs(statusData?.database?.connectionTimeoutMs) }}</b></div>
            <div class="kv"><span>校验耗时</span><b>{{ formatMs(statusData?.database?.validationTimeMs) }}</b></div>
            <div class="kv"><span>用户总数</span><b>{{ statusData?.business?.userTotal ?? '-' }}</b></div>
          </div>
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="monitor-panel">
          <header class="monitor-panel__head">
            <div class="monitor-panel__title-group">
              <h2 class="monitor-panel__title">Web 与存储</h2>
              <el-tag :type="healthTagType(statusData?.webServer?.status)" size="small" effect="plain">
                {{ statusData?.webServer?.status || '-' }}
              </el-tag>
            </div>
          </header>
          <div class="kv-grid kv-grid--dense">
            <div class="kv"><span>监听端口</span><b>{{ statusData?.webServer?.port ?? '-' }}</b></div>
            <div class="kv"><span>容器</span><b>{{ statusData?.webServer?.servletContainer || '-' }}</b></div>
            <div class="kv"><span>应用名</span><b>{{ statusData?.application?.name || '-' }}</b></div>
            <div class="kv"><span>版本</span><b>{{ statusData?.application?.version || '-' }}</b></div>
            <div class="kv"><span>Java</span><b>{{ statusData?.application?.javaVersion || '-' }}</b></div>
            <div class="kv"><span>Spring Boot</span><b>{{ statusData?.application?.springBootVersion || '-' }}</b></div>
            <div class="kv"><span>Profile</span><b>{{ statusData?.application?.profile || '-' }}</b></div>
            <div class="kv">
              <span>启动时间</span>
              <b>
                {{
                  statusData?.application?.startTime
                    ? formatDateTime(statusData.application.startTime)
                    : '-'
                }}
              </b>
            </div>
          </div>
          <div class="meter-block meter-block--spaced">
            <div class="meter-block__head">
              <span>磁盘使用</span>
              <span>{{ formatPercent(statusData?.storage?.diskUsagePercent) }}</span>
            </div>
            <div class="meter-block__track">
              <div
                class="meter-block__fill"
                :class="meterToneClass(statusData?.storage?.diskUsagePercent)"
                :style="{ width: `${clampPercent(statusData?.storage?.diskUsagePercent)}%` }"
              />
            </div>
            <p class="meter-block__sub">
              可用 {{ formatMb(statusData?.storage?.diskFreeMb) }} /
              总计 {{ formatMb(statusData?.storage?.diskTotalMb) }}
              · 日志 {{ formatMb(statusData?.storage?.logFileSizeMb) }}
              · 上传 {{ formatMb(statusData?.storage?.uploadDirSizeMb) }}
            </p>
          </div>
        </section>
      </el-col>
    </el-row>

    <!-- 分表 -->
    <section v-if="statusData?.sharding" class="monitor-panel">
      <header class="monitor-panel__head">
        <div class="monitor-panel__title-group">
          <h2 class="monitor-panel__title">分表策略</h2>
          <el-tag :type="healthTagType(statusData.sharding.status)" size="small" effect="plain">
            {{ statusData.sharding.enabled ? '已启用' : '未启用' }}
            · {{ statusData.sharding.status || '-' }}
          </el-tag>
        </div>
        <span class="monitor-panel__hint">
          策略 {{ statusData.sharding.strategyCount ?? 0 }}
          · 表 {{ statusData.sharding.existingTableCount ?? 0 }}/{{
            statusData.sharding.expectedTableCount ?? 0
          }}
          · 缺表 {{ statusData.sharding.missingTableCount ?? 0 }}
        </span>
      </header>

      <div v-if="shardingStrategies.length" class="sharding-list">
        <div
          v-for="strategy in shardingStrategies"
          :key="strategy.name || strategy.tablePrefix"
          class="sharding-card"
        >
          <div class="sharding-card__head">
            <div class="sharding-card__title">
              <strong>{{ strategy.displayName || strategy.name || '-' }}</strong>
              <el-tag :type="healthTagType(strategy.status)" size="small" effect="plain">
                {{ strategy.status || '-' }}
              </el-tag>
              <el-tag v-if="strategy.autoCreate" size="small" type="success" effect="plain">
                自动建表
              </el-tag>
            </div>
            <span class="sharding-card__prefix">{{ strategy.tablePrefix || '-' }}</span>
          </div>
          <div class="kv-grid kv-grid--dense">
            <div class="kv">
              <span>已有 / 期望</span>
              <b>{{ strategy.existingTableCount ?? 0 }} / {{ strategy.expectedTableCount ?? 0 }}</b>
            </div>
            <div class="kv"><span>缺表</span><b>{{ strategy.missingTableCount ?? 0 }}</b></div>
            <div class="kv"><span>约行数</span><b>{{ formatCount(strategy.approximateRowTotal) }}</b></div>
            <div class="kv"><span>数据量</span><b>{{ formatBytes(strategy.dataLengthBytes) }}</b></div>
            <div class="kv"><span>回溯月</span><b>{{ strategy.monthsBehind ?? '-' }}</b></div>
            <div class="kv"><span>前瞻月</span><b>{{ strategy.monthsAhead ?? '-' }}</b></div>
          </div>
          <p v-if="strategy.missingMonths?.length" class="sharding-card__missing">
            缺失月份：{{ strategy.missingMonths.join('、') }}
          </p>
          <el-table
            v-if="strategy.tables?.length"
            :data="strategy.tables"
            size="small"
            stripe
            class="sharding-table"
            max-height="240"
          >
            <el-table-column prop="tableName" label="表名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="month" label="月份" width="100" />
            <el-table-column label="存在" width="80">
              <template #default="{ row }">
                <el-tag :type="row.exists ? 'success' : 'danger'" size="small" effect="plain">
                  {{ row.exists ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="约行数" width="110">
              <template #default="{ row }">
                {{ formatCount(row.approximateRows) }}
              </template>
            </el-table-column>
            <el-table-column label="数据量" width="110">
              <template #default="{ row }">
                {{ formatBytes(row.dataLengthBytes) }}
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="160">
              <template #default="{ row }">
                {{ row.createTime ? formatDateTime(row.createTime) : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <el-empty v-else description="暂无分表策略" :image-size="56" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSystemStatusApi, getSystemHealthApi } from '@/api/systemMonitor'
import type {
  ComponentHealthDTO,
  HealthStatus,
  ShardingStrategyMetricsDTO,
  SystemStatusDTO,
} from '@/types/systemMonitor'
import { formatDateTime } from '@/utils/format'

const REFRESH_INTERVAL_MS = 30_000

const loading = ref(false)
const autoRefresh = ref(true)
const statusData = ref<SystemStatusDTO | null>(null)
const healthComponents = ref<ComponentHealthDTO[]>([])

let refreshTimer: ReturnType<typeof setInterval> | null = null

const overallStatus = computed(() => statusData.value?.status || 'UNKNOWN')

const appBrief = computed(() => {
  const app = statusData.value?.application
  if (!app?.name) return '对接 /system/status · /system/health'
  return [
    app.name,
    app.version ? `v${String(app.version).replace(/^v/i, '')}` : '',
    app.profile,
    formatUptime(app.uptimeMillis),
  ]
    .filter(Boolean)
    .join(' · ')
})

const osSummary = computed(() => {
  const os = statusData.value?.os
  if (!os?.osName) return '-'
  return [os.osName, os.osVersion, os.osArch].filter(Boolean).join(' / ')
})

const databaseSummary = computed(() => {
  const db = statusData.value?.database
  if (!db?.databaseProduct) return '-'
  return [db.databaseProduct, db.databaseVersion].filter(Boolean).join(' ')
})

const connectionUsagePercent = computed(() => {
  const db = statusData.value?.database
  if (db?.activeConnections == null || db?.maxConnections == null || db.maxConnections <= 0) {
    return null
  }
  return (db.activeConnections / db.maxConnections) * 100
})

const connectionUsageLabel = computed(() => {
  const db = statusData.value?.database
  if (db?.activeConnections == null || db?.maxConnections == null) return '-'
  return `${db.activeConnections} / ${db.maxConnections}`
})

const shardingStrategies = computed<ShardingStrategyMetricsDTO[]>(
  () => statusData.value?.sharding?.strategies ?? [],
)

const kpiCards = computed(() => {
  const s = statusData.value
  return [
    {
      key: 'heap',
      label: '堆内存',
      value: formatPercent(s?.jvm?.heapUsagePercent),
      percent: s?.jvm?.heapUsagePercent ?? null,
      sub: `${formatMb(s?.jvm?.heapUsedMb)} / ${formatMb(s?.jvm?.heapMaxMb)}`,
    },
    {
      key: 'cpu',
      label: '系统 CPU',
      value: formatPercent(s?.os?.systemCpuUsagePercent),
      percent: s?.os?.systemCpuUsagePercent ?? null,
      sub: `进程 ${formatPercent(s?.os?.processCpuUsagePercent)}`,
    },
    {
      key: 'mem',
      label: '系统内存',
      value: formatPercent(s?.os?.systemMemoryUsagePercent),
      percent: s?.os?.systemMemoryUsagePercent ?? null,
      sub: `可用 ${formatMb(s?.os?.systemMemoryFreeMb)}`,
    },
    {
      key: 'disk',
      label: '磁盘',
      value: formatPercent(s?.storage?.diskUsagePercent),
      percent: s?.storage?.diskUsagePercent ?? null,
      sub: `可用 ${formatMb(s?.storage?.diskFreeMb)}`,
    },
    {
      key: 'db',
      label: '数据库连接',
      value: connectionUsageLabel.value,
      percent: connectionUsagePercent.value,
      sub: `空闲 ${s?.database?.idleConnections ?? '-'}`,
    },
    {
      key: 'users',
      label: '业务用户',
      value: String(s?.business?.userTotal ?? '-'),
      percent: null,
      sub: `线程 ${s?.jvm?.activeThreads ?? '-'}`,
    },
  ]
})

const componentDisplayName = (name?: string) => {
  if (!name) return '-'
  const nameMap: Record<string, string> = {
    database: '数据库',
    webServer: 'Web 服务',
    storage: '存储',
    jvm: 'JVM',
    sharding: '分表',
    application: '应用',
  }
  return nameMap[name] || name
}

const healthTagType = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return 'success'
  if (normalized === 'DEGRADED') return 'warning'
  if (normalized === 'DOWN') return 'danger'
  return 'info'
}

const healthLabel = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return '运行正常'
  if (normalized === 'DEGRADED') return '性能降级'
  if (normalized === 'DOWN') return '服务异常'
  return '状态未知'
}

const healthTone = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return 'ok'
  if (normalized === 'DEGRADED') return 'warn'
  if (normalized === 'DOWN') return 'bad'
  return 'muted'
}

const clampPercent = (value?: number | null) => {
  if (value == null || Number.isNaN(value)) return 0
  return Math.min(100, Math.max(0, Math.round(value)))
}

const meterToneClass = (value?: number | null) => {
  if (value == null) return 'is-muted'
  if (value >= 90) return 'is-danger'
  if (value >= 75) return 'is-warn'
  return 'is-ok'
}

const formatPercent = (value?: number | null) => {
  if (value == null || Number.isNaN(value)) return '-'
  return `${Number(value).toFixed(1)}%`
}

const formatMb = (value?: number | null) => {
  if (value == null || Number.isNaN(value)) return '-'
  if (value >= 1024) return `${(value / 1024).toFixed(1)} GB`
  return `${Number(value).toFixed(1)} MB`
}

const formatMs = (value?: number | null) => {
  if (value == null) return '-'
  return `${value} ms`
}

const formatCount = (value?: number | null) => {
  if (value == null) return '-'
  return Number(value).toLocaleString()
}

const formatBytes = (value?: number | null) => {
  if (value == null) return '-'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

const formatUptime = (millis?: number) => {
  if (millis == null || millis < 0) return '-'
  const totalSeconds = Math.floor(millis / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  return `${minutes} 分钟`
}

const loadMonitorData = async () => {
  loading.value = true
  try {
    const [status, health] = await Promise.all([getSystemStatusApi(), getSystemHealthApi()])
    statusData.value = status
    healthComponents.value = health.components?.length
      ? health.components
      : status.components || []
  } catch {
    ElMessage.error('加载运维监控数据失败')
  } finally {
    loading.value = false
  }
}

const clearRefreshTimer = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const setupRefreshTimer = () => {
  clearRefreshTimer()
  if (autoRefresh.value) {
    refreshTimer = setInterval(() => {
      loadMonitorData()
    }, REFRESH_INTERVAL_MS)
  }
}

watch(autoRefresh, setupRefreshTimer)

onMounted(() => {
  loadMonitorData()
  setupRefreshTimer()
})

onUnmounted(() => {
  clearRefreshTimer()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.monitor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;

  :deep(.el-row) {
    margin-bottom: 0 !important;
  }
}

.monitor-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 18px;
  border-radius: var(--app-radius-lg, 12px);
  border: 1px solid color-mix(in srgb, var(--app-primary) 12%, var(--app-border-color));
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--app-primary) 12%, transparent), transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);

  &__status {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__title-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__title {
    margin: 0;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 20px;
    font-weight: 650;
    color: $text-primary;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    margin: 6px 0 0;
    font-size: 12px;
    color: $text-secondary;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.monitor-pulse {
  width: 10px;
  height: 10px;
  margin-top: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &--ok {
    background: #67c23a;
    box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.16);
  }

  &--warn {
    background: #e6a23c;
    box-shadow: 0 0 0 4px rgba(230, 162, 60, 0.16);
  }

  &--bad {
    background: #f56c6c;
    box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.16);
  }

  &--muted {
    background: #c0c4cc;
  }
}

.monitor-kpis {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.monitor-kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--app-border-color);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);

  &__label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__value {
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 20px;
    font-weight: 700;
    line-height: 1.15;
    color: $text-primary;
  }

  &__bar {
    height: 4px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--app-border-color) 80%, #eef2f6);
    overflow: hidden;
  }

  &__fill {
    height: 100%;
    border-radius: inherit;

    &.is-ok {
      background: var(--app-primary);
    }

    &.is-warn {
      background: #e6a23c;
    }

    &.is-danger {
      background: #f56c6c;
    }

    &.is-muted {
      background: #c0c4cc;
      width: 0 !important;
    }
  }

  &__sub {
    font-size: 11px;
    color: $text-secondary;
  }
}

.monitor-row {
  width: 100%;
}

.monitor-panel {
  height: 100%;
  margin-bottom: 12px;
  padding: 14px 16px 16px;
  border: 1px solid var(--app-border-color);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    min-height: 24px;
  }

  &__title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
    color: $text-primary;
  }

  &__hint {
    font-size: 12px;
    color: $text-secondary;
  }
}

.monitor-health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.health-chip {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);

  &--ok {
    border-color: color-mix(in srgb, #67c23a 35%, var(--app-border-color));
    background: color-mix(in srgb, #67c23a 6%, #fff);
  }

  &--warn {
    border-color: color-mix(in srgb, #e6a23c 35%, var(--app-border-color));
    background: color-mix(in srgb, #e6a23c 6%, #fff);
  }

  &--bad {
    border-color: color-mix(in srgb, #f56c6c 35%, var(--app-border-color));
    background: color-mix(in srgb, #f56c6c 6%, #fff);
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__name {
    font-size: 13px;
    font-weight: 650;
    color: $text-primary;
  }

  &__msg,
  &__meta {
    margin: 6px 0 0;
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.4;
  }
}

.os-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: $text-regular;
}

.meter-block {
  margin-bottom: 14px;

  &--spaced {
    margin-top: 14px;
    margin-bottom: 0;
  }

  &__head {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 6px;
    font-size: 12px;
    color: $text-secondary;
  }

  &__track {
    height: 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--app-border-color) 80%, #eef2f6);
    overflow: hidden;
  }

  &__fill {
    height: 100%;
    border-radius: inherit;
    transition: width 0.35s ease;

    &.is-ok {
      background: linear-gradient(90deg, var(--app-primary), color-mix(in srgb, var(--app-primary) 60%, #67c23a));
    }

    &.is-warn {
      background: #e6a23c;
    }

    &.is-danger {
      background: #f56c6c;
    }

    &.is-muted {
      background: #c0c4cc;
      width: 0 !important;
    }
  }

  &__sub {
    margin: 6px 0 0;
    font-size: 12px;
    color: $text-secondary;
  }
}

.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;

  &--dense {
    gap: 8px 10px;
  }
}

.kv {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--app-surface-muted) 90%, transparent);

  span {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }

  b {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    text-align: right;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sharding-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sharding-card {
  padding: 12px;
  border: 1px solid var(--app-border-color);
  border-radius: 10px;
  background: color-mix(in srgb, var(--app-surface-muted) 70%, transparent);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    flex-wrap: wrap;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    strong {
      font-size: 13px;
      color: $text-primary;
    }
  }

  &__prefix {
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 12px;
    color: $text-secondary;
  }

  &__missing {
    margin: 10px 0 0;
    font-size: 12px;
    color: #e6a23c;
  }
}

.sharding-table {
  margin-top: 10px;
  width: 100%;
}

@media (max-width: 1400px) {
  .monitor-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .monitor-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kv-grid {
    grid-template-columns: 1fr;
  }

  .monitor-hero__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
