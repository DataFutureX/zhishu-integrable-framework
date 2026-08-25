<template>
  <ListPageShell
    :loading="loading"
    hero-title="操作日志"
    hero-eyebrow="系统设置"
    :hero-eyebrow-icon="Document"
    :hero-metrics="heroMetrics"
    :hero-enable-rate="pageSuccessRate"
    hero-ring-label="成功率"
  >
    <template #heroDescription>
      共 <strong>{{ total }}</strong> 条记录，本页成功率 <strong>{{ pageSuccessRate }}%</strong>
    </template>
    <template #heroActions>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </template>

    <template #strip>
      <StatusFilterStrip
        :model-value="queryParams.status"
        :options="statusFilterOptions"
        @update:model-value="applyStatusFilter"
      />
    </template>

    <template #filter>
      <ListFilterPanel :active-count="activeFilterCount" :default-expanded="true">
        <el-form :inline="true" :model="queryParams" label-width="96px">
          <el-form-item label="操作用户">
            <el-input
              v-model="queryParams.username"
              placeholder="请输入用户名"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="模块名称">
            <el-input
              v-model="queryParams.module"
              placeholder="请输入模块名称"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="操作类型">
            <el-input
              v-model="queryParams.operation"
              placeholder="请输入操作类型"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="操作时间">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              :default-time="defaultRangeTime"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
        <template v-if="filterChips.length" #chips>
          <el-tag
            v-for="chip in filterChips"
            :key="chip.key"
            closable
            round
            effect="plain"
            @close="removeFilterChip(chip.key)"
          >
            {{ chip.label }}
          </el-tag>
        </template>
      </ListFilterPanel>
    </template>

    <template #toolbar>
      <ListToolbar title="操作日志列表">
        <template #hint>记录系统用户的操作行为，支持按用户、模块与时间筛选</template>
      </ListToolbar>
    </template>

    <el-table
      :data="tableData"
      class="modern-table"
      :row-class-name="tableRowClassName"
      empty-text="暂无操作日志"
    >
      <el-table-column
        type="index"
        label="序号"
        width="60"
        align="center"
        fixed="left"
        :index="tableIndexMethod"
      />
      <el-table-column label="操作时间" width="180" fixed="left" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.createTime ? formatDateTime(row.createTime) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作用户" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="name-cell">
            <span
              class="status-dot"
              :class="row.status === 1 ? 'status-dot--enabled' : 'status-dot--disabled'"
            />
            <span>{{ formatOperator(row as OperationLogVO) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.module || '—' }}</template>
      </el-table-column>
      <el-table-column prop="operation" label="操作" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.operation || '—' }}</template>
      </el-table-column>
      <el-table-column prop="method" label="请求方法" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.method" size="small" :type="methodTagType(row.method)" effect="plain">
            {{ row.method }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="responseCode" label="响应码" width="88" align="center">
        <template #default="{ row }">
          <span :class="responseCodeClass(row.responseCode)">{{ row.responseCode ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时" width="96" align="right">
        <template #default="{ row }">
          {{ row.durationMs != null ? `${row.durationMs} ms` : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP 地址" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.ipAddress || '—' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="88" align="center">
        <template #default="{ row }">
          <span
            class="status-pill"
            :class="row.status === 1 ? 'status-pill--enabled' : 'status-pill--disabled'"
          >
            {{ row.status === 1 ? '成功' : '失败' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="88" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :icon="View" @click="handleViewDetail(row as OperationLogVO)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #pagination>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </template>

    <template #extra>
      <el-drawer
        v-model="detailVisible"
        title="操作日志详情"
        size="520px"
        destroy-on-close
      >
        <div v-loading="detailLoading" class="detail-panel">
          <template v-if="detailData">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="日志 ID">{{ detailData.id }}</el-descriptions-item>
              <el-descriptions-item label="操作用户">{{ formatOperator(detailData) }}</el-descriptions-item>
              <el-descriptions-item label="模块">{{ detailData.module || '—' }}</el-descriptions-item>
              <el-descriptions-item label="操作">{{ detailData.operation || '—' }}</el-descriptions-item>
              <el-descriptions-item label="请求方法">{{ detailData.method || '—' }}</el-descriptions-item>
              <el-descriptions-item label="响应码">{{ detailData.responseCode ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="耗时">
                {{ detailData.durationMs != null ? `${detailData.durationMs} ms` : '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
                  {{ detailData.status === 1 ? '成功' : '失败' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="IP 地址">{{ detailData.ipAddress || '—' }}</el-descriptions-item>
              <el-descriptions-item label="操作时间">
                {{ detailData.createTime ? formatDateTime(detailData.createTime) : '—' }}
              </el-descriptions-item>
            </el-descriptions>

            <div v-if="detailData.errorMessage" class="detail-block">
              <div class="detail-block__title">失败原因</div>
              <pre class="detail-block__content detail-block__content--error">{{ detailData.errorMessage }}</pre>
            </div>

            <div v-if="detailData.requestParams" class="detail-block">
              <div class="detail-block__title">请求参数</div>
              <pre class="detail-block__content">{{ formatRequestParams(detailData.requestParams) }}</pre>
            </div>

            <div v-if="detailData.userAgent" class="detail-block">
              <div class="detail-block__title">User-Agent</div>
              <pre class="detail-block__content detail-block__content--wrap">{{ detailData.userAgent }}</pre>
            </div>
          </template>
        </div>
      </el-drawer>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { Search, Refresh, Document, View, List, CircleCheck } from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import { getOperationLogPageApi, getOperationLogDetailApi } from '@/api/operationLog'
import type { OperationLogQueryDTO, OperationLogVO } from '@/types/operationLog'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const tableData = ref<OperationLogVO[]>([])
const total = ref(0)
const detailData = ref<OperationLogVO | null>(null)
const timeRange = ref<[string, string] | null>(null)

const defaultRangeTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
]

const queryParams = reactive<Required<Pick<OperationLogQueryDTO, 'pageNum' | 'pageSize'>> & OperationLogQueryDTO>({
  username: '',
  module: '',
  operation: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20,
})

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '成功', value: 1, dot: 'enabled' as const },
  { label: '失败', value: 0, dot: 'disabled' as const },
]

const pageSuccessCount = computed(() => tableData.value.filter((row) => row.status === 1).length)

const pageSuccessRate = computed(() => {
  if (!tableData.value.length) return 0
  return Math.round((pageSuccessCount.value / tableData.value.length) * 100)
})

const heroMetrics = computed(() => [
  { key: 'total', label: '日志总数', value: total.value, icon: List, accent: 'primary' as const },
  { key: 'success', label: '本页成功', value: pageSuccessCount.value, icon: CircleCheck, accent: 'success' as const },
])

const activeFilterCount = computed(() => filterChips.value.length)

const filterChips = computed(() => {
  const chips: { key: string; label: string }[] = []
  if (queryParams.username?.trim()) {
    chips.push({ key: 'username', label: `用户：${queryParams.username.trim()}` })
  }
  if (queryParams.module?.trim()) {
    chips.push({ key: 'module', label: `模块：${queryParams.module.trim()}` })
  }
  if (queryParams.operation?.trim()) {
    chips.push({ key: 'operation', label: `操作：${queryParams.operation.trim()}` })
  }
  if (queryParams.status === 0 || queryParams.status === 1) {
    chips.push({ key: 'status', label: queryParams.status === 1 ? '成功' : '失败' })
  }
  if (timeRange.value?.[0] && timeRange.value?.[1]) {
    chips.push({ key: 'timeRange', label: `${timeRange.value[0]} ~ ${timeRange.value[1]}` })
  }
  return chips
})

const formatOperator = (row: OperationLogVO) => {
  if (row.realName && row.username) return `${row.realName}（${row.username}）`
  return row.realName || row.username || '—'
}

const formatRequestParams = (value: string) => {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const methodTagType = (method: string) => {
  const normalized = method.toUpperCase()
  if (normalized === 'GET') return 'success'
  if (normalized === 'POST') return 'primary'
  if (normalized === 'PUT') return 'warning'
  if (normalized === 'DELETE') return 'danger'
  return 'info'
}

const responseCodeClass = (code?: number) => {
  if (code == null) return ''
  if (code >= 200 && code < 300) return 'response-code--success'
  if (code >= 400) return 'response-code--danger'
  return 'response-code--warning'
}

const tableRowClassName = ({ row }: { row: OperationLogVO }) => {
  return row.status === 1 ? '' : 'log-row--failed'
}

const buildQueryParams = (): OperationLogQueryDTO => {
  const params: OperationLogQueryDTO = {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
  }
  if (queryParams.username?.trim()) params.username = queryParams.username.trim()
  if (queryParams.module?.trim()) params.module = queryParams.module.trim()
  if (queryParams.operation?.trim()) params.operation = queryParams.operation.trim()
  if (queryParams.status === 0 || queryParams.status === 1) params.status = queryParams.status
  if (timeRange.value?.[0]) params.startTime = timeRange.value[0]
  if (timeRange.value?.[1]) params.endTime = timeRange.value[1]
  return params
}

const fetchList = async () => {
  loading.value = true
  try {
    const pageData = await getOperationLogPageApi(buildQueryParams())
    tableData.value = pageData.records
    total.value = pageData.total
  } catch (error) {
    console.error('获取操作日志失败:', error)
  } finally {
    loading.value = false
  }
}

const tableIndexMethod = (index: number) => {
  const pageNum = queryParams.pageNum ?? 1
  const pageSize = queryParams.pageSize ?? 20
  return (pageNum - 1) * pageSize + index + 1
}

const applyStatusFilter = (status: number | undefined) => {
  queryParams.status = status
  queryParams.pageNum = 1
  fetchList()
}

const removeFilterChip = (key: string) => {
  if (key === 'username') queryParams.username = ''
  else if (key === 'module') queryParams.module = ''
  else if (key === 'operation') queryParams.operation = ''
  else if (key === 'status') queryParams.status = undefined
  else if (key === 'timeRange') timeRange.value = null
  queryParams.pageNum = 1
  fetchList()
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchList()
}

const handleReset = () => {
  queryParams.username = ''
  queryParams.module = ''
  queryParams.operation = ''
  queryParams.status = undefined
  timeRange.value = null
  queryParams.pageNum = 1
  fetchList()
}

const handleViewDetail = async (row: OperationLogVO) => {
  detailVisible.value = true
  detailData.value = null
  detailLoading.value = true
  try {
    detailData.value = await getOperationLogDetailApi(row.id)
  } catch (error) {
    console.error('获取操作日志详情失败:', error)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

useRouteActivate(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

:deep(.log-row--failed) {
  --el-table-tr-bg-color: rgba(245, 108, 108, 0.04);
}

.response-code {
  &--success {
    color: $success-color;
    font-weight: 600;
  }

  &--danger {
    color: $danger-color;
    font-weight: 600;
  }

  &--warning {
    color: $warning-color;
    font-weight: 600;
  }
}

.detail-panel {
  min-height: 200px;
}

.detail-block {
  margin-top: 16px;

  &__title {
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  &__content {
    margin: 0;
    padding: 12px;
    max-height: 240px;
    overflow: auto;
    font-size: 12px;
    line-height: 1.6;
    color: $text-regular;
    background: #f6f8fa;
    border: 1px solid $border-lighter;
    border-radius: $border-radius-md;
    white-space: pre-wrap;
    word-break: break-all;

    &--error {
      color: $danger-color;
      background: rgba(245, 108, 108, 0.06);
      border-color: rgba(245, 108, 108, 0.2);
    }

    &--wrap {
      white-space: pre-wrap;
    }
  }
}
</style>
