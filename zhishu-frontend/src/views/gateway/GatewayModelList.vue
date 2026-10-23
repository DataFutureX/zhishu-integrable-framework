<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">模型设置</span>
          <div class="gateway-page__actions">
            <el-select
              v-model="selectedProviderId"
              placeholder="全部供应商"
              size="small"
              style="width: 200px"
              @change="loadModels"
            >
              <el-option label="全部供应商" :value="ALL_PROVIDERS" />
              <el-option
                v-for="provider in providers"
                :key="provider.id"
                :label="provider.isDefault ? `${provider.name}（默认）` : provider.name"
                :value="provider.id"
              />
            </el-select>
            <el-radio-group v-model="modelType" size="small" @change="loadModels">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="CHAT">对话</el-radio-button>
              <el-radio-button label="EMBEDDING">向量</el-radio-button>
            </el-radio-group>
            <el-button size="small" :icon="Refresh" @click="loadModels">刷新</el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              size="small"
              :icon="Download"
              @click="openImport"
            >
              从上游导入
            </el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              type="primary"
              size="small"
              :icon="Plus"
              @click="openCreate"
            >
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
        title="默认展示全部供应商的模型，可按供应商与类型筛选。新建或导入时必须指定供应商；对话模型对外暴露 model_id，向量模型仅供知识库使用，不进入 /v1/models。"
      />

      <el-table v-loading="loading" :data="models" class="modern-table" empty-text="暂无模型">
        <el-table-column prop="modelId" label="模型 ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="displayName" label="展示名" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="success" class="mr-4">默认</el-tag>
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="供应商" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.providerName || row.providerKey || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelType === 'EMBEDDING' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ row.modelType === 'EMBEDDING' ? '向量' : '对话' }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              link
              type="primary"
              size="small"
              @click="openEdit(row as GatewayModelVO)"
            >
              编辑
            </el-button>
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
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              link
              type="primary"
              size="small"
              :disabled="row.isDefault"
              @click="handleDefault(row as GatewayModelVO)"
            >
              设默认
            </el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              link
              type="primary"
              size="small"
              @click="handleToggle(row as GatewayModelVO)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
              link
              type="danger"
              size="small"
              @click="handleDelete(row as GatewayModelVO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新建模型'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="供应商" prop="providerId">
          <el-select
            v-model="form.providerId"
            filterable
            placeholder="请选择供应商"
            style="width: 100%"
            @change="handleFormProviderChange"
          >
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
          <el-radio-group v-model="form.modelType" :disabled="!!editingId" @change="handleModelTypeChange">
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
          <div class="upstream-picker">
            <el-select
              v-model="form.upstreamModel"
              filterable
              allow-create
              default-first-option
              :loading="loadingDialogUpstream"
              placeholder="从供应商已接入模型中选择，也可直接输入"
              style="flex: 1"
              @change="handleUpstreamModelChange"
            >
              <el-option v-for="item in dialogUpstreamModels" :key="item.id" :label="item.id" :value="item.id">
                <span>{{ item.name && item.name !== item.id ? `${item.id} · ${item.name}` : item.id }}</span>
                <span class="gateway-page__meta" style="float: right; margin-left: 12px">
                  {{ item.contextWindow ?? '?' }} / {{ item.maxOutputTokens ?? '?' }}
                </span>
              </el-option>
            </el-select>
            <el-button :loading="loadingDialogUpstream" @click="reloadDialogUpstream">重新拉取</el-button>
          </div>
          <span v-if="dialogUpstreamError" class="gateway-page__meta upstream-picker__error">
            未能自动拉取上游模型（{{ dialogUpstreamError }}），可手工输入模型名，或先到「供应商管理」确认 Base URL 与 API Key
          </span>
          <span v-else-if="!loadingDialogUpstream && dialogUpstreamModels.length === 0" class="gateway-page__meta">
            该供应商未返回可用模型，可直接输入上游模型名
          </span>
          <span v-else class="gateway-page__meta">选中后自动回填展示名、上下文窗口、输出上限、模态与推理档位</span>
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
          <span class="gateway-page__meta">请求未携带 max_tokens 时使用；向量模型不需要</span>
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
      <el-table
        v-loading="loadingUpstream"
        :data="upstreamModels"
        row-key="id"
        max-height="360"
        class="modern-table"
        @selection-change="(rows: GatewayUpstreamModel[]) => (importSelection = rows)"
      >
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column prop="id" label="上游模型" min-width="190" show-overflow-tooltip />
        <el-table-column prop="name" label="展示名" min-width="150" show-overflow-tooltip />
        <el-table-column label="ctx / out" width="130">
          <template #default="{ row }">
            <span class="gateway-page__meta">{{ row.contextWindow ?? '—' }} / {{ row.maxOutputTokens ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="模态" width="110">
          <template #default="{ row }">{{ (row.inputModalities || []).join(', ') || '—' }}</template>
        </el-table-column>
        <el-table-column label="推理档位" min-width="170">
          <template #default="{ row }">
            <span class="gateway-page__meta">
              {{ (row.reasoningEfforts || []).join(', ') || '—' }}
              <template v-if="row.defaultReasoningEffort">（默认 {{ row.defaultReasoningEffort }}）</template>
            </span>
          </template>
        </el-table-column>
      </el-table>
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
import { PERMISSIONS } from '@/constants/permissions'
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
import type {
  GatewayModelType,
  GatewayModelUpsertDTO,
  GatewayModelVO,
  GatewayProviderVO,
  GatewayUpstreamModel,
} from '@/types/modelGateway'

defineOptions({ name: 'GatewayModelList' })

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const loadingUpstream = ref(false)
const testingId = ref<number | null>(null)

const providers = ref<GatewayProviderVO[]>([])
const models = ref<GatewayModelVO[]>([])
// 供应商筛选哨兵值：0 = 全部供应商
const ALL_PROVIDERS = 0
const selectedProviderId = ref<number>(ALL_PROVIDERS)
// '' = 全部类型
const modelType = ref<GatewayModelType | ''>('')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const importVisible = ref(false)
const importType = ref<GatewayModelType>('CHAT')
const importProviderId = ref<number | null>(null)
const importSelection = ref<GatewayUpstreamModel[]>([])
const upstreamModels = ref<GatewayUpstreamModel[]>([])

// 弹窗内：选定供应商后自动拉出的上游模型（按 providerId 缓存，避免反复跳外网）
const dialogUpstreamModels = ref<GatewayUpstreamModel[]>([])
const loadingDialogUpstream = ref(false)
const dialogUpstreamError = ref('')
const upstreamCache = new Map<string, GatewayUpstreamModel[]>()
// 记录自动回填的展示名，便于切换上游模型时跟随更新而不覆盖用户自定义名
const autoDisplayName = ref('')

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
    {
      // 注意：请求层为防精度丢失会把超过安全整数范围的雪花 ID 转为字符串（见 utils/request.ts 的
      // preprocessLargeIntegers），所以不能用 type:'number' 规则，否则选了供应商仍会报「请先选择供应商」。
      validator: (_rule, value, callback) => {
        const filled = value !== null && value !== undefined && value !== '' && Number(value) > 0
        if (filled) {
          callback()
        } else {
          callback(new Error('请先选择供应商'))
        }
      },
      trigger: ['change', 'blur'],
    },
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
  upstreamModel: [{ required: true, message: '请选择或填写上游模型名', trigger: ['change', 'blur'] }],
}

// 新建时的采样参数默认值（与现有云端模型一致，保存前可微调）；向量模型不适用
const DEFAULT_CHAT_PARAMS = { temperature: 0.7, topP: 0.9, maxTokens: 2000 }

async function loadProviders() {
  providers.value = await listGatewayProvidersApi()
}

/**
 * 拉取（并缓存）指定供应商的上游模型与元数据。
 *
 * @param providerId 供应商 ID
 * @param force      是否忽略缓存强制重拉
 */
async function loadDialogUpstreamModels(providerId?: number | string | null, force = false) {
  dialogUpstreamError.value = ''
  const cacheKey = providerId == null ? '' : String(providerId)
  if (!cacheKey || cacheKey === '0') {
    dialogUpstreamModels.value = []
    return
  }
  const cached = upstreamCache.get(cacheKey)
  if (cached && !force) {
    dialogUpstreamModels.value = cached
    return
  }
  loadingDialogUpstream.value = true
  try {
    const list = await listUpstreamModelsApi(providerId as number)
    upstreamCache.set(cacheKey, list)
    dialogUpstreamModels.value = list
  } catch (error) {
    upstreamCache.delete(cacheKey)
    dialogUpstreamModels.value = []
    dialogUpstreamError.value = (error as Error)?.message || '拉取失败'
  } finally {
    loadingDialogUpstream.value = false
  }
}

function reloadDialogUpstream() {
  void loadDialogUpstreamModels(form.providerId, true)
}

/** 换供应商：模型列表不通用，清掉已选上游名并重拉 */
function handleFormProviderChange() {
  form.upstreamModel = ''
  void loadDialogUpstreamModels(form.providerId)
}

function toSlug(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9.]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^[^a-z0-9]+/, '')
    .replace(/-+$/g, '')
}

