<template>
  <div class="gateway-page">
    <el-card shadow="never" class="gateway-page__toolbar">
      <div class="gateway-page__header">
        <span class="gateway-page__title">调用用量</span>
        <div class="gateway-page__actions">
          <el-radio-group v-model="preset" size="small" @change="handlePresetChange">
            <el-radio-button value="7">近 7 天</el-radio-button>
            <el-radio-button value="30">近 30 天</el-radio-button>
            <el-radio-button value="90">近 90 天</el-radio-button>
            <el-radio-button value="all">全部</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            size="small"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 230px"
            @change="preset = ''"
          />
          <el-button size="small" :icon="Search" @click="loadSummary">查询</el-button>
          <el-button size="small" :icon="Refresh" @click="handleRefresh">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="gateway-page__stats">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">请求总数</div>
          <div class="stat-card__value">{{ summary.totalRequests }}</div>
          <div class="stat-card__meta">{{ rangeLabel }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">总 token</div>
          <div class="stat-card__value">{{ summary.totalTokens }}</div>
          <div class="stat-card__meta">平均 {{ avgTokens }} / 次</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">成功 / 失败</div>
          <div class="stat-card__value">{{ summary.successCount }} / {{ summary.errorCount }}</div>
          <div class="stat-card__meta">失败含超时、额度拒绝、中断</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">估算计量</div>
          <div class="stat-card__value">{{ summary.estimatedCount }}</div>
          <div class="stat-card__meta">上游未返回 usage 时按估算结算</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-loading="loading" :gutter="12" class="gateway-page__stats">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按供应商（token 降序，最多 20 条）</template>
          <el-table :data="summary.byProvider" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="供应商" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="100" align="right" />
            <el-table-column label="token 占比" min-width="130">
              <template #default="{ row }">
                <div class="share">
                  <el-progress
                    :percentage="percent(row.tokens)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="share__text">{{ percent(row.tokens) }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按模型（token 降序，最多 20 条）</template>
          <el-table :data="summary.byModel" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="模型" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="100" align="right" />
            <el-table-column label="token 占比" min-width="130">
              <template #default="{ row }">
                <div class="share">
                  <el-progress
                    :percentage="percent(row.tokens)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="share__text">{{ percent(row.tokens) }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row v-loading="loading" :gutter="12" class="gateway-page__stats">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按用户（token 降序，最多 20 条）</template>
          <el-table :data="summary.byUser" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="用户" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="100" align="right" />
            <el-table-column label="token 占比" min-width="130">
              <template #default="{ row }">
                <div class="share">
                  <el-progress
                    :percentage="percent(row.tokens)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="share__text">{{ percent(row.tokens) }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>按天（日期升序）</template>
          <el-table :data="summary.byDay" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="日期" width="120" />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="110" align="right" />
            <el-table-column label="token 占比" min-width="130">
              <template #default="{ row }">
                <div class="share">
                  <el-progress
                    :percentage="percent(row.tokens)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="share__text">{{ percent(row.tokens) }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { summaryGatewayUsagesApi } from '@/api/gateway'
import type { GatewayUsageSummaryVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayUsageStats' })

const DEFAULT_PRESET_DAYS = 30

const loading = ref(false)
const preset = ref('30')
const dateRange = ref<[string, string] | null>(null)

/** 汇总初始值：后端未返回某分组时（旧版本）用空数组兜底，避免表格拿到 undefined */
function emptySummary(): GatewayUsageSummaryVO {
  return {
    totalRequests: 0,
    totalTokens: 0,
    successCount: 0,
    errorCount: 0,
    estimatedCount: 0,
    byProvider: [],
    byModel: [],
    byUser: [],
    byDay: [],
  }
}

const summary = ref<GatewayUsageSummaryVO>(emptySummary())

const rangeLabel = computed(() =>
  dateRange.value ? `${dateRange.value[0]} 至 ${dateRange.value[1]}` : '全部时间',
)

const avgTokens = computed(() => {
  const requests = summary.value.totalRequests
  return requests > 0 ? Math.round(summary.value.totalTokens / requests) : 0
})

function toDateStr(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function presetRange(days: number): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - (days - 1))
  return [toDateStr(start), toDateStr(end)]
}

async function loadSummary() {
  loading.value = true
  try {
    const data = await summaryGatewayUsagesApi({
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
    })
    summary.value = { ...emptySummary(), ...data }
  } finally {
    loading.value = false
  }
}

function handlePresetChange(value: string | number | boolean | undefined) {
  const key = String(value ?? '')
  if (key === 'all') {
    dateRange.value = null
    void loadSummary()
    return
  }
  const days = Number(key)
  if (!Number.isFinite(days) || days <= 0) return
  dateRange.value = presetRange(days)
  void loadSummary()
}

function handleRefresh() {
  if (preset.value === 'all') {
    dateRange.value = null
  } else if (preset.value) {
    dateRange.value = presetRange(Number(preset.value))
  }
  void loadSummary()
}

function percent(tokens: number) {
  const total = summary.value.totalTokens
  if (!total || tokens <= 0) return 0
  return Math.round((tokens / total) * 1000) / 10
}

onMounted(() => {
  dateRange.value = presetRange(DEFAULT_PRESET_DAYS)
  void loadSummary()
})
</script>

<style scoped>
.gateway-page__toolbar {
  margin-bottom: 12px;
}

.gateway-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.gateway-page__title {
  font-size: 15px;
  font-weight: 600;
}

.gateway-page__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gateway-page__stats {
  margin-bottom: 12px;
}

.stat-card__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat-card__value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
}

.stat-card__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}

.share {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share :deep(.el-progress) {
  flex: 1;
  min-width: 40px;
}

.share__text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  min-width: 40px;
  text-align: right;
}
</style>
