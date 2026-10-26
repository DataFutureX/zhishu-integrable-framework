<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    hero-title="Models"
    hero-eyebrow="模型网关"
    :hero-eyebrow-icon="Connection"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      统一维护网关对外暴露的模型目录：从上游导入或手动登记，配置容量、默认参数与启停状态；对话模型对外提供
      model_id，向量模型仅供知识库使用。
    </template>
    <template #heroActions>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="loadModels"
        >刷新</el-button
      >
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
    </template>

    <template #filter>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="gateway-page__hint"
        title="默认展示全部供应商的模型，可按供应商与类型筛选。新建或导入时必须指定供应商；对话模型对外暴露 model_id，向量模型仅供知识库使用，不进入 /v1/models。"
      />

      <div class="model-toolbar">
        <el-select
          v-model="selectedProviderId"
          class="model-toolbar__provider"
          placeholder="全部供应商"
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
        <div class="type-switch" role="group" aria-label="模型类型切换">
          <button
            v-for="opt in typeOptions"
            :key="opt.value"
            type="button"
            class="type-switch__btn"
            :class="{ 'is-active': modelType === opt.value }"
            :aria-pressed="modelType === opt.value"
            @click="switchModelType(opt.value)"
          >
            <el-icon :size="14"><component :is="opt.icon" /></el-icon>
            <span>{{ opt.label }}</span>
          </button>
        </div>
        <el-input
          v-model="keyword"
          class="model-toolbar__search"
          clearable
          placeholder="搜索展示名、模型 ID、上游模型或供应商…"
          :prefix-icon="Search"
        />
        <el-radio-group v-model="statusFilter">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="ENABLED">启用</el-radio-button>
          <el-radio-button value="DISABLED">停用</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="viewMode" class="view-toggle">
          <el-radio-button value="table">
            <el-icon><Grid /></el-icon>
            <span>表格</span>
          </el-radio-button>
          <el-radio-button value="card">
            <el-icon><Postcard /></el-icon>
            <span>卡片</span>
          </el-radio-button>
        </el-radio-group>
        <span class="model-toolbar__count">{{ displayModels.length }} 个模型</span>
      </div>
    </template>

    <div class="model-results">
      <el-empty
        v-if="!displayModels.length"
        class="model-empty"
        :description="activeFilterCount ? '没有匹配的模型' : '暂无模型'"
        :image-size="88"
      >
        <el-button v-if="activeFilterCount" :icon="Refresh" @click="resetFilters">
          清除筛选
        </el-button>
      </el-empty>

      <el-table
        v-else-if="viewMode === 'table'"
        :data="displayModels"
        class="modern-table"
        empty-text="暂无模型"
      >
        <el-table-column prop="modelId" label="模型 ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="displayName" label="展示名" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" size="small" type="success" class="mr-4">默认</el-tag>
            <el-tag
              :type="row.status === 'ENABLED' ? 'success' : 'info'"
              size="small"
              effect="light"
            >
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
            <el-tag
              :type="row.modelType === 'EMBEDDING' ? 'warning' : 'primary'"
              size="small"
              effect="plain"
            >
              {{ row.modelType === 'EMBEDDING' ? '向量' : '对话' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="upstreamModel"
          label="上游模型"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="参数" width="150">
          <template #default="{ row }">
            <span class="gateway-page__meta">
              T={{ row.temperature ?? '—' }} / P={{ row.topP ?? '—' }} /
              {{ row.maxTokens ?? '—' }}
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

      <div v-else class="model-card-wrap">
        <div class="model-card-grid">
          <article
            v-for="row in displayModels"
            :key="row.id"
            class="model-persona"
            :class="[
              `model-persona--${modelTone(row)}`,
              {
                'is-disabled': row.status !== 'ENABLED',
                'is-default': row.isDefault,
              },
            ]"
          >
            <div class="model-persona__rail" aria-hidden="true" />
            <div class="model-persona__body">
              <header class="model-persona__head">
                <div class="model-persona__avatar">
                  <el-icon :size="22"><component :is="modelIcon(row)" /></el-icon>
                  <span v-if="row.status === 'ENABLED'" class="model-persona__pulse" />
                </div>
                <div class="model-persona__titles">
                  <div class="model-persona__name-row">
                    <h3 class="model-persona__name" :title="row.displayName">
                      {{ row.displayName }}
                    </h3>
                    <el-tag v-if="row.isDefault" size="small" type="success" effect="dark" round>
                      默认
                    </el-tag>
                  </div>
                  <div class="model-persona__id-row">
                    <code class="model-persona__code">{{ row.modelId }}</code>
                    <span class="model-persona__provider" :title="providerLabel(row)">
                      {{ providerLabel(row) }}
                    </span>
                  </div>
                </div>
                <el-tag
                  size="small"
                  :type="row.status === 'ENABLED' ? 'success' : 'info'"
                  effect="plain"
                  round
                >
                  {{ row.status === 'ENABLED' ? '启用' : '停用' }}
                </el-tag>
              </header>

              <p class="model-persona__desc" :title="row.remark || ''">
                {{ row.remark || '未填写备注，可在编辑中补充该模型的用途、限流或客户端要求。' }}
              </p>

              <div class="model-persona__specs">
                <span class="model-persona__spec model-persona__spec--type">
                  {{ modelTypeLabel(row) }}
                </span>
                <span
                  class="model-persona__spec model-persona__spec--model"
                  :title="row.upstreamModel"
                >
                  {{ row.upstreamModel }}
                </span>
                <span class="model-persona__spec">ctx {{ formatTokens(row.contextWindow) }}</span>
                <span class="model-persona__spec">out {{ formatTokens(row.maxOutputTokens) }}</span>
                <span
                  v-if="row.modelType !== 'EMBEDDING'"
                  class="model-persona__spec model-persona__spec--muted"
                >
                  T {{ row.temperature ?? '—' }} · P {{ row.topP ?? '—' }} · max
                  {{ row.maxTokens ?? '—' }}
                </span>
                <span v-if="row.sortOrder" class="model-persona__spec model-persona__spec--muted">
                  排序 {{ row.sortOrder }}
                </span>
              </div>

              <div class="model-persona__chips">
                <span
                  v-for="item in modalityList(row)"
                  :key="`modal-${item}`"
                  class="model-chip is-tool"
                >
                  {{ item }}
                </span>
                <span v-for="item in reasoningList(row)" :key="`reason-${item}`" class="model-chip">
                  推理 {{ item }}
                </span>
                <span
                  v-if="!modalityList(row).length && !reasoningList(row).length"
                  class="model-persona__chips-empty"
                >
                  未配置输入模态与推理档位
                </span>
              </div>

              <footer class="model-persona__foot">
                <el-button
                  type="primary"
                  size="small"
                  :icon="VideoPlay"
                  :loading="testingId === row.id"
                  @click="handleTest(row as GatewayModelVO)"
                >
                  测试
                </el-button>
                <div class="model-persona__links">
                  <el-button
                    v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
                    type="primary"
                    link
                    size="small"
                    @click="openEdit(row as GatewayModelVO)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
                    type="primary"
                    link
                    size="small"
                    :disabled="Boolean(row.isDefault)"
                    @click="handleDefault(row as GatewayModelVO)"
                  >
                    设默认
                  </el-button>
                  <el-button
                    v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
                    type="primary"
                    link
                    size="small"
                    @click="handleToggle(row as GatewayModelVO)"
                  >
                    {{ row.status === 'ENABLED' ? '停用' : '启用' }}
                  </el-button>
                  <el-button
                    v-permission="PERMISSIONS.GATEWAY_MODEL_UPDATE"
                    type="danger"
                    link
                    size="small"
                    @click="handleDelete(row as GatewayModelVO)"
                  >
                    删除
                  </el-button>
                </div>
              </footer>
            </div>
          </article>
        </div>
      </div>
    </div>

    <template #extra>
      <GatewayModelFormDialog
        ref="modelFormRef"
        v-model="dialogVisible"
        :editing-id="editingId"
        :providers="providers"
        :form="form"
        :rules="rules"
        :saving="saving"
        :upstream-models="dialogUpstreamModels"
        :loading-upstream="loadingDialogUpstream"
        :upstream-error="dialogUpstreamError"
        @provider-change="handleFormProviderChange"
        @type-change="handleModelTypeChange"
        @upstream-change="handleUpstreamModelChange"
        @reload-upstream="reloadDialogUpstream"
        @submit="handleSubmit"
      />

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
          <el-select
            v-model="importType"
            size="small"
            style="width: 120px"
            @change="handleImportProviderChange"
          >
            <el-option label="对话" value="CHAT" />
            <el-option label="向量" value="EMBEDDING" />
          </el-select>
          <el-button size="small" :loading="loadingUpstream" @click="loadUpstreamModels"
            >拉取模型列表</el-button
          >
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
              <span class="gateway-page__meta"
                >{{ row.contextWindow ?? '—' }} / {{ row.maxOutputTokens ?? '—' }}</span
              >
            </template>
          </el-table-column>
          <el-table-column label="模态" width="110">
            <template #default="{ row }">{{
              (row.inputModalities || []).join(', ') || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="推理档位" min-width="170">
            <template #default="{ row }">
              <span class="gateway-page__meta">
                {{ (row.reasoningEfforts || []).join(', ') || '—' }}
                <template v-if="row.defaultReasoningEffort"
                  >（默认 {{ row.defaultReasoningEffort }}）</template
                >
              </span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loadingUpstream && upstreamModels.length === 0"
          description="点击「拉取模型列表」"
        />
        <template #footer>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="importSelection.length === 0"
            @click="handleImport"
          >
            导入
          </el-button>
        </template>
      </el-dialog>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import {
  ChatDotRound,
  CircleCheck,
  Coin,
  Connection,
  DataAnalysis,
  Download,
  Grid,
  Operation,
  Plus,
  Postcard,
  Refresh,
  Search,
  StarFilled,
  VideoPlay,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import GatewayModelFormDialog from '@/components/gateway/GatewayModelFormDialog.vue'
import { PERMISSIONS } from '@/constants/permissions'
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus'
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
  GatewayId,
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
const testingId = ref<GatewayId | null>(null)

const providers = ref<GatewayProviderVO[]>([])
const models = ref<GatewayModelVO[]>([])
// 供应商筛选哨兵值：0 = 全部供应商
const ALL_PROVIDERS = 0
const selectedProviderId = ref<GatewayId>(ALL_PROVIDERS)
// '' = 全部类型
const modelType = ref<GatewayModelType | ''>('')
const typeOptions: { value: GatewayModelType | ''; label: string; icon: Component }[] = [
  { value: '', label: '全部', icon: Operation },
  { value: 'CHAT', label: '对话', icon: ChatDotRound },
  { value: 'EMBEDDING', label: '向量', icon: DataAnalysis },
]
const switchModelType = (value: GatewayModelType | '') => {
  if (modelType.value === value) {
    return
  }
  modelType.value = value
  void loadModels()
}
const viewMode = ref<'table' | 'card'>('card')
const keyword = ref('')
const statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')

const sortOrderRank = (value?: number | null) =>
  value == null || Number.isNaN(value) ? Number.MAX_SAFE_INTEGER : value

// 启用模型整体置顶，对话模型排在向量模型之前，组内再按排列序号升序
const modelTypeRank = (value?: GatewayModelType | string | null) => (value === 'EMBEDDING' ? 1 : 0)

const compareModels = (a: GatewayModelVO, b: GatewayModelVO) => {
  const enabledDelta = Number(b.status === 'ENABLED') - Number(a.status === 'ENABLED')
  if (enabledDelta !== 0) {
    return enabledDelta
  }
  const typeDelta = modelTypeRank(a.modelType) - modelTypeRank(b.modelType)
  if (typeDelta !== 0) {
    return typeDelta
  }
  const orderDelta = sortOrderRank(a.sortOrder) - sortOrderRank(b.sortOrder)
  if (orderDelta !== 0) {
    return orderDelta
  }
  return String(a.modelId).localeCompare(String(b.modelId))
}

const displayModels = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return models.value
    .filter((row) => {
      if (statusFilter.value !== 'ALL' && row.status !== statusFilter.value) {
        return false
      }
      if (!needle) {
        return true
      }
      return [row.displayName, row.modelId, row.upstreamModel, row.providerName, row.providerKey]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(needle))
    })
    .sort(compareModels)
})