/** 按后端批量导入同样的规则建议 model_id：gw-<provider>-<model> */
function suggestModelId(upstreamModel: string): string | null {
  const provider = providers.value.find((item) => String(item.id) === String(form.providerId))
  if (!provider || !upstreamModel) {
    return null
  }
  const raw = `gw-${toSlug(provider.providerKey || provider.name)}-${toSlug(upstreamModel)}`
  const id = raw.length > 64 ? raw.slice(0, 64).replace(/-+$/g, '') : raw
  return /^[a-z0-9][a-z0-9.-]{0,63}$/.test(id) ? id : null
}

/** 选中上游模型后回填 model_id 建议与能力元数据 */
function handleUpstreamModelChange(value: string) {
  const modelIdSuggested = suggestModelId(value)
  if (!editingId.value && modelIdSuggested && (!form.modelId.trim() || form.modelId.startsWith('gw-'))) {
    form.modelId = modelIdSuggested
  }
  const item = dialogUpstreamModels.value.find((model) => model.id === value)
  if (!item) {
    return
  }
  if (!form.displayName.trim() || form.displayName === autoDisplayName.value) {
    form.displayName = item.name || item.id
    autoDisplayName.value = form.displayName
  }
  // 上游没给的字段保持现值，不抹除已有配置
  if (item.contextWindow != null) {
    form.contextWindow = item.contextWindow
  }
  if (item.maxOutputTokens != null) {
    form.maxOutputTokens = item.maxOutputTokens
  }
  if (item.inputModalities?.length) {
    form.inputModalities = item.inputModalities.join(',')
  }
  if (item.reasoningEfforts?.length) {
    form.reasoningEfforts = item.reasoningEfforts.join(',')
  }
  if (item.defaultReasoningEffort) {
    form.defaultReasoningEffort = item.defaultReasoningEffort
  }
  if (item.systemPromptUpdate) {
    form.systemPromptUpdate = item.systemPromptUpdate
  }
}

