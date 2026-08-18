<template>
  <ListPageShell
    :loading="loading"
    hero-title="公告管理"
    hero-eyebrow="系统管理"
    :hero-eyebrow-icon="Notification"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      共 <strong>{{ total }}</strong> 条公告，已发布 <strong>{{ publishedCount }}</strong> 条
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_ADD"
        type="primary"
        size="small"
        :icon="Plus"
        @click="openCreateDialog"
      >
        新建公告
      </el-button>
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
          <el-form-item label="标题">
            <el-input
              v-model="queryParams.title"
              placeholder="请输入标题关键词"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 120px">
              <el-option label="普通" :value="0" />
              <el-option label="重要" :value="1" />
              <el-option label="紧急" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </ListFilterPanel>
    </template>

    <el-table v-loading="loading" :data="tableData" class="modern-table" empty-text="暂无公告">
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="优先级" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="priorityTagType(row.priority)" size="small" effect="plain">
            {{ priorityLabel(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publisherName" label="发布人" width="110" />
      <el-table-column prop="publishTime" label="发布时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.publishTime) || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_QUERY"
            type="primary"
            link
            size="small"
            @click.stop="openDetailDialog(row as AnnouncementVO)"
          >
            查看
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.DRAFT || row.status === AnnouncementStatus.REVOKED"
            v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_EDIT"
            type="primary"
            link
            size="small"
            @click.stop="openEditDialog(row as AnnouncementVO)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.DRAFT || row.status === AnnouncementStatus.REVOKED"
            v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_PUBLISH"
            type="success"
            link
            size="small"
            @click.stop="handlePublish(row as AnnouncementVO)"
          >
            {{ row.status === AnnouncementStatus.REVOKED ? '重新发布' : '发布' }}
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.PUBLISHED"
            v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_PUBLISH"
            type="warning"
            link
            size="small"
            @click.stop="handleRevoke(row as AnnouncementVO)"
          >
            撤回
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.DRAFT || row.status === AnnouncementStatus.REVOKED"
            v-permission="PERMISSIONS.SYSTEM_ANNOUNCEMENT_REMOVE"
            type="danger"
            link
            size="small"
            @click.stop="handleDelete(row as AnnouncementVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #pagination>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="fetchList"
        @size-change="handleSearch"
      />
    </template>
  </ListPageShell>

  <el-dialog
    v-model="formDialogVisible"
    :title="formDialogTitle"
    width="640px"
    append-to-body
    destroy-on-close
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="88px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入公告标题" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-radio-group v-model="formData.priority">
          <el-radio :value="0">普通</el-radio>
          <el-radio :value="1">重要</el-radio>
          <el-radio :value="2">紧急</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="内容" prop="content">
        <el-input
          v-model="formData.content"
          type="textarea"
          :rows="8"
          placeholder="请输入公告内容"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formDialogVisible = false">取消</el-button>
      <el-button :loading="submitting" @click="submitForm(false)">保存草稿</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm(true)">保存并发布</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="detailDialogVisible"
    title="公告详情"
    width="640px"
    append-to-body
    destroy-on-close
    @closed="detailItem = null"
  >
    <div v-if="detailItem" class="announcement-detail">
      <h3 class="announcement-detail__title">{{ detailItem.title }}</h3>
      <div class="announcement-detail__meta">
        <el-tag :type="priorityTagType(detailItem.priority)" size="small" effect="plain">
          {{ priorityLabel(detailItem.priority) }}
        </el-tag>
        <el-tag :type="statusTagType(detailItem.status)" size="small">
          {{ statusLabel(detailItem.status) }}
        </el-tag>
        <span v-if="detailItem.publisherName">发布人：{{ detailItem.publisherName }}</span>
        <span v-if="detailItem.publishTime">发布时间：{{ formatDateTime(detailItem.publishTime) }}</span>
      </div>
      <div class="announcement-detail__content">{{ detailItem.content }}</div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Notification, Plus, Refresh, Search, List, CircleCheck } from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import {
  createAnnouncementApi,
  deleteAnnouncementApi,
  getAnnouncementPageApi,
  publishAnnouncementApi,
  revokeAnnouncementApi,
  updateAnnouncementApi,
} from '@/api/announcement'
import type { AnnouncementQueryDTO, AnnouncementVO } from '@/types/announcement'
import {
  ANNOUNCEMENT_PRIORITY_LABEL,
  ANNOUNCEMENT_STATUS_LABEL,
  AnnouncementStatus,
} from '@/types/announcement'
import { formatDateTime } from '@/utils/format'
import { PERMISSIONS } from '@/constants/permissions'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<AnnouncementVO[]>([])
const total = ref(0)
const publishedCount = ref(0)

const queryParams = reactive<Required<Pick<AnnouncementQueryDTO, 'pageNum' | 'pageSize'>> & AnnouncementQueryDTO>({
  pageNum: 1,
  pageSize: 20,
  title: '',
  priority: undefined,
  status: undefined,
})

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '草稿', value: AnnouncementStatus.DRAFT },
  { label: '已发布', value: AnnouncementStatus.PUBLISHED },
  { label: '已撤回', value: AnnouncementStatus.REVOKED },
]

