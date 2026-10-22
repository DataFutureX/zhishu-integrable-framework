<template>
  <div class="gateway-page">
    <el-row :gutter="12" class="gateway-page__stats">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">请求总数</div>
          <div class="stat-card__value">{{ summary.totalRequests }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">总 token</div>
          <div class="stat-card__value">{{ summary.totalTokens }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">失败次数</div>
          <div class="stat-card__value">{{ summary.errorCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__label">估算计量</div>
          <div class="stat-card__value">{{ summary.estimatedCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">调用台账</span>
          <div class="gateway-page__actions">
            <el-input v-model="filters.username" size="small" placeholder="用户名" clearable style="width: 130px" />
            <el-input v-model="filters.modelId" size="small" placeholder="模型 ID" clearable style="width: 170px" />
            <el-select v-model="filters.channel" size="small" placeholder="通道" clearable style="width: 120px">
              <el-option v-for="item in channels" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="filters.status" size="small" placeholder="状态" clearable style="width: 130px">
              <el-option v-for="item in statuses" :key="item" :label="item" :value="item" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              size="small"
              value-format="YYYY-MM-DD"
              start-placeholder="开始"
              end-placeholder="结束"
              style="width: 230px"
            />
            <el-button size="small" :icon="Search" @click="handleSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" class="modern-table" empty-text="暂无调用记录">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="110" show-overflow-tooltip />
        <el-table-column prop="channel" label="通道" width="100" align="center" />
        <el-table-column prop="modelId" label="模型" min-width="180" show-overflow-tooltip />
        <el-table-column label="token" width="150" align="right">
          <template #default="{ row }">
            {{ row.promptTokens ?? '—' }} / {{ row.completionTokens ?? '—' }} / {{ row.totalTokens ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="120" align="right">
          <template #default="{ row }">{{ row.latencyMs != null ? `${row.latencyMs} ms` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">{{ row.status }}</el-tag>
            <el-tag v-if="row.estimated" size="small" type="warning" effect="plain" class="ml-4">估算</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误" min-width="160" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="gateway-page__pager"
        @change="loadRecords"
      />
    </el-card>

    <el-row :gutter="12" class="gateway-page__stats">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>按模型</template>
          <el-table :data="summary.byModel" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="模型" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="110" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>按用户</template>
          <el-table :data="summary.byUser" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="用户" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="110" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>按天</template>
          <el-table :data="summary.byDay" size="small" empty-text="暂无数据">
            <el-table-column prop="key" label="日期" show-overflow-tooltip />
            <el-table-column prop="requests" label="次数" width="80" align="right" />
            <el-table-column prop="tokens" label="token" width="110" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { pageGatewayUsagesApi, summaryGatewayUsagesApi } from '@/api/gateway'
import type { GatewayUsageSummaryVO, GatewayUsageVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayUsageList' })

const channels = ['DESKTOP', 'CONSOLE', 'AGENT', 'BRIEFING', 'OPENAPI', 'TEST']
const statuses = ['SUCCESS', 'ERROR', 'ABORTED', 'TIMEOUT', 'REJECTED_QUOTA', 'RESERVED']

const loading = ref(false)
const records = ref<GatewayUsageVO[]>([])
const dateRange = ref<[string, string] | null>(null)
const filters = reactive({ username: '', modelId: '', channel: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })
const summary = ref<GatewayUsageSummaryVO>({
  totalRequests: 0,
  totalTokens: 0,
  successCount: 0,
  errorCount: 0,
  estimatedCount: 0,
  byModel: [],
  byUser: [],
  byDay: [],
})

function queryParams() {
  return {
    username: filters.username || undefined,
    modelId: filters.modelId || undefined,
    channel: filters.channel || undefined,
    status: filters.status || undefined,
    from: dateRange.value?.[0],
    to: dateRange.value?.[1],
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const page = await pageGatewayUsagesApi({
      ...queryParams(),
      current: pagination.current,
      size: pagination.size,
    })
    records.value = page.records ?? []
    pagination.total = page.total ?? 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  summary.value = await summaryGatewayUsagesApi({
    from: dateRange.value?.[0],
    to: dateRange.value?.[1],
  })
}

function handleSearch() {
  pagination.current = 1
  void loadRecords()
  void loadSummary()
}

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'REJECTED_QUOTA') return 'warning'
  if (status === 'RESERVED') return 'info'
  return 'danger'
}

onMounted(() => {
  void loadRecords()
  void loadSummary()
})
</script>

<style scoped>
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

.gateway-page__pager {
  margin-top: 12px;
  justify-content: flex-end;
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

.ml-4 {
  margin-left: 4px;
}
</style>
