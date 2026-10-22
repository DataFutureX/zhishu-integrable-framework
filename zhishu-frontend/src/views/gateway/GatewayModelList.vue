<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">模型设置</span>
          <div class="gateway-page__actions">
            <el-select
              v-model="selectedProviderId"
              placeholder="选择供应商"
              size="small"
              style="width: 200px"
              @change="loadModels"
            >
              <el-option
                v-for="provider in providers"
                :key="provider.id"
                :label="provider.isDefault ? `${provider.name}（默认）` : provider.name"
                :value="provider.id"
              />
            </el-select>
            <el-radio-group v-model="modelType" size="small" @change="loadModels">
              <el-radio-button label="CHAT">对话</el-radio-button>
              <el-radio-button label="EMBEDDING">向量</el-radio-button>
            </el-radio-group>
            <el-button size="small" :icon="Refresh" @click="loadModels">刷新</el-button>
            <el-button size="small" :icon="Download" @click="openImport">
              从上游导入
            </el-button>
            <el-button type="primary" size="small" :icon="Plus" @click="openCreate">
              新建模型
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="gateway-page__hint"
        title="选择供应商后添加模型：对话模型对外暴露 model_id；向量模型仅供知识库使用，不进入 /v1/models。"
      />

      <el-table v-loading="loading" :data="models" class="modern-table" empty-text="该供应商下暂无模型">
        <el-table-column prop="modelId" label="模型 ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="displayName" label="展示名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="upstreamModel" label="上游模型" min-width="160" show-overflow-tooltip />
        <el-table-column label="参数" width="150">
          <template #default="{ row }">
            <span class="gateway-page__meta">
              T={{ row.temperature ?? '—' }} / P={{ row.topP ?? '—' }} / {{ row.maxTokens ?? '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="能力" width="170">
          <template #default="{ row }">
            <span class="gateway-page__meta">
              ctx={{ row.contextWindow ?? '—' }} · out={{ row.maxOutputTokens ?? '—' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="success" class="mr-4">默认</el-tag>
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row as GatewayModelVO)">编辑</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :loading="testingId === row.id"
              @click="handleTest(row as GatewayModelVO)"
            >
              测试
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="row.isDefault"
              @click="handleDefault(row as GatewayModelVO)"
            >
              设默认
            </el-button>
            <el-button link type="primary" size="small" @click="handleToggle(row as GatewayModelVO)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as GatewayModelVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新建模型'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="供应商" prop="providerId">
          <el-select v-model="form.providerId" filterable placeholder="请选择供应商" style="width: 100%">
            <el-option
              v-for="provider in providers"
              :key="provider.id"
              :label="provider.isDefault ? `${provider.name}（默认）` : provider.name"
              :value="provider.id"
            />
          </el-select>
          <span class="gateway-page__meta">模型必须归属一个供应商；未配置时请先去「供应商管理」新建</span>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-radio-group v-model="form.modelType" :disabled="!!editingId">
            <el-radio-button label="CHAT">对话</el-radio-button>
            <el-radio-button label="EMBEDDING">向量</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="模型 ID" prop="modelId">
          <el-input v-model="form.modelId" :disabled="!!editingId" placeholder="如 gw-dashscope-qwen-plus" />
        </el-form-item>
        <el-form-item label="展示名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="如 通义千问 Plus" />
        </el-form-item>
        <el-form-item label="上游模型名" prop="upstreamModel">
          <el-input v-model="form.upstreamModel" placeholder="供应商真实模型名，如 qwen-plus" />
        </el-form-item>
        <el-form-item label="上下文窗口">
          <el-input-number v-model="form.contextWindow" :min="1" :step="1024" controls-position="right" />
        </el-form-item>
        <el-form-item label="输出上限">
          <el-input-number v-model="form.maxOutputTokens" :min="1" :step="1024" controls-position="right" />
        </el-form-item>
        <el-form-item label="默认温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
          <span class="gateway-page__meta">请求未携带 temperature 时使用</span>
        </el-form-item>
        <el-form-item label="默认 Top P">
          <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
        <el-form-item label="默认 max_tokens">
          <el-input-number v-model="form.maxTokens" :min="1" :step="256" controls-position="right" />
        </el-form-item>
        <el-form-item label="输入模态">
          <el-input v-model="form.inputModalities" placeholder="逗号分隔，如 text,image" />
        </el-form-item>
        <el-form-item label="system 策略">
          <el-select v-model="form.systemPromptUpdate" style="width: 100%">
            <el-option label="in-history" value="in-history" />
            <el-option label="leading-only" value="leading-only" />
          </el-select>
        </el-form-item>
        <el-form-item label="reasoning 档位">
          <el-input v-model="form.reasoningEfforts" placeholder="逗号分隔，如 low,medium,high" />
        </el-form-item>
        <el-form-item label="默认档位">
          <el-input v-model="form.defaultReasoningEffort" placeholder="如 high" />
        </el-form-item>
        <el-form-item label="客户端门禁">
          <el-input
            v-model="form.clientRequirements"
            type="textarea"
            :rows="2"
            placeholder='{"LetsWork":{"min_version":"0.2.1"}}'
          />
        </el-form-item>
        <el-form-item label="透传参数">
          <el-input v-model="form.extraParams" type="textarea" :rows="2" placeholder='{"enable_thinking":true}' />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="从上游导入模型" width="640px">
      <div class="import-toolbar">
        <el-select
          v-model="importProviderId"
          placeholder="请选择供应商"
          size="small"
          style="width: 180px"
          @change="handleImportProviderChange"
        >
          <el-option
            v-for="provider in providers"
            :key="provider.id"
            :label="provider.isDefault ? `${provider.name}（默认）` : provider.name"
            :value="provider.id"
          />
        </el-select>
        <el-select v-model="importType" size="small" style="width: 120px" @change="handleImportProviderChange">
          <el-option label="对话" value="CHAT" />
          <el-option label="向量" value="EMBEDDING" />
        </el-select>
        <el-button size="small" :loading="loadingUpstream" @click="loadUpstreamModels">拉取模型列表</el-button>
        <span class="gateway-page__meta">已选 {{ importSelection.length }} 个</span>
      </div>
      <el-checkbox-group v-model="importSelection" class="import-list">
        <el-checkbox v-for="item in upstreamModels" :key="item" :label="item" :value="item" />
      </el-checkbox-group>
      <el-empty v-if="!loadingUpstream && upstreamModels.length === 0" description="点击「拉取模型列表」" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="importSelection.length === 0" @click="handleImport">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Download, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  batchImportGatewayModelsApi,
  changeGatewayModelStatusApi,
  createGatewayModelApi,
  deleteGatewayModelApi,
  listGatewayModelsApi,
  listGatewayProvidersApi,
  listUpstreamModelsApi,
  setDefaultGatewayModelApi,
  testGatewayModelApi,
  updateGatewayModelApi,
} from '@/api/gateway'
import type { GatewayModelType, GatewayModelUpsertDTO, GatewayModelVO, GatewayProviderVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayModelList' })

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const loadingUpstream = ref(false)
const testingId = ref<number | null>(null)

const providers = ref<GatewayProviderVO[]>([])
const models = ref<GatewayModelVO[]>([])
const selectedProviderId = ref<number | null>(null)
const modelType = ref<GatewayModelType>('CHAT')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const importVisible = ref(false)
const importType = ref<GatewayModelType>('CHAT')
const importProviderId = ref<number | null>(null)
const importSelection = ref<string[]>([])
const upstreamModels = ref<string[]>([])

const form = reactive<GatewayModelUpsertDTO>({
  modelId: '',
  displayName: '',
  providerId: 0,
  upstreamModel: '',
  modelType: 'CHAT',
  contextWindow: null,
  maxOutputTokens: null,
  inputModalities: 'text',
  systemPromptUpdate: 'in-history',
  reasoningEfforts: null,
  defaultReasoningEffort: null,
  clientRequirements: null,
  extraParams: null,
  temperature: null,
  topP: null,
  maxTokens: null,
  isDefault: false,
  status: 'ENABLED',
  sortOrder: 0,
  remark: null,
})

const rules: FormRules = {
  providerId: [
    { required: true, message: '请先选择供应商', trigger: 'change' },
    { type: 'number', min: 1, message: '请先选择供应商', trigger: 'change' },
  ],
  modelId: [
    { required: true, message: '请填写模型 ID', trigger: 'blur' },
    {
      pattern: /^[a-z0-9][a-z0-9.-]{0,63}$/,
      message: '仅小写字母、数字、点与连字符，且以字母或数字开头',
      trigger: 'blur',
    },
  ],
  displayName: [{ required: true, message: '请填写展示名', trigger: 'blur' }],
  upstreamModel: [{ required: true, message: '请填写上游模型名', trigger: 'blur' }],
}

async function loadProviders() {
  providers.value = await listGatewayProvidersApi()
  if (!selectedProviderId.value && providers.value.length > 0) {
    const defaultProvider = providers.value.find((p) => p.isDefault) ?? providers.value[0]
    selectedProviderId.value = defaultProvider.id
  }
}

async function loadModels() {
  if (!selectedProviderId.value) {
    models.value = []
    return
  }
  loading.value = true
  try {
    models.value = await listGatewayModelsApi({
      type: modelType.value,
      providerId: selectedProviderId.value,
    })
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.modelId = ''
  form.displayName = ''
  form.providerId = selectedProviderId.value ?? 0
  form.upstreamModel = ''
  form.modelType = modelType.value
  form.contextWindow = null
  form.maxOutputTokens = null
  form.inputModalities = 'text'
  form.systemPromptUpdate = 'in-history'
  form.reasoningEfforts = null
  form.defaultReasoningEffort = null
  form.clientRequirements = null
  form.extraParams = null
  form.temperature = null
  form.topP = null
  form.maxTokens = null
  form.isDefault = false
  form.status = 'ENABLED'
  form.sortOrder = 0
  form.remark = null
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: GatewayModelVO) {
  editingId.value = row.id
  form.modelId = row.modelId
  form.displayName = row.displayName
  form.providerId = row.providerId
  form.upstreamModel = row.upstreamModel
  form.modelType = (row.modelType as GatewayModelType) ?? 'CHAT'
  form.contextWindow = row.contextWindow ?? null
  form.maxOutputTokens = row.maxOutputTokens ?? null
  form.inputModalities = row.inputModalities ?? 'text'
  form.systemPromptUpdate = row.systemPromptUpdate ?? 'in-history'
  form.reasoningEfforts = row.reasoningEfforts ?? null
  form.defaultReasoningEffort = row.defaultReasoningEffort ?? null
  form.clientRequirements = row.clientRequirements ?? null
  form.extraParams = row.extraParams ?? null
  form.temperature = row.temperature ?? null
  form.topP = row.topP ?? null
  form.maxTokens = row.maxTokens ?? null
  form.isDefault = Boolean(row.isDefault)
  form.status = row.status
  form.sortOrder = row.sortOrder ?? 0
  form.remark = row.remark ?? null
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateGatewayModelApi(editingId.value, { ...form })
      ElMessage.success('已保存')
    } else {
      await createGatewayModelApi({ ...form })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    // 列表跟随刚刚保存的供应商与类型，避免新建后看不到
    selectedProviderId.value = form.providerId
    modelType.value = (form.modelType as GatewayModelType) ?? modelType.value
    await loadModels()
  } finally {
    saving.value = false
  }
}

async function handleTest(row: GatewayModelVO) {
  testingId.value = row.id
  try {
    const result = await testGatewayModelApi(row.id)
    ElMessage.info(result || '测试完成')
  } finally {
    testingId.value = null
  }
}

async function handleDefault(row: GatewayModelVO) {
  await setDefaultGatewayModelApi(row.id)
  ElMessage.success('已设为默认模型')
  await loadModels()
}

async function handleToggle(row: GatewayModelVO) {
  await changeGatewayModelStatusApi(row.id, row.status !== 'ENABLED')
  await loadModels()
}

async function handleDelete(row: GatewayModelVO) {
  await ElMessageBox.confirm(`确认删除模型 ${row.modelId}？`, '提示', { type: 'warning' })
  await deleteGatewayModelApi(row.id)
  ElMessage.success('已删除')
  await loadModels()
}

function openImport() {
  importProviderId.value = selectedProviderId.value
  importType.value = modelType.value
  importSelection.value = []
  upstreamModels.value = []
  importVisible.value = true
}

function handleImportProviderChange() {
  importSelection.value = []
  upstreamModels.value = []
}

async function loadUpstreamModels() {
  if (!importProviderId.value) {
    ElMessage.warning('请先选择供应商')
    return
  }
  loadingUpstream.value = true
  try {
    upstreamModels.value = await listUpstreamModelsApi(importProviderId.value)
    if (upstreamModels.value.length === 0) {
      ElMessage.warning('上游未返回模型列表')
    }
  } finally {
    loadingUpstream.value = false
  }
}

async function handleImport() {
  if (!importProviderId.value) {
    ElMessage.warning('请先选择供应商')
    return
  }
  importing.value = true
  try {
    const created = await batchImportGatewayModelsApi({
      providerId: importProviderId.value,
      modelType: importType.value,
      upstreamModels: importSelection.value,
    })
    ElMessage.success(`已导入 ${created.length} 个模型`)
    importVisible.value = false
    selectedProviderId.value = importProviderId.value
    modelType.value = importType.value
    await loadModels()
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  await loadProviders()
  await loadModels()
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

.gateway-page__hint {
  margin-bottom: 12px;
}

.gateway-page__meta {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mr-4 {
  margin-right: 4px;
}

.import-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.import-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
  max-height: 320px;
  overflow-y: auto;
}
</style>