const activeFilterCount = computed(() => {
  let count = 0
  if (queryParams.title) count += 1
  if (queryParams.priority != null) count += 1
  return count
})

const heroMetrics = computed(() => [
  { key: 'total', label: '公告总数', value: total.value, icon: List, accent: 'primary' as const },
  { key: 'published', label: '已发布', value: publishedCount.value, icon: CircleCheck, accent: 'success' as const },
])

const formDialogVisible = ref(false)
const formDialogTitle = ref('新建公告')
const editingId = ref<number | string | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive({
  title: '',
  content: '',
  priority: 0,
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

const detailDialogVisible = ref(false)
const detailItem = ref<AnnouncementVO | null>(null)

function priorityLabel(value?: number) {
  return ANNOUNCEMENT_PRIORITY_LABEL[value ?? 0] ?? '普通'
}

function statusLabel(value?: number) {
  return ANNOUNCEMENT_STATUS_LABEL[value ?? 0] ?? '未知'
}

function priorityTagType(value?: number): 'info' | 'warning' | 'danger' {
  if (value === 2) return 'danger'
  if (value === 1) return 'warning'
  return 'info'
}

function statusTagType(value?: number): 'info' | 'success' | 'warning' {
  if (value === AnnouncementStatus.PUBLISHED) return 'success'
  if (value === AnnouncementStatus.REVOKED) return 'warning'
  return 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const pageData = await getAnnouncementPageApi({ ...queryParams })
    tableData.value = pageData.records ?? []
    total.value = pageData.total ?? 0
    publishedCount.value = tableData.value.filter(
      (item) => item.status === AnnouncementStatus.PUBLISHED,
    ).length
  } catch {
    // 错误由拦截器处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.title = ''
  queryParams.priority = undefined
  queryParams.status = undefined
  handleSearch()
}

function applyStatusFilter(value: number | undefined) {
  queryParams.status = value
  handleSearch()
}

function resetForm() {
  editingId.value = null
  formData.title = ''
  formData.content = ''
  formData.priority = 0
  formRef.value?.clearValidate()
}

function openCreateDialog() {
  formDialogTitle.value = '新建公告'
  resetForm()
  formDialogVisible.value = true
}

function openEditDialog(row: AnnouncementVO) {
  formDialogTitle.value =
    row.status === AnnouncementStatus.REVOKED ? '编辑公告（已撤回）' : '编辑公告'
  editingId.value = row.id
  formData.title = row.title ?? ''
  formData.content = row.content ?? ''
  formData.priority = row.priority ?? 0
  formDialogVisible.value = true
}

function openDetailDialog(row: AnnouncementVO) {
  detailItem.value = { ...row }
  detailDialogVisible.value = true
}

async function submitForm(publishImmediately: boolean) {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value != null) {
      await updateAnnouncementApi({
        id: editingId.value,
        title: formData.title.trim(),
        content: formData.content.trim(),
        priority: formData.priority,
      })
      if (publishImmediately) {
        await publishAnnouncementApi(editingId.value)
        ElMessage.success('公告已更新并发布')
      } else {
        ElMessage.success('公告已更新')
      }
    } else {
      await createAnnouncementApi({
        title: formData.title.trim(),
        content: formData.content.trim(),
        priority: formData.priority,
        publishImmediately,
      })
      ElMessage.success(publishImmediately ? '公告已发布' : '草稿已保存')
    }
    formDialogVisible.value = false
    fetchList()
  } catch {
    // 错误由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handlePublish(row: AnnouncementVO) {
  const isRePublish = row.status === AnnouncementStatus.REVOKED
  try {
    await ElMessageBox.confirm(
      isRePublish ? `确定重新发布公告「${row.title}」吗？` : `确定发布公告「${row.title}」吗？`,
      isRePublish ? '重新发布确认' : '发布确认',
      { type: 'info' },
    )
    await publishAnnouncementApi(row.id)
    ElMessage.success(isRePublish ? '公告已重新发布' : '公告已发布')
    fetchList()
  } catch {
    // 取消或错误
  }
}

async function handleRevoke(row: AnnouncementVO) {
  try {
    await ElMessageBox.confirm(`确定撤回公告「${row.title}」吗？`, '撤回确认', { type: 'warning' })
    await revokeAnnouncementApi(row.id)
    ElMessage.success('公告已撤回')
    fetchList()
  } catch {
    // 取消或错误
  }
}

async function handleDelete(row: AnnouncementVO) {
  try {
    await ElMessageBox.confirm(`确定删除草稿「${row.title}」吗？`, '删除确认', { type: 'warning' })
    await deleteAnnouncementApi(row.id)
    ElMessage.success('公告已删除')
    fetchList()
  } catch {
    // 取消或错误
  }
}

useRouteActivate(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.announcement-detail {
  &__title {
    margin: 0 0 12px;
    font-size: 18px;
    color: #303133;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 16px;
    margin-bottom: 16px;
    font-size: 13px;
    color: #909399;
  }

  &__content {
    white-space: pre-wrap;
    line-height: 1.7;
    color: #606266;
    font-size: 14px;
  }
}
</style>
