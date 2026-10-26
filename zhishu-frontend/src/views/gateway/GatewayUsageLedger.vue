<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">调用台账</span>
          <div class="gateway-page__actions">
            <el-input
              v-model="filters.username"
              size="small"
              placeholder="用户名"
              clearable
              style="width: 130px"
            />
            <el-input
              v-model="filters.modelId"
              size="small"
              placeholder="模型 ID"
              clearable
              style="width: 170px"
            />
            <el-select
              v-model="filters.channel"
              size="small"
              placeholder="通道"
              clearable
              style="width: 120px"
            >
              <el-option v-for="item in channels" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select
              v-model="filters.status"
              size="small"
              placeholder="状态"
              clearable
              style="width: 130px"
            >
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
            <el-button size="small" :icon="Refresh" @click="handleReset">重置</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" class="modern-table" empty-text="暂无调用记录">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="110" show-overflow-tooltip />
        <el-table-column prop="channel" label="通道" width="100" align="center" />
        <el-table-column prop="modelId" label="模型" min-width="170" show-overflow-tooltip />
        <el-table-column prop="upstreamModel" label="上游模型" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.upstreamModel || '—' }}</template>
        </el-table-column>
        <el-table-column label="token" width="150" align="right">
          <template #default="{ row }">
            <el-tooltip
              placement="top"
              :content="`缓存 ${row.cachedTokens ?? 0} / 推理 ${row.reasoningTokens ?? 0}`"
            >
              <span>
                {{ row.promptTokens ?? '—' }} / {{ row.completionTokens ?? '—' }} /
                {{ row.totalTokens ?? '—' }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="130" align="right">
          <template #default="{ row }">
            <div>{{ row.latencyMs != null ? `${row.latencyMs} ms` : '—' }}</div>
            <div v-if="row.ttftMs != null" class="gateway-page__meta">首字 {{ row.ttftMs }} ms</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">{{
              row.status
            }}</el-tag>
            <el-tag v-if="row.estimated" size="small" type="warning" effect="plain" class="ml-4"
              >估算</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column prop="requestId" label="请求 ID" width="170" show-overflow-tooltip />
        <el-table-column label="错误" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMessage || row.errorCode || '—' }}</template>
        </el-table-column>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageGatewayUsagesApi } from '@/api/gateway'
import type { GatewayUsageVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayUsageLedger' })

const channels = ['DESKTOP', 'CONSOLE', 'AGENT', 'BRIEFING', 'OPENAPI', 'TEST']
const statuses = ['SUCCESS', 'ERROR', 'ABORTED', 'TIMEOUT', 'REJECTED_QUOTA', 'RESERVED']

const loading = ref(false)
const records = ref<GatewayUsageVO[]>([])
const dateRange = ref<[string, string] | null>(null)
const filters = reactive({ username: '', modelId: '', channel: '', status: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

async function loadRecords() {
  loading.value = true
  try {
    const page = await pageGatewayUsagesApi({
      username: filters.username || undefined,
      modelId: filters.modelId || undefined,
      channel: filters.channel || undefined,
      status: filters.status || undefined,
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
      current: pagination.current,
      size: pagination.size,
    })
    records.value = page.records ?? []
    pagination.total = page.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  void loadRecords()
}

function handleReset() {
  filters.username = ''
  filters.modelId = ''
  filters.channel = ''
  filters.status = ''
  dateRange.value = null
  handleSearch()
}

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'REJECTED_QUOTA') return 'warning'
  if (status === 'RESERVED') return 'info'
  return 'danger'
}

onMounted(() => {
  void loadRecords()
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

.gateway-page__pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.gateway-page__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ml-4 {
  margin-left: 4px;
}
</style>