const activeFilterCount = computed(
  () =>
    (keyword.value.trim() ? 1 : 0) +
    (statusFilter.value !== 'ALL' ? 1 : 0) +
    (selectedProviderId.value !== ALL_PROVIDERS ? 1 : 0) +
    (modelType.value ? 1 : 0),
)

const enabledModelCount = computed(
  () => models.value.filter((row) => row.status === 'ENABLED').length,
)

const defaultModelName = computed(
  () => models.value.find((row) => row.isDefault)?.displayName || '—',
)

const heroMetrics = computed(() => [
  {
    key: 'models',
    label: '模型总数',
    value: models.value.length,
    icon: Coin,
    accent: 'primary' as const,
  },
  {
    key: 'enabled',
    label: '已启用',
    value: enabledModelCount.value,
    icon: CircleCheck,
    accent: 'success' as const,
  },
  {
    key: 'default',
    label: '默认模型',
    value: defaultModelName.value,
    icon: StarFilled,
    accent: 'primary' as const,
  },
])

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = 'ALL'
  if (selectedProviderId.value !== ALL_PROVIDERS || modelType.value) {
    selectedProviderId.value = ALL_PROVIDERS
    modelType.value = ''
    void loadModels()
  }
}

const modelTone = (row: GatewayModelVO) => (row.modelType === 'EMBEDDING' ? 'embedding' : 'chat')

