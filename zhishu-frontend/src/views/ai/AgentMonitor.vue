<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    hero-title="执行监控"
    hero-eyebrow="智能中心"
    :hero-eyebrow-icon="Monitor"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      查看智能体执行历史、性能指标（TTFT / TPOT）与执行轨迹详情。
    </template>
    <template #heroActions>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
    </template>

    <template #strip>
      <div class="monitor-strip">
        <el-select v-model="query.status" clearable placeholder="状态" class="monitor-strip__select">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
        </el-select>
        <el-select v-model="query.runType" clearable placeholder="执行类型" class="monitor-strip__select">
          <el-option label="正式对话" value="CHAT" />
          <el-option label="试运行" value="TRIAL" />
        </el-select>
        <el-select v-model="query.agentId" clearable filterable placeholder="全部智能体" class="monitor-strip__select">
          <el-option v-for="a in agentOptions" :key="a.id" :label="a.name" :value="a.id" />
        </el-select>
        <el-input v-model="query.keyword" clearable placeholder="搜索用户输入…" :prefix-icon="Search" class="monitor-strip__search" @keyup.enter="fetchList" />
        <el-button :icon="Search" @click="fetchList">查询</el-button>
      </div>
    </template>

    <template #toolbar>
      <ListToolbar title="执行记录">
        <template #hint>查看智能体执行历史与性能指标</template>
      </ListToolbar>
    </template>

    <el-table :data="tableData" class="modern-table" empty-text="暂无执行记录" @row-click="handleRowClick">
      <el-table-column prop="agentName" label="智能体" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="agent-name">{{ row.agentName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="userMessage" label="用户输入" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.userMessage || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <span :class="['status-chip', `status-chip--${statusTone(row.status)}`]">
            {{ statusLabel(row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">
          <span class="perf-cell">{{ formatDuration(row.durationMs) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="TTFT / TPOT" width="160">
        <template #default="{ row }">
          <span class="perf-cell">
            <span class="perf-label">TTFT</span>{{ formatMs(row.ttftMs) }}
            <span class="perf-sep">/</span>
            <span class="perf-label">TPOT</span>{{ formatMs(row.tpotMs) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="tokenCount" label="Tokens" width="80" align="center">
        <template #default="{ row }">
          {{ row.tokenCount ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="modelName" label="模型" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="model-tag">{{ row.modelName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="runType" label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" round>{{ row.runType === 'TRIAL' ? '试运行' : '对话' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="120">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="handleDetailClick(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #pagination>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </template>

    <el-drawer
      v-model="drawerVisible"
      direction="rtl"
      size="768px"
      destroy-on-close
      :close-on-click-modal="true"
      class="exec-detail-drawer"
    >
      <template #header>
        <div class="drawer-header">
          <div class="drawer-header-main">
            <div class="drawer-header-icon" :class="`is-${statusTone(detail?.status || 'info')}`">
              <el-icon :size="22"><DataLine /></el-icon>
            </div>
            <div class="drawer-header-text">
              <div class="drawer-eyebrow">执行详情</div>
              <div class="drawer-title">{{ detail?.agentName || '加载中…' }}</div>
              <div class="drawer-subtitle">
                <span v-if="detail" class="drawer-code">{{ detail.modelName || '未知模型' }}</span>
                <span :class="['status-chip', `status-chip--${statusTone(detail?.status || '')}`]">
                  {{ statusLabel(detail?.status || '') }}
                </span>
                <el-tag size="small" effect="plain" round>
                  {{ detail?.runType === 'TRIAL' ? '试运行' : '正式对话' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </template>

      <div class="drawer-body">
        <template v-if="detail">
          <!-- 性能指标卡片 -->
          <div class="drawer-summary">
            <div class="summary-card summary-card--primary">
              <div class="summary-card__icon">
                <el-icon :size="18"><Timer /></el-icon>
              </div>
              <div class="summary-card__content">
                <span class="summary-card__label">执行耗时</span>
                <span class="summary-card__value">{{ formatDuration(detail.durationMs) }}</span>
              </div>
            </div>
            <div class="summary-card">
              <div class="summary-card__icon">
                <el-icon :size="18"><TrendCharts /></el-icon>
              </div>
              <div class="summary-card__content">
                <span class="summary-card__label">Token 总数</span>
                <span class="summary-card__value">{{ detail.tokenCount ?? '—' }}</span>
              </div>
            </div>
            <div class="summary-card">
              <div class="summary-card__icon">
                <el-icon :size="18"><CircleCheck /></el-icon>
              </div>
              <div class="summary-card__content">
                <span class="summary-card__label">TTFT</span>
                <span class="summary-card__value">{{ formatMs(detail.ttftMs) }}</span>
              </div>
            </div>
            <div class="summary-card summary-card--time">
              <div class="summary-card__icon">
                <el-icon :size="18"><Clock /></el-icon>
              </div>
              <div class="summary-card__content">
                <span class="summary-card__label">执行时间</span>
                <span class="summary-card__value">{{ formatTime(detail.createTime) }}</span>
              </div>
            </div>
          </div>

          <!-- 基本信息 -->
          <el-card shadow="never" class="drawer-section-card">
            <template #header>
              <div class="section-card-header">
                <span class="section-card-title">基本信息</span>
              </div>
            </template>
            <el-descriptions :column="1" border class="drawer-descriptions">
              <el-descriptions-item label="智能体">{{ detail.agentName }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ detail.modelName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="TPOT">{{ formatMs(detail.tpotMs) }}</el-descriptions-item>
              <el-descriptions-item label="执行类型">{{ detail.runType === 'TRIAL' ? '试运行' : '正式对话' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 用户输入 -->
          <el-card v-if="detail.userMessage" shadow="never" class="drawer-section-card">
            <template #header>
              <div class="section-card-header">
                <span class="section-card-title">用户输入</span>
              </div>
            </template>
            <pre class="doc-content-text">{{ detail.userMessage }}</pre>
          </el-card>

          <!-- 响应摘要 -->
          <el-card v-if="detail.responseSummary" shadow="never" class="drawer-section-card">
            <template #header>
              <div class="section-card-header">
                <span class="section-card-title">响应摘要</span>
              </div>
            </template>
            <pre class="doc-content-text doc-content-text--response">{{ detail.responseSummary }}</pre>
          </el-card>

          <!-- 错误信息 -->
          <el-card v-if="detail.errorMessage" shadow="never" class="drawer-section-card drawer-section-card--error">
            <template #header>
              <div class="section-card-header">
                <span class="section-card-title">错误信息</span>
              </div>
            </template>
            <pre class="doc-content-text doc-content-text--error">{{ detail.errorMessage }}</pre>
          </el-card>

          <!-- 执行轨迹 -->
          <el-card v-if="detail.traces && detail.traces.length" shadow="never" class="drawer-section-card">
            <template #header>
              <div class="section-card-header">
                <span class="section-card-title">执行轨迹</span>
                <span class="section-meta">{{ detail.traces.length }} 步</span>
              </div>
            </template>
            <div class="trace-timeline">
              <div v-for="(trace, idx) in detail.traces" :key="idx" :class="['trace-item', `trace-item--${traceTone(trace.type)}`]">
                <div class="trace-item__dot" />
                <div class="trace-item__body">
                  <div class="trace-item__head">
                    <span class="trace-item__name">{{ trace.name }}</span>
                    <span v-if="trace.durationMs" class="trace-item__duration">{{ trace.durationMs }}ms</span>
                  </div>
                  <div v-if="trace.detail" class="trace-item__detail">{{ trace.detail }}</div>
                </div>
              </div>
            </div>
          </el-card>
        </template>
        <el-empty v-else description="加载中…" :image-size="64" />
      </div>
    </el-drawer>
  </ListPageShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Monitor, Refresh, Search, DataLine, CircleCheck, Timer, TrendCharts, Clock } from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import type { PageHeroMetric } from '@/components/list-page/PageHero.vue'
import {
  getAgentExecutions,
  getAgentExecutionDetail,
  getAgentMonitorStats,
  listAgents,
} from '@/api/ai'
import type {
  AgentExecutionVO,
  AgentExecutionDetailVO,
  AgentMonitorStatsVO,
  AgentMonitorQuery,
} from '@/types/agentMonitor'
import type { AgentVO } from '@/types/aiAgent'

const loading = ref(false)
const tableData = ref<AgentExecutionVO[]>([])
const total = ref(0)
const stats = ref<AgentMonitorStatsVO | null>(null)
const agentOptions = ref<AgentVO[]>([])
const drawerVisible = ref(false)
const detail = ref<AgentExecutionDetailVO | null>(null)

const query = reactive<AgentMonitorQuery>({
  page: 1,
  size: 20,
  status: null,
  runType: null,
  agentId: null,
  keyword: null,
})

const heroMetrics = computed<PageHeroMetric[]>(() => {
  const s = stats.value
  if (!s) return []
  return [
    { key: 'total', label: '总执行', value: s.totalCount, icon: DataLine, accent: 'primary' },
    { key: 'rate', label: '成功率', value: `${s.successRate}%`, icon: CircleCheck, accent: 'success' },
    { key: 'avg', label: '平均耗时', value: formatDuration(s.avgDurationMs), icon: Timer, accent: 'primary' },
    { key: 'today', label: '今日执行', value: s.todayCount, icon: TrendCharts, accent: 'primary' },
  ]
})

onMounted(() => {
  fetchAgents()
  fetchList()
  fetchStats()
})

async function fetchAgents() {
  try { agentOptions.value = await listAgents() } catch { /* ignore */ }
}

async function fetchStats() {
  try { stats.value = await getAgentMonitorStats('TODAY') } catch { /* ignore */ }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getAgentExecutions(query)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function refreshAll() { fetchList(); fetchStats() }

function handleRowClick(row: AgentExecutionVO) {
  openDetail(row)
}

function handleDetailClick(row: unknown) {
  openDetail(row as AgentExecutionVO)
}

async function openDetail(row: AgentExecutionVO) {
  drawerVisible.value = true
  detail.value = null
  try { detail.value = await getAgentExecutionDetail(row.id) } catch { detail.value = null }
}

function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatMs(ms: number | null | undefined): string {
  if (ms == null || ms < 0) return '—'
  return `${ms}ms`
}

function formatTime(t: string | null | undefined): string {
  if (!t) return '—'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function statusLabel(s: string): string {
  return { SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中' }[s] || s
}

function statusTone(s: string): string {
  return { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning' }[s] || 'info'
}

function traceTone(type: string): string {
  if (type.includes('START')) return 'start'
  if (type.includes('END')) return 'end'
  if (type.includes('TOOL')) return 'tool'
  return 'default'
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.monitor-strip {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid $border-lighter;
  flex-wrap: wrap;

  &__select { width: 140px; }
  &__search { width: 220px; }
}

.agent-name { font-weight: 500; color: $text-primary; }

.status-chip {
  &--success {
    color: $success-color;
    background: rgba(103, 194, 58, 0.1);
    border-color: rgba(103, 194, 58, 0.3);
  }
  &--danger {
    color: $danger-color;
    background: rgba(245, 108, 108, 0.1);
    border-color: rgba(245, 108, 108, 0.3);
  }
  &--warning {
    color: $warning-color;
    background: rgba(230, 162, 60, 0.1);
    border-color: rgba(230, 162, 60, 0.3);
  }
}

.model-tag {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: $text-secondary;
  background: $bg-color;
  padding: 2px 6px;
  border-radius: $border-radius-sm;
}

.perf-cell {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: $text-primary;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.perf-label {
  font-size: 10px;
  color: $text-secondary;
  font-weight: 600;
  text-transform: uppercase;
}
.perf-sep { color: $border-color; margin: 0 1px; }

/* ── Drawer header ── */
.drawer-header {
  display: flex;
  align-items: center;
  min-width: 0;
}

.drawer-header-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.drawer-header-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;

  &.is-success {
    background: linear-gradient(135deg, #67c23a 0%, #4ea82a 100%);
    box-shadow: 0 4px 14px rgba(103, 194, 58, 0.38);
  }
  &.is-danger {
    background: linear-gradient(135deg, #f56c6c 0%, #d9534f 100%);
    box-shadow: 0 4px 14px rgba(245, 108, 108, 0.38);
  }
  &.is-warning {
    background: linear-gradient(135deg, #e6a23c 0%, #cf9225 100%);
    box-shadow: 0 4px 14px rgba(230, 162, 60, 0.38);
  }
  &.is-info {
    background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
    box-shadow: 0 4px 14px rgba(64, 158, 255, 0.35);
  }
}

.drawer-header-text { min-width: 0; }

.drawer-eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $primary-color;
  margin-bottom: 2px;
}

.drawer-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.drawer-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: $text-secondary;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(64, 158, 255, 0.06);
  border: 1px solid rgba(64, 158, 255, 0.12);
}

/* ── Drawer body ── */
.drawer-body {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 16px 20px 24px;
  background:
    radial-gradient(ellipse at top right, rgba(64, 158, 255, 0.06) 0%, transparent 55%),
    $bg-color;
}

/* ── Summary cards ── */
.drawer-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: $border-radius-md;
  border: 1px solid $border-lighter;
  background: $bg-white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: rgba(144, 147, 153, 0.12);
    color: $info-color;
    flex-shrink: 0;
  }

  &__content {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__value {
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.3;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }

  &--primary .summary-card__icon {
    background: rgba(64, 158, 255, 0.12);
    color: $primary-color;
  }

  &--time .summary-card__icon {
    background: rgba(64, 158, 255, 0.12);
    color: $primary-color;
  }
}

/* ── Section cards ── */
.drawer-section-card {
  margin-bottom: 16px;
  border-radius: $border-radius-md;
  border: 1px solid $border-lighter;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;

  &:last-child { margin-bottom: 0; }

  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  }

  :deep(.el-card__body) {
    padding: 16px;
    background: $bg-white;
  }

  &--error :deep(.el-card__header) {
    background: linear-gradient(180deg, #fff5f5 0%, #fff 100%);
  }
}

.section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-card-title {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 14px;
    border-radius: 2px;
    background: $bg-gradient;
  }
}

.section-meta {
  font-size: 12px;
  color: $text-secondary;
}

/* ── Descriptions ── */
.drawer-descriptions {
  :deep(.el-descriptions__label) {
    width: 100px;
    font-weight: 500;
    color: $text-regular;
    background: #f8fafc;
  }

  :deep(.el-descriptions__content) {
    color: $text-primary;
    font-weight: 500;
  }
}

/* ── Content text ── */
.doc-content-text {
  margin: 0;
  padding: 4px 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: $text-regular;

  &--response {
    padding: 10px 14px;
    border-radius: 6px;
    background: $bg-color;
    border-left: 3px solid $success-color;
  }

  &--error {
    padding: 10px 14px;
    border-radius: 6px;
    background: #fff5f5;
    border-left: 3px solid $danger-color;
    color: $danger-color;
  }
}

/* ── 执行轨迹 ── */
.trace-timeline {
  position: relative;
  padding-left: 24px;

  &::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 4px;
    bottom: 4px;
    width: 2px;
    background: $border-lighter;
  }
}

.trace-item {
  position: relative;
  padding-bottom: 16px;

  &:last-child { padding-bottom: 0; }

  &__dot {
    position: absolute;
    left: -24px;
    top: 5px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 2px solid $border-color;
    background: $bg-white;
  }

  &--start &__dot { border-color: $primary-color; background: $primary-color; }
  &--end &__dot   { border-color: $success-color; background: $success-color; }
  &--tool &__dot  { border-color: $warning-color; background: $warning-color; }

  &__body { min-width: 0; }

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__name { font-size: 13px; font-weight: 500; color: $text-primary; }

  &__duration {
    font-size: 11px;
    color: $info-color;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    background: rgba(64, 158, 255, 0.08);
    padding: 1px 6px;
    border-radius: 3px;
  }

  &__detail {
    margin-top: 4px;
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.6;
    word-break: break-word;
    padding: 4px 8px;
    background: $bg-color;
    border-radius: 4px;
  }
}
</style>

<style lang="scss">
@use '@/styles/variables.scss' as *;

.exec-detail-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 18px 20px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
    position: relative;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: linear-gradient(90deg, #409eff 0%, #66b1ff 50%, #409eff 100%);
    }
  }

  .el-drawer__body {
    padding: 0;
    background: $bg-color;
    overflow: auto;
  }

  .el-drawer__close-btn {
    font-size: 18px;
    width: 32px;
    height: 32px;
    border-radius: 8px;

    &:hover {
      color: $primary-color;
      background: rgba(64, 158, 255, 0.08);
    }
  }
}
</style>
