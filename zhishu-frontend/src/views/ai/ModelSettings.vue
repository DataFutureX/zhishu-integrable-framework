<template>
  <ListPageShell
    :loading="loading"
    hero-title="模型设置"
    hero-eyebrow="AI 配置"
    :hero-eyebrow-icon="Monitor"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      共 <strong>{{ providerList.length }}</strong> 个模型设置
    </template>
    <template #heroActions>
      <el-button type="primary" size="small" :icon="Plus" @click="handleCreate">新建模型</el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </template>

    <template #strip>
      <StatusFilterStrip
        :model-value="statusFilter"
        :options="statusFilterOptions"
        @update:model-value="statusFilter = $event"
      />
    </template>

    <template #toolbar>
      <ListToolbar title="模型列表">
        <template #hint>管理 AI 模型供应商配置，支持多模型接入</template>
      </ListToolbar>
    </template>

    <el-table :data="filteredList" class="modern-table" empty-text="暂无模型设置">
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="name-cell">
            <span
              class="status-dot"
              :class="row.status === 'ENABLED' ? 'status-dot--enabled' : 'status-dot--disabled'"
            />
            <span class="name-cell__text">{{ row.name }}</span>
            <el-tag v-if="row.isDefault" size="small" type="success" class="name-cell__tag">默认</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="providerKey" label="标识" width="110" show-overflow-tooltip />
      <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
      <el-table-column prop="chatModel" label="对话模型" width="140" show-overflow-tooltip />
      <el-table-column prop="embeddingModel" label="嵌入模型" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.embeddingModel || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="生成参数" width="160">
        <template #default="{ row }">
          <span class="params-text">
            T={{ row.temperature }} / {{ row.maxTokens }} / {{ row.topP }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            link type="primary" size="small" :icon="Edit"
            @click="handleEdit(row as ModelProviderVO)"
          >
            编辑
          </el-button>
          <el-button
            link type="primary" size="small" :icon="Connection"
            :loading="testingId === row.id"
            @click="handleTest(row as ModelProviderVO)"
          >
            测试
          </el-button>
          <el-popconfirm
            title="确定删除此模型设置？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button
                link type="danger" size="small" :icon="Delete"
                :disabled="row.isDefault"
              >
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <template #extra>
      <!-- 新建/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑模型设置' : '新建模型设置'"
        width="600px"
        :close-on-click-modal="false"
      >
        <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          label-width="100px"
          class="provider-form"
        >
          <el-form-item label="名称" prop="name">
            <el-input v-model="formData.name" placeholder="如：通义千问、DeepSeek" maxlength="50" />
          </el-form-item>
          <el-form-item v-if="!isEdit" label="标识" prop="providerKey">
            <el-input v-model="formData.providerKey" placeholder="如：dashscope、deepseek" maxlength="50" />
          </el-form-item>
          <el-form-item label="Base URL" prop="baseUrl">
            <el-input v-model="formData.baseUrl" placeholder="https://api.example.com/v1" />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model="formData.apiKey"
              type="password"
              show-password
              :placeholder="isEdit ? '留空则不修改' : '请输入 API Key'"
            />
            <div v-if="isEdit && formData.apiKeyMasked" class="form-hint">
              当前已配置：{{ formData.apiKeyMasked }}
            </div>
          </el-form-item>
          <el-form-item label="对话模型" prop="chatModel">
            <el-input v-model="formData.chatModel" placeholder="如：qwen-plus、deepseek-chat" />
          </el-form-item>
          <el-form-item label="嵌入模型" prop="embeddingModel">
            <el-input v-model="formData.embeddingModel" placeholder="如：text-embedding-v3（可选）" />
          </el-form-item>
          <el-divider content-position="left">生成参数</el-divider>
          <el-form-item label="Temperature" prop="temperature">
            <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" show-input />
          </el-form-item>
          <el-form-item label="Max Tokens" prop="maxTokens">
            <el-input-number v-model="formData.maxTokens" :min="256" :max="8192" :step="256" />
          </el-form-item>
          <el-form-item label="Top P" prop="topP">
            <el-slider v-model="formData.topP" :min="0" :max="1" :step="0.05" show-input />
          </el-form-item>
          <el-form-item v-if="isEdit" label="状态" prop="status">
            <el-switch
              v-model="formData.status"
              active-value="ENABLED"
              inactive-value="DISABLED"
              active-text="启用"
              inactive-text="禁用"
            />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
        </template>
      </el-dialog>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIModelSettings' })

import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus,
  Refresh,
  Edit,
  Delete,
  Monitor,
  Connection,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import type { ModelProviderVO, ModelProviderCreateDTO, ModelProviderUpdateDTO } from '@/types/modelProvider'
import {
  listModelProviders,
  getModelProvider,
  createModelProvider,
  updateModelProvider,
  deleteModelProvider,
  testModelProviderConnection,
} from '@/api/ai'
import { useRouteActivate } from '@/composables/useRouteActivate'

const loading = ref(false)
const providerList = ref<ModelProviderVO[]>([])

const statusFilter = ref<string | undefined>(undefined)

const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const testingId = ref<number | null>(null)

const formRef = ref<FormInstance>()
const formData = reactive<{
  name: string
  providerKey: string
  baseUrl: string
  apiKey: string
  apiKeyMasked?: string | null
  chatModel: string
  embeddingModel: string
  temperature: number
  maxTokens: number
  topP: number
  status: string
  remark: string
}>({
  name: '',
  providerKey: '',
  baseUrl: '',
  apiKey: '',
  apiKeyMasked: null,
  chatModel: '',
  embeddingModel: '',
  temperature: 0.7,
  maxTokens: 2000,
  topP: 0.9,
  status: 'ENABLED',
  remark: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  providerKey: [{ required: true, message: '请输入标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  chatModel: [{ required: true, message: '请输入对话模型', trigger: 'blur' }],
}

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 'ENABLED', dot: 'enabled' as const },
  { label: '禁用', value: 'DISABLED', dot: 'disabled' as const },
]

const filteredList = computed(() => {
  if (statusFilter.value == null) return providerList.value
  return providerList.value.filter((p) => p.status === statusFilter.value)
})

const heroMetrics = computed(() => [
  { key: 'total', label: '模型总数', value: providerList.value.length, icon: Monitor, accent: 'primary' as const },
])

async function fetchList() {
  loading.value = true
  try {
    providerList.value = await listModelProviders()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '获取模型列表失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  formData.name = ''
  formData.providerKey = ''
  formData.baseUrl = ''
  formData.apiKey = ''
  formData.apiKeyMasked = null
  formData.chatModel = ''
  formData.embeddingModel = ''
  formData.temperature = 0.7
  formData.maxTokens = 2000
  formData.topP = 0.9
  formData.status = 'ENABLED'
  formData.remark = ''
}

function handleCreate() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: ModelProviderVO) {
  isEdit.value = true
  editingId.value = row.id
  try {
    const detail = await getModelProvider(row.id)
    formData.name = detail.name
    formData.providerKey = detail.providerKey
    formData.baseUrl = detail.baseUrl
    formData.apiKey = ''
    formData.apiKeyMasked = detail.apiKeyMasked
    formData.chatModel = detail.chatModel
    formData.embeddingModel = detail.embeddingModel ?? ''
    formData.temperature = detail.temperature
    formData.maxTokens = detail.maxTokens
    formData.topP = detail.topP
    formData.status = detail.status
    formData.remark = detail.remark ?? ''
    dialogVisible.value = true
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '获取详情失败'
    ElMessage.error(msg)
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      const data: ModelProviderUpdateDTO = {
        name: formData.name,
        baseUrl: formData.baseUrl,
        chatModel: formData.chatModel,
        embeddingModel: formData.embeddingModel || null,
        temperature: formData.temperature,
        maxTokens: formData.maxTokens,
        topP: formData.topP,
        status: formData.status,
        remark: formData.remark || null,
      }
      if (formData.apiKey) {
        data.apiKey = formData.apiKey
      }
      await updateModelProvider(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      const data: ModelProviderCreateDTO = {
        name: formData.name,
        providerKey: formData.providerKey,
        baseUrl: formData.baseUrl,
        apiKey: formData.apiKey || null,
        chatModel: formData.chatModel,
        embeddingModel: formData.embeddingModel || null,
        temperature: formData.temperature,
        maxTokens: formData.maxTokens,
        topP: formData.topP,
        remark: formData.remark || null,
      }
      await createModelProvider(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '操作失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteModelProvider(id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '删除失败'
    ElMessage.error(msg)
  }
}

async function handleTest(row: ModelProviderVO) {
  testingId.value = row.id
  try {
    const message = await testModelProviderConnection(row.id)
    ElMessage.success(message || '连通性测试成功')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '测试失败'
    ElMessage.error(msg)
  } finally {
    testingId.value = null
  }
}

useRouteActivate(fetchList)
onMounted(fetchList)
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  &__text {
    font-weight: 500;
    color: var(--app-text-primary);
  }

  &__tag {
    flex-shrink: 0;
  }
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--enabled {
    background: $success-color;
  }

  &--disabled {
    background: $text-placeholder;
  }
}

.params-text {
  font-size: 12px;
  color: var(--app-text-regular);
  font-family: var(--app-font-mono, monospace);
}

.provider-form {
  padding: 0 4px;
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