const modelIcon = (row: GatewayModelVO) =>
  row.modelType === 'EMBEDDING' ? DataAnalysis : ChatDotRound

const modelTypeLabel = (row: GatewayModelVO) =>
  row.modelType === 'EMBEDDING' ? '向量模型' : '对话模型'

const providerLabel = (row: GatewayModelVO) =>
  row.providerName || row.providerKey || (row.providerId ? `供应商 #${row.providerId}` : '—')

function splitTokens(value?: string | null): string[] {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

const modalityList = (row: GatewayModelVO) => splitTokens(row.inputModalities)

const reasoningList = (row: GatewayModelVO) => splitTokens(row.reasoningEfforts)

function formatTokens(value?: number | null): string {
  if (value == null || value <= 0) return '—'
  if (value < 1024) return String(value)
  const k = value / 1024
  return `${Number.isInteger(k) ? k : k.toFixed(1)}K`
}

const dialogVisible = ref(false)
const editingId = ref<GatewayId | null>(null)
/** 表单弹窗改为展示型组件后，校验入口通过组件暴露的 validate() 调用 */
const modelFormRef = ref<{ validate: () => Promise<boolean> }>()

const importVisible = ref(false)
const importType = ref<GatewayModelType>('CHAT')
const importProviderId = ref<GatewayId | null>(null)
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
  upstreamModel: [
    { required: true, message: '请选择或填写上游模型名', trigger: ['change', 'blur'] },
  ],
}