async function loadModels() {
  loading.value = true
  try {
    models.value = await listGatewayModelsApi({
      type: modelType.value || undefined,
      providerId: selectedProviderId.value || undefined,
    })
  } finally {
    loading.value = false
  }
}

/** 弹窗预选供应商：优先用当前筛选值，其次默认供应商，再否则首个 */
function pickInitialProviderId(): number {
  if (selectedProviderId.value !== ALL_PROVIDERS) {
    return selectedProviderId.value
  }
  const preferred = providers.value.find((item) => item.isDefault) || providers.value[0]
  return preferred ? preferred.id : 0
}

/** 切换类型时同步采样参数：向量模型不适用 temperature / top_p / max_tokens */
function handleModelTypeChange(value: string | number | boolean | undefined) {
  if (value === 'CHAT') {
    if (form.temperature == null) form.temperature = DEFAULT_CHAT_PARAMS.temperature
    if (form.topP == null) form.topP = DEFAULT_CHAT_PARAMS.topP
    if (form.maxTokens == null) form.maxTokens = DEFAULT_CHAT_PARAMS.maxTokens
    return
  }
  form.temperature = null
  form.topP = null
  form.maxTokens = null
}

function resetForm() {
  form.modelId = ''
  form.displayName = ''
  form.providerId = pickInitialProviderId()
  form.upstreamModel = ''
  form.modelType = modelType.value || 'CHAT'
  form.contextWindow = null
  form.maxOutputTokens = null
  form.inputModalities = 'text'
  form.systemPromptUpdate = 'in-history'
  form.reasoningEfforts = null
  form.defaultReasoningEffort = null
  form.clientRequirements = null
  form.extraParams = null
  // 默认给一组可微调的采样参数（向量模型置空）
  const chat = form.modelType === 'CHAT'
  form.temperature = chat ? DEFAULT_CHAT_PARAMS.temperature : null
  form.topP = chat ? DEFAULT_CHAT_PARAMS.topP : null
  form.maxTokens = chat ? DEFAULT_CHAT_PARAMS.maxTokens : null
  form.isDefault = false
  form.status = 'ENABLED'
  form.sortOrder = 0
  form.remark = null
}

function openCreate() {
  editingId.value = null
  resetForm()
  autoDisplayName.value = ''
  dialogVisible.value = true
  void loadDialogUpstreamModels(form.providerId)
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
  autoDisplayName.value = ''
  dialogVisible.value = true
  // 已有的上游模型名可能不在返回列表中，allow-create 保证仍能正常回显
  void loadDialogUpstreamModels(row.providerId)
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
    // 保持当前筛选（默认为全部），新建的模型就在列表里
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
  importProviderId.value = selectedProviderId.value || null
  importType.value = modelType.value || 'CHAT'
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
      // 带入上游 /models 的元数据，导入后无需再手工补容量与档位
      models: importSelection.value.map((item) => ({
        upstreamModel: item.id,
        displayName: item.name ?? null,
        contextWindow: item.contextWindow ?? null,
        maxOutputTokens: item.maxOutputTokens ?? null,
        inputModalities: item.inputModalities ?? null,
        reasoningEfforts: item.reasoningEfforts ?? null,
        defaultReasoningEffort: item.defaultReasoningEffort ?? null,
        systemPromptUpdate: item.systemPromptUpdate ?? null,
      })),
    })
    ElMessage.success(`已导入 ${created.length} 个模型`)
    importVisible.value = false
    // 导入完成后跳到该供应商与该类型，方便核对结果
    selectedProviderId.value = importProviderId.value ?? ALL_PROVIDERS
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

.upstream-picker {
  display: flex;
  gap: 8px;
  width: 100%;
}

.upstream-picker__error {
  color: var(--el-color-danger);
}

.import-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
  max-height: 320px;
  overflow-y: auto;
}
</style>