// 新建时的采样参数默认值（与现有云端模型一致，保存前可微调）；向量模型不适用
const DEFAULT_CHAT_PARAMS = { temperature: 0.7, topP: 0.9, maxTokens: 2000 }

async function loadProviders() {
  try {
    providers.value = await listGatewayProvidersApi()
  } catch (error) {
    // 错误提示由 request 拦截器统一出口，这里仅记录，避免冒泡成未处理的 rejection
    console.error('获取供应商列表失败:', error)
  }
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
  if (
    !editingId.value &&
    modelIdSuggested &&
    (!form.modelId.trim() || form.modelId.startsWith('gw-'))
  ) {
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
function pickInitialProviderId(): GatewayId {
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
  const valid = await modelFormRef.value?.validate()
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

<style scoped lang="scss">
.list-page {
  --model-ink: #0f2740;
  --model-muted: #5b738a;
  --model-accent: #0891b2;
  --model-primary: #1a7fd4;
  --model-line: rgba(26, 43, 60, 0.1);
  --model-shelf: #f4f7fa;
}

.gateway-page__hint {
  margin: 14px 12px 0;
}

.gateway-page__meta {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.view-toggle :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.model-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 12px 12px 14px;
  padding: 10px 12px;
  border: 1px solid var(--model-line);
  border-radius: 8px;
  background: var(--model-shelf);

  &__provider {
    width: 200px;
  }

  &__search {
    flex: 1;
    min-width: 220px;
    max-width: 380px;
  }

  &__count {
    margin-left: auto;
    color: var(--model-muted);
    font-size: 12px;
  }
}

.type-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--model-line);
  border-radius: 999px;
  background: #fff;

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 12px;
    border: none;
    border-radius: 999px;
    background: transparent;
    color: var(--model-muted);
    font-size: 12.5px;
    font-weight: 600;
    line-height: 1.4;
    cursor: pointer;
    transition:
      background 0.18s ease,
      color 0.18s ease,
      box-shadow 0.18s ease;

    &:hover {
      color: var(--model-primary);
    }

    &:focus-visible {
      outline: 2px solid var(--model-primary);
      outline-offset: 2px;
    }

    &.is-active {
      color: #fff;
      background: var(--model-primary);
      box-shadow: 0 2px 6px rgba(26, 127, 212, 0.28);
    }
  }
}

.model-results {
  min-height: 220px;
  padding: 0 12px 16px;
}

.model-empty {
  padding: 48px 0;
}

.model-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(306px, 1fr));
  gap: 14px;
}

.model-persona {
  position: relative;
  display: grid;
  grid-template-columns: 6px minmax(0, 1fr);
  min-height: 248px;
  overflow: hidden;
  border: 1px solid rgba(26, 43, 60, 0.08);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--model-primary) 35%, transparent);
    box-shadow:
      0 4px 8px rgba(15, 23, 42, 0.05),
      0 12px 24px rgba(15, 23, 42, 0.07);
  }

  &.is-disabled {
    --persona-tone: #b3bfcb;

    border-color: #ccd6e0;
    background: #e6ebf1;
    box-shadow: none;

    &:hover {
      transform: none;
      border-color: #bdc9d6;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
    }

    & .model-persona__rail {
      background: #a9b6c4;
    }

    & .model-persona__avatar {
      color: #647587;
      background: #d8e0e8;
      box-shadow: none;
      filter: none;
    }

    & .model-persona__name,
    & .model-persona__provider {
      color: #5f7183;
    }

    & .model-persona__desc {
      color: #75879a;
    }

    & .model-persona__code {
      color: #6b7d8f;
      background: #d9e1e9;
    }

    & .model-persona__spec {
      color: #64768a;
      background: #dde5ec;
      border-color: #cad4de;
      font-weight: 600;
    }

    & .model-chip {
      color: #6b7d8f;
      background: #dde5ec;
      border-color: #cad4de;
    }

    & .model-persona__foot {
      border-top-color: #cdd6e0;
    }
  }

  &.is-default {
    border-color: color-mix(in srgb, var(--model-accent) 40%, transparent);
    box-shadow:
      0 0 0 1px rgba(8, 145, 178, 0.08),
      0 10px 24px rgba(8, 145, 178, 0.08);
  }

  &--chat {
    --persona-tone: #7dd3fc;
  }

  &--embedding {
    --persona-tone: #fdba74;
  }

  &__rail {
    background: var(--persona-tone);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
    padding: 16px 16px 14px;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__avatar {
    position: relative;
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 12px;
    color: #04202a;
    background: color-mix(in srgb, var(--persona-tone) 55%, #fff);
    box-shadow: 0 4px 12px color-mix(in srgb, var(--persona-tone) 28%, transparent);
  }

  &__pulse {
    position: absolute;
    right: -2px;
    bottom: -2px;
    width: 10px;
    height: 10px;
    border: 2px solid #fff;
    border-radius: 50%;
    background: #22c55e;
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
    animation: model-pulse 1.6s ease-out infinite;
  }

  &__titles {
    flex: 1;
    min-width: 0;
  }

  &__name-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__name {
    margin: 0;
    overflow: hidden;
    color: var(--model-ink);
    font-size: 16px;
    font-weight: 700;
    line-height: 1.3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__id-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    min-width: 0;
    margin-top: 4px;
  }

  &__code {
    padding: 1px 8px;
    border-radius: 4px;
    color: var(--model-muted);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
    background: #f1f6fa;
  }

  &__provider {
    font-size: 11px;
    font-weight: 650;
    color: var(--persona-tone);
  }

  &__desc {
    display: -webkit-box;
    min-height: 40px;
    margin: 0;
    overflow: hidden;
    color: var(--model-muted);
    font-size: 13px;
    line-height: 1.55;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  &__specs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__spec {
    padding: 2px 8px;
    border: 1px solid rgba(26, 127, 212, 0.16);
    border-radius: 999px;
    color: var(--model-primary);
    font-size: 11px;
    font-weight: 650;
    background: rgba(26, 127, 212, 0.08);

    &--type {
      color: #0f2740;
      background: color-mix(in srgb, var(--persona-tone) 22%, #fff);
      border-color: color-mix(in srgb, var(--persona-tone) 45%, #fff);
    }

    &--model {
      max-width: 180px;
      overflow: hidden;
      color: #7c3aed;
      font-weight: 600;
      background: rgba(124, 58, 237, 0.07);
      border-color: rgba(124, 58, 237, 0.18);
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &--muted {
      color: var(--model-muted);
      font-weight: 500;
      background: #f5f8fb;
      border-color: #e8eef3;
    }
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 28px;
  }

  &__chips-empty {
    color: var(--el-text-color-placeholder);
    font-size: 12px;
  }

  &__foot {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: auto;
    padding-top: 10px;
    border-top: 1px solid #eef3f7;
  }

  &__links {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

.model-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  color: #334155;
  font-size: 11.5px;
  font-weight: 600;
  background: #f1f5f9;

  &.is-tool {
    color: #0e7490;
    background: rgba(8, 145, 178, 0.08);
    border-color: rgba(8, 145, 178, 0.22);
  }
}

@keyframes model-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(34, 197, 94, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0);
  }
}

@media (max-width: 768px) {
  .model-card-grid {
    grid-template-columns: 1fr;
  }
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
