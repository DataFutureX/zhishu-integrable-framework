<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    hero-title="Agents"
    hero-eyebrow="智能中心"
    :hero-eyebrow-icon="Cpu"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      配置智能体人设、能力与工作流；试运行验证 Tools / RAG，再交给 Agent 会话调用。
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.AI_AGENT_ADD"
        type="primary"
        size="small"
        :icon="Plus"
        @click="openCreate"
      >
        新建智能体
      </el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </template>

    <div class="agent-home">
      <div class="agent-toolbar">
        <el-input
          v-model="queryParams.keyword"
          class="agent-toolbar__search"
          clearable
          placeholder="搜索名称或编码…"
          :prefix-icon="Search"
        />
        <el-select
          v-model="queryParams.workflowType"
          clearable
          placeholder="工作流"
          class="agent-toolbar__workflow"
        >
          <el-option
            v-for="item in workflowTemplates"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
        <el-radio-group v-model="statusFilter" size="default">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="ENABLED">启用</el-radio-button>
          <el-radio-button value="DISABLED">禁用</el-radio-button>
        </el-radio-group>
      </div>

      <el-empty
        v-if="!displayData.length"
        class="agent-empty"
        :description="activeFilterCount ? '没有匹配的智能体' : '暂无智能体，请先新建'"
        :image-size="88"
      >
        <el-button
          v-if="!activeFilterCount"
          v-permission="PERMISSIONS.AI_AGENT_ADD"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新建智能体
        </el-button>
        <el-button v-else :icon="Refresh" @click="handleReset">清除筛选</el-button>
      </el-empty>

      <div v-else class="agent-grid">
        <article
          v-for="row in displayData"
          :key="row.id"
          class="agent-persona"
          :class="[
            `agent-persona--${workflowTone(row.workflowType)}`,
            {
              'is-enabled': row.status === 'ENABLED',
              'is-disabled': row.status !== 'ENABLED',
              'is-default': row.defaultAgent,
            },
          ]"
        >
          <div class="agent-persona__rail" aria-hidden="true" />
          <div class="agent-persona__body">
            <header class="agent-persona__head">
              <div class="agent-persona__avatar">
                <el-icon :size="22"><Service /></el-icon>
                <span v-if="row.status === 'ENABLED'" class="agent-persona__pulse" />
              </div>
              <div class="agent-persona__titles">
                <div class="agent-persona__name-row">
                  <h3 class="agent-persona__name" :title="row.name">{{ row.name }}</h3>
                  <el-tag v-if="row.defaultAgent" size="small" type="success" effect="dark" round>
                    默认
                  </el-tag>
                </div>
                <div class="agent-persona__id-row">
                  <code class="agent-persona__code">{{ row.code }}</code>
                  <span class="agent-persona__workflow">{{ workflowLabel(row.workflowType) }}</span>
                </div>
              </div>
              <el-tag
                size="small"
                :type="row.status === 'ENABLED' ? 'success' : 'info'"
                effect="plain"
                round
              >
                {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
              </el-tag>
            </header>

            <p class="agent-persona__desc" :title="row.description || ''">
              {{ row.description || '暂无人设简介，可在编辑中补充角色定位与适用场景。' }}
            </p>

            <div class="agent-persona__specs">
              <span v-if="row.enableMemory" class="agent-persona__spec">Memory</span>
              <span v-if="row.capabilities?.includes('RAG')" class="agent-persona__spec">RAG</span>
              <span v-if="row.builtin" class="agent-persona__spec">内置</span>
              <span class="agent-persona__spec agent-persona__spec--muted">
                {{ row.capabilities?.length || 0 }} 项能力
              </span>
            </div>

            <div class="agent-persona__skills">
              <el-tooltip
                v-for="cap in row.capabilities"
                :key="cap"
                placement="top"
                :show-after="200"
              >
                <template #content>
                  <div class="cap-tip">
                    <div class="cap-tip__title">{{ capabilityLabel(cap) }}</div>
                    <div class="cap-tip__desc">{{ capabilityDesc(cap) }}</div>
                    <div v-if="capabilityTools(cap).length" class="cap-tip__tools">
                      <div
                        v-for="tool in capabilityTools(cap)"
                        :key="tool.name"
                        class="cap-tip__tool"
                      >
                        <code>{{ tool.name }}</code>
                        <span v-if="tool.description">{{ tool.description }}</span>
                      </div>
                    </div>
                    <div v-else-if="capabilityMeta(cap)?.toolBased === false" class="cap-tip__desc">
                      非 Tool 能力
                    </div>
                  </div>
                </template>
                <span class="agent-skill" :class="{ 'is-tool': capabilityMeta(cap)?.toolBased }">
                  {{ capabilityLabel(cap) }}
                </span>
              </el-tooltip>
              <span v-if="!row.capabilities?.length" class="agent-persona__skills-empty">未配置能力</span>
            </div>

            <footer class="agent-persona__foot">
              <el-button type="primary" size="small" :icon="Promotion" @click="openTrial(row)">
                试运行
              </el-button>
              <div class="agent-persona__links">
                <el-button
                  v-permission="PERMISSIONS.AI_AGENT_EDIT"
                  type="primary"
                  link
                  size="small"
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_AGENT_GRAPH"
                  type="primary"
                  link
                  size="small"
                  @click="openGraph(row)"
                >
                  编排
                </el-button>
                <el-button
                  v-if="!row.defaultAgent && row.status === 'ENABLED'"
                  type="success"
                  link
                  size="small"
                  @click="handleSetDefault(row)"
                >
                  设默认
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_AGENT_REMOVE"
                  type="danger"
                  link
                  size="small"
                  :disabled="row.builtin || row.defaultAgent"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </div>
            </footer>
          </div>
        </article>
      </div>
    </div>

    <template #extra>
      <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑智能体' : '新建智能体'"
      width="1080px"
      :close-on-click-modal="false"
      destroy-on-close
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item v-if="!editingId" label="编码" prop="code">
          <el-input v-model="form.code" placeholder="字母开头，如 alert_helper" maxlength="64" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="工作流" prop="workflowType">
          <div class="workflow-picker">
            <button
              v-for="item in workflowTemplates"
              :key="item.code"
              type="button"
              class="workflow-pick"
              :class="[
                `workflow-pick--${workflowTone(item.code)}`,
                { 'is-active': form.workflowType === item.code },
              ]"
              @click="selectWorkflow(item.code)"
            >
              <el-tooltip placement="top" popper-class="agent-workflow-hint-popper">
                <template #content>
                  <div class="workflow-hint">
                    <div class="workflow-hint__title">{{ item.label }}</div>
                    <p class="workflow-hint__desc">{{ workflowHint(item) }}</p>
                  </div>
                </template>
                <span class="workflow-pick__icon">
                  <el-icon :size="20"><component :is="workflowIcon(item.code)" /></el-icon>
                </span>
              </el-tooltip>
              <span class="workflow-pick__label">{{ item.label }}</span>
            </button>
          </div>
        </el-form-item>
        <el-form-item label="能力" prop="capabilities">
          <el-checkbox-group v-model="form.capabilities" class="cap-group">
            <div v-for="cap in capabilities" :key="cap.code" class="cap-option">
              <el-checkbox :label="cap.code" class="cap-option__check">
                <span class="cap-option__title">{{ cap.label }}</span>
                <el-tag v-if="cap.toolBased" size="small" type="primary" effect="plain">Tools</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">非 Tool</el-tag>
              </el-checkbox>
              <p class="cap-option__desc">{{ cap.description }}</p>
              <div v-if="resolveCapTools(cap).length" class="cap-option__tools">
                <el-tooltip
                  v-for="tool in resolveCapTools(cap)"
                  :key="tool.name"
                  :content="tool.description || tool.name"
                  placement="top"
                  :show-after="150"
                >
                  <el-tag size="small" class="tool-chip" effect="plain">
                    {{ tool.name }}
                  </el-tag>
                </el-tooltip>
              </div>
            </div>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <div class="prompt-head">
            <el-button
              type="primary"
              link
              :icon="MagicStick"
              :loading="draftingPrompt"
              @click="generateSystemPromptDraft"
            >
              根据表单生成初稿
            </el-button>
          </div>
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="8"
            placeholder="可先填名称、简介并勾选能力，再点上方生成初稿"
          />
          <p class="prompt-hint">依据名称、简介、工作流、能力、知识库与上游 MCP 生成可再编辑的初稿。</p>
        </el-form-item>
        <el-form-item label="模型参数">
          <div class="model-row">
            <el-switch v-model="followGlobalModel" active-text="跟随全局" inactive-text="自定义" />
          </div>
          <div v-if="!followGlobalModel" class="model-fields">
            <el-input v-model="form.model" placeholder="模型名，如 qwen-plus" style="width: 200px" />
            <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
            <el-input-number v-model="form.maxTokens" :min="256" :max="16000" :step="256" />
          </div>
        </el-form-item>
        <el-form-item label="多轮记忆">
          <el-switch v-model="form.enableMemory" />
        </el-form-item>
        <el-form-item label="绑定文档">
          <el-select
            v-model="form.documentIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="空=全部知识库"
            style="width: 100%"
            :loading="docsLoading"
          >
            <el-option
              v-for="doc in documentOptions"
              :key="doc.id"
              :label="doc.label"
              :value="doc.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上游 MCP">
          <el-select
            v-model="form.mcpUpstreamIds"
            multiple
            filterable
            clearable
            collapse-tags
            placeholder="可选：挂载他方 MCP 工具"
            style="width: 100%"
          >
            <el-option
              v-for="u in mcpUpstreams"
              :key="u.id"
              :label="`${u.name} (${u.code})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="ENABLED">启用</el-radio>
            <el-radio label="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="trialVisible"
      width="760px"
      destroy-on-close
      class="agent-trial-dialog"
      :title="trialAgent ? `试运行 · ${trialAgent.name}` : '试运行'"
    >
      <div v-if="trialAgent" class="trial-meta">
        <code>{{ trialAgent.code }}</code>
        <el-tag size="small" effect="plain">{{ workflowLabel(trialAgent.workflowType) }}</el-tag>
        <span v-if="trialConversationId" class="trial-cid">会话 {{ trialConversationId }}</span>
      </div>
      <el-input
        v-model="trialMessage"
        type="textarea"
        :rows="4"
        placeholder="输入一句话测试智能体…"
        :disabled="trialLoading"
      />
      <div class="trial-actions">
        <el-checkbox
          v-model="trialEnableRag"
          :disabled="!trialAgent?.capabilities?.includes('RAG')"
        >
          启用知识库
        </el-checkbox>
        <el-checkbox v-model="trialEnableMemory">多轮记忆</el-checkbox>
        <el-checkbox v-model="trialStream">流式</el-checkbox>
        <el-button @click="clearTrialMemory">清空会话</el-button>
        <el-button type="primary" :loading="trialLoading" @click="runTrial">运行</el-button>
      </div>
      <el-scrollbar v-if="trialResult" max-height="240px" class="trial-result">
        <pre>{{ trialResult }}</pre>
      </el-scrollbar>
      <AgentTracePanel :traces="trialTraces" default-expand />
      <div v-if="trialRuns.length" class="trial-runs">
        <p class="trial-runs__title">最近运行</p>
        <el-table :data="trialRuns" size="small" max-height="160">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="currentNode" label="节点" />
          <el-table-column prop="createTime" label="时间" width="160" />
        </el-table>
      </div>
    </el-dialog>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIAgentManage' })

import { computed, reactive, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { Cpu, Guide, Plus, Refresh, RefreshRight, Search, SetUp, Service, Share, Sort, Promotion, MagicStick } from '@element-plus/icons-vue'
import AgentTracePanel from '@/components/ai/AgentTracePanel.vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { PERMISSIONS } from '@/constants/permissions'
import {
  createAgent,
  deleteAgent,
  draftAgentSystemPrompt,
  getDocumentList,
  listAgentCapabilities,
  listAgentRuns,
  listAgents,
  listMcpUpstreams,
  listWorkflowTemplates,
  setDefaultAgent,
  trialAgent as runAgentTrial,
  updateAgent,
} from '@/api/ai'
import { postAiSse } from '@/utils/aiSse'
import type { AgentRunVO, AgentVO, CapabilityVO, ToolInfoVO, WorkflowTemplateVO } from '@/types/aiAgent'
import type { McpUpstreamVO } from '@/types/mcp'
import type { AgentTraceEvent } from '@/types/aiChat'
import type { DocumentVO } from '@/types/aiDocument'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const tableData = ref<AgentVO[]>([])
const capabilities = ref<CapabilityVO[]>([])
const workflowTemplates = ref<WorkflowTemplateVO[]>([])
const statusFilter = ref<string>('ALL')
const queryParams = reactive({
  keyword: '',
  workflowType: '' as string,
})

const formVisible = ref(false)
const editingId = ref<number | null>(null)
const followGlobalModel = ref(true)
const draftingPrompt = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  systemPrompt: '',
  model: '' as string | null,
  temperature: null as number | null,
  maxTokens: null as number | null,
  capabilities: [] as string[],
  workflowType: 'REACT',
  documentIds: [] as number[],
  mcpUpstreamIds: [] as number[],
  enableMemory: true,
  status: 'ENABLED',
})

const documents = ref<DocumentVO[]>([])
const docsLoading = ref(false)
const mcpUpstreams = ref<McpUpstreamVO[]>([])

const documentOptions = computed(() =>
  documents.value.map((d) => ({
    id: Number(d.id),
    label: d.fileName || `文档#${d.id}`,
  })),
)

const formRules: FormRules = {
  code: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{1,62}$/,
      message: '编码需以字母开头，仅含字母数字下划线',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  workflowType: [{ required: true, message: '请选择工作流', trigger: 'change' }],
  capabilities: [{ type: 'array', required: true, min: 1, message: '至少勾选一项能力', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const trialVisible = ref(false)
const trialAgent = ref<AgentVO | null>(null)
const trialMessage = ref('')
const trialEnableRag = ref(false)
const trialEnableMemory = ref(false)
const trialStream = ref(true)
const trialLoading = ref(false)
const trialResult = ref('')
const trialTraces = ref<AgentTraceEvent[]>([])
const trialConversationId = ref('')
const trialRuns = ref<AgentRunVO[]>([])

const defaultAgentName = computed(
  () => tableData.value.find((a) => a.defaultAgent)?.name || '',
)

const activeFilterCount = computed(() => {
  let n = 0
  if (queryParams.keyword) n += 1
  if (queryParams.workflowType) n += 1
  if (statusFilter.value !== 'ALL') n += 1
  return n
})

const displayData = computed(() => {
  let rows = [...tableData.value]
  if (statusFilter.value !== 'ALL') {
    rows = rows.filter((r) => r.status === statusFilter.value)
  }
  if (queryParams.workflowType) {
    rows = rows.filter((r) => r.workflowType === queryParams.workflowType)
  }
  const kw = queryParams.keyword.trim().toLowerCase()
  if (kw) {
    rows = rows.filter(
      (r) => r.name.toLowerCase().includes(kw) || r.code.toLowerCase().includes(kw),
    )
  }
  return rows
})

const capabilityMeta = (code: string) =>
  capabilities.value.find((c) => c.code === code)

const capabilityLabel = (code: string) => capabilityMeta(code)?.label || code

const capabilityDesc = (code: string) =>
  capabilityMeta(code)?.description || '暂无能力描述'

const resolveCapTools = (cap: CapabilityVO): ToolInfoVO[] => {
  if (cap.tools?.length) return cap.tools
  return (cap.toolNames || []).map((name) => ({ name, description: '' }))
}

const capabilityTools = (code: string): ToolInfoVO[] => {
  const meta = capabilityMeta(code)
  return meta ? resolveCapTools(meta) : []
}

const toolMethodCount = computed(() => {
  const names = new Set<string>()
  capabilities.value.forEach((cap) => {
    resolveCapTools(cap).forEach((t) => names.add(t.name))
  })
  return names.size
})

const heroMetrics = computed(() => [
  {
    key: 'agents',
    label: '智能体',
    value: tableData.value.length,
    icon: Service,
    accent: 'primary' as const,
  },
  {
    key: 'default',
    label: '默认智能体',
    value: defaultAgentName.value || '—',
    icon: Cpu,
    accent: 'primary' as const,
  },
  {
    key: 'tools',
    label: '可挂载 Tools',
    value: toolMethodCount.value,
    icon: SetUp,
    accent: 'primary' as const,
  },
])

const workflowLabel = (code: string) =>
  workflowTemplates.value.find((w) => w.code === code)?.label || code

const workflowTone = (code: string) => {
  const map: Record<string, string> = {
    REACT: 'react',
    SEQUENTIAL: 'sequential',
    ROUTING: 'routing',
    GRAPH: 'graph',
  }
  return map[code] || 'react'
}

const workflowIconMap: Record<string, Component> = {
  REACT: RefreshRight,
  SEQUENTIAL: Sort,
  ROUTING: Guide,
  GRAPH: Share,
}

const workflowHintMap: Record<string, string> = {
  REACT: '单智能体推理-行动循环：模型边思考边按需调用 Tools，适合通用问答与工具调用。',
  SEQUENTIAL: '固定三步：先澄清意图，再调用工具执行，最后润色答案。适合巡检、报告等结构化任务。',
  ROUTING: '入口先判断问题类型，再路由到「数据查询」或「知识问答」子智能体。适合问题差异较大的场景。',
  GRAPH: '按可视化节点与边编排执行（START / LLM / TOOL_AGENT / ROUTER / END）。保存后可进入工作流编排画布。',
}

const workflowIcon = (code: string) => workflowIconMap[code] || Cpu

const workflowHint = (item: WorkflowTemplateVO) => workflowHintMap[item.code] || item.description

const selectWorkflow = (code: string) => {
  form.workflowType = code
  void formRef.value?.validateField('workflowType')
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.workflowType = ''
  statusFilter.value = 'ALL'
}

const fetchDocuments = async () => {
  docsLoading.value = true
  try {
    const res = await getDocumentList()
    documents.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error(e)
    documents.value = []
  } finally {
    docsLoading.value = false
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const [agents, caps, templates, ups] = await Promise.all([
      listAgents(),
      listAgentCapabilities(),
      listWorkflowTemplates(),
      listMcpUpstreams().catch(() => []),
    ])
    tableData.value = agents || []
    capabilities.value = caps || []
    workflowTemplates.value = templates || []
    mcpUpstreams.value = ups || []
    void fetchDocuments()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  editingId.value = null
  followGlobalModel.value = true
  form.code = ''
  form.name = ''
  form.description = ''
  form.systemPrompt = ''
  form.model = null
  form.temperature = null
  form.maxTokens = null
  form.capabilities = ['RAG', 'MCP_TOOLS']
  form.workflowType = 'REACT'
  form.documentIds = []
  form.mcpUpstreamIds = []
  form.enableMemory = true
  form.status = 'ENABLED'
}

const generateSystemPromptDraft = async () => {
  if (!form.name.trim() && !form.description.trim() && !form.capabilities.length) {
    ElMessage.warning('请先填写名称、简介或勾选能力')
    return
  }
  if (form.systemPrompt.trim()) {
    try {
      await ElMessageBox.confirm('将覆盖当前系统提示词，是否继续？', '生成初稿', { type: 'warning' })
    } catch {
      return
    }
  }
  draftingPrompt.value = true
  try {
    const mcpNames = mcpUpstreams.value
      .filter((u) => form.mcpUpstreamIds.includes(u.id))
      .map((u) => `${u.name} (${u.code})`)
    const docNames = documentOptions.value
      .filter((d) => form.documentIds.includes(d.id))
      .map((d) => d.label)
    const vo = await draftAgentSystemPrompt({
      name: form.name,
      description: form.description,
      capabilities: form.capabilities,
      workflowType: form.workflowType,
      enableMemory: form.enableMemory,
      mcpUpstreamNames: mcpNames,
      documentNames: docNames,
      existingPrompt: form.systemPrompt || undefined,
    })
    form.systemPrompt = vo?.prompt || ''
    ElMessage.success(
      vo?.source === 'TEMPLATE'
        ? '已按表单拼出初稿，请核对后保存'
        : '已生成初稿，请核对后保存',
    )
  } finally {
    draftingPrompt.value = false
  }
}

const openCreate = () => {
  resetForm()
  formVisible.value = true
}

const openEdit = (row: AgentVO) => {
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description || ''
  form.systemPrompt = row.systemPrompt
  form.model = row.model || null
  form.temperature = row.temperature ?? null
  form.maxTokens = row.maxTokens ?? null
  form.capabilities = [...(row.capabilities || [])]
  form.workflowType = row.workflowType || 'REACT'
  form.documentIds = [...(row.documentIds || [])]
  form.mcpUpstreamIds = [...(row.mcpUpstreamIds || [])]
  form.enableMemory = !!row.enableMemory
  form.status = row.status || 'ENABLED'
  followGlobalModel.value = !row.model && row.temperature == null && row.maxTokens == null
  formVisible.value = true
  void fetchDocuments()
}

const openGraph = (row: AgentVO) => {
  router.push(`/ai/agents/${row.id}/graph`)
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const modelFields = followGlobalModel.value
      ? { model: null, temperature: null, maxTokens: null }
      : {
          model: form.model || null,
          temperature: form.temperature,
          maxTokens: form.maxTokens,
        }
    if (editingId.value) {
      await updateAgent(editingId.value, {
        name: form.name,
        description: form.description,
        systemPrompt: form.systemPrompt,
        ...modelFields,
        capabilities: form.capabilities,
        workflowType: form.workflowType,
        documentIds: form.documentIds,
        mcpUpstreamIds: form.mcpUpstreamIds,
        enableMemory: form.enableMemory,
        status: form.status,
      })
      ElMessage.success('已更新')
    } else {
      await createAgent({
        code: form.code,
        name: form.name,
        description: form.description,
        systemPrompt: form.systemPrompt,
        ...modelFields,
        capabilities: form.capabilities,
        workflowType: form.workflowType,
        documentIds: form.documentIds,
        mcpUpstreamIds: form.mcpUpstreamIds,
        enableMemory: form.enableMemory,
        status: form.status,
      })
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await fetchList()
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: AgentVO) => {
  try {
    await ElMessageBox.confirm(`确认删除智能体「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAgent(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch (e) {
    console.error(e)
  }
}

const handleSetDefault = async (row: AgentVO) => {
  try {
    await setDefaultAgent(row.id)
    ElMessage.success(`已将「${row.name}」设为默认`)
    await fetchList()
  } catch (e) {
    console.error(e)
  }
}

const openTrial = async (row: AgentVO) => {
  trialAgent.value = row
  trialMessage.value = ''
  trialResult.value = ''
  trialTraces.value = []
  trialConversationId.value = ''
  trialEnableMemory.value = false
  trialStream.value = true
  trialEnableRag.value = row.capabilities?.includes('RAG') ?? false
  trialVisible.value = true
  try {
    trialRuns.value = await listAgentRuns(row.id, 8)
  } catch {
    trialRuns.value = []
  }
}

const clearTrialMemory = () => {
  trialConversationId.value = ''
  trialResult.value = ''
  trialTraces.value = []
  ElMessage.success('已清空试运行会话')
}

const runTrial = async () => {
  if (!trialAgent.value || !trialMessage.value.trim()) {
    ElMessage.warning('请输入试运行消息')
    return
  }
  trialLoading.value = true
  trialResult.value = ''
  trialTraces.value = []
  const payload = {
    message: trialMessage.value.trim(),
    enableRag: trialEnableRag.value,
    conversationId: trialConversationId.value || undefined,
    enableMemory: trialEnableMemory.value,
  }
  try {
    if (trialStream.value) {
      let content = ''
      let awaitingFinal = false
      let gotFinalMessage = false
      await postAiSse(
        `/agents/${trialAgent.value.id}/trial/stream`,
        payload,
        {
          onMessage: (chunk) => {
            if (!gotFinalMessage || awaitingFinal) {
              content = chunk
              gotFinalMessage = true
              awaitingFinal = false
            } else {
              content += chunk
            }
            trialResult.value = content
          },
          onProgress: (json) => {
            try {
              const event = JSON.parse(json) as {
                type?: string
                name?: string
                detail?: string | null
              }
              trialTraces.value = [...trialTraces.value, event as never]
              // 正文已开始输出后，不再用 progress 覆盖结果
              if (gotFinalMessage) {
                return
              }
              if (event.type === 'NODE_END' && event.name?.includes('意图') && event.detail) {
                trialResult.value = `已理解意图\n\n${event.detail}\n\n（正在继续执行…）`
                awaitingFinal = true
                content = ''
              } else if (event.type === 'NODE_START' && awaitingFinal) {
                const hint =
                  event.name?.includes('工具') || event.name?.includes('执行')
                    ? '正在查询数据…'
                    : event.name?.includes('润色')
                      ? '正在整理最终回答…'
                      : `正在执行：${event.name}…`
                if (event.name?.includes('意图')) {
                  trialResult.value = trialResult.value.replace(/（正在继续执行…）/, `（${hint}）`)
                } else {
                  trialResult.value = trialResult.value.replace(/（[^）]*…）\s*$/, `（${hint}）`)
                }
              }
            } catch {
              /* ignore */
            }
          },
          onTrace: (json) => {
            try {
              trialTraces.value = JSON.parse(json)
            } catch {
              /* ignore */
            }
          },
          onDone: (cid) => {
            if (cid && cid !== '[DONE]') trialConversationId.value = cid
            if (!trialResult.value.trim()) {
              trialResult.value = '（无内容）'
            }
          },
        },
      )
    } else {
      const res = await runAgentTrial(trialAgent.value.id, payload)
      trialResult.value = res.content || '（无内容）'
      trialTraces.value = res.traces || []
      if (res.conversationId) trialConversationId.value = res.conversationId
    }
    trialRuns.value = await listAgentRuns(trialAgent.value.id, 8)
  } catch (e) {
    console.error(e)
  } finally {
    trialLoading.value = false
  }
}

useRouteActivate(() => {
  void fetchList()
})
</script>

<style scoped lang="scss">
.agent-home {
  --agent-ink: #0f2740;
  --agent-muted: #5b738a;
  --agent-line: rgba(26, 43, 60, 0.1);
  --agent-accent: #0891b2;
  --agent-primary: #1a7fd4;
  --agent-shelf: #f4f7fa;
  padding: 14px 12px 16px;
  min-height: 360px;
}

.agent-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--agent-line);
  border-radius: var(--app-radius-md);
  background: var(--agent-shelf);

  &__search {
    flex: 1;
    min-width: 200px;
    max-width: 360px;
  }

  &__workflow {
    width: 160px;
  }
}

.agent-empty {
  padding: 48px 0;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.agent-persona {
  position: relative;
  display: grid;
  grid-template-columns: 6px minmax(0, 1fr);
  min-height: 248px;
  border-radius: 12px;
  border: 1px solid rgba(26, 43, 60, 0.08);
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--agent-primary) 35%, transparent);
    box-shadow: 0 4px 8px rgba(15, 23, 42, 0.05), 0 12px 24px rgba(15, 23, 42, 0.07);
  }

  &.is-disabled {
    opacity: 0.78;
  }

  &.is-default {
    border-color: color-mix(in srgb, var(--agent-accent) 40%, transparent);
    box-shadow: 0 0 0 1px rgba(8, 145, 178, 0.08), 0 10px 24px rgba(8, 145, 178, 0.08);
  }

  &--react {
    --persona-tone: #7dd3fc;
  }
  &--sequential {
    --persona-tone: #5eead4;
  }
  &--routing {
    --persona-tone: #fdba74;
  }
  &--graph {
    --persona-tone: #6ee7b7;
  }

  &__rail {
    background: linear-gradient(180deg, var(--persona-tone), color-mix(in srgb, var(--persona-tone) 70%, #fff));
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px 16px 14px;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__avatar {
    position: relative;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 12px;
    color: #04202a;
    background: linear-gradient(135deg, #ecfeff, var(--persona-tone));
    box-shadow: 0 4px 12px color-mix(in srgb, var(--persona-tone) 28%, transparent);
  }

  &__pulse {
    position: absolute;
    right: -2px;
    bottom: -2px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #22c55e;
    border: 2px solid #fff;
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
    animation: agent-pulse 1.6s ease-out infinite;
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
    font-size: 16px;
    font-weight: 700;
    color: var(--agent-ink);
    line-height: 1.3;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__id-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    min-width: 0;
    flex-wrap: wrap;
  }

  &__code {
    padding: 1px 8px;
    border-radius: 4px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
    color: var(--agent-muted);
    background: #f1f6fa;
  }

  &__workflow {
    font-size: 11px;
    font-weight: 650;
    color: var(--persona-tone);
  }

  &__desc {
    margin: 0;
    min-height: 40px;
    font-size: 13px;
    line-height: 1.55;
    color: var(--agent-muted);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__specs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__spec {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 650;
    color: var(--agent-primary);
    background: rgba(26, 127, 212, 0.08);
    border: 1px solid rgba(26, 127, 212, 0.16);

    &--muted {
      color: var(--agent-muted);
      background: #f5f8fb;
      border-color: #e8eef3;
      font-weight: 500;
    }
  }

  &__skills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 28px;
  }

  &__skills-empty {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: auto;
    padding-top: 10px;
    border-top: 1px solid #eef3f7;
    flex-wrap: wrap;
  }

  &__links {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

.agent-skill {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  color: #334155;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  cursor: default;

  &.is-tool {
    color: #0e7490;
    background: rgba(8, 145, 178, 0.08);
    border-color: rgba(8, 145, 178, 0.22);
  }
}

@keyframes agent-pulse {
  0% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45); }
  70% { box-shadow: 0 0 0 7px rgba(34, 197, 94, 0); }
  100% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); }
}

.cap-tip {
  max-width: 320px;

  &__title {
    font-weight: 600;
    margin-bottom: 4px;
  }

  &__desc {
    font-size: 12px;
    line-height: 1.5;
    opacity: 0.92;
  }

  &__tools {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__tool {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;
    line-height: 1.4;

    code {
      font-size: 11px;
      color: #a5d6ff;
    }

    span {
      opacity: 0.9;
    }
  }
}

.cap-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  align-items: stretch;
}

.prompt-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}

.prompt-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}

.workflow-picker {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}

.workflow-pick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  color: inherit;
  transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;

  &--react {
    --wf-tone: #7dd3fc;
  }

  &--sequential {
    --wf-tone: #5eead4;
  }

  &--routing {
    --wf-tone: #fdba74;
  }

  &--graph {
    --wf-tone: #6ee7b7;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--wf-tone) 70%, #94a3b8);
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--wf-tone) 85%, #0891b2);
    background: color-mix(in srgb, var(--wf-tone) 22%, #fff);
    box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-tone) 35%, transparent);
  }

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    color: #0f2740;
    background: linear-gradient(135deg, #fff, var(--wf-tone));
    cursor: help;
  }

  &__label {
    font-size: 12px;
    line-height: 1.3;
    text-align: center;
    color: var(--el-text-color-primary);
  }
}

.cap-option {
  min-width: 0;
  height: 100%;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);

  &__check {
    height: auto;
    align-items: flex-start;

    :deep(.el-checkbox__label) {
      display: inline-flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      white-space: normal;
      line-height: 1.4;
    }
  }

  &__title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: 6px 0 0 24px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &__tools {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 8px 0 0 24px;
  }
}

.tool-chip {
  cursor: help;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.model-row {
  margin-bottom: 8px;
}

.model-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.trial-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);

  code {
    padding: 2px 8px;
    border-radius: 4px;
    background: var(--el-fill-color-light);
    font-size: 12px;
  }
}

.trial-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}

.trial-result {
  margin-top: 8px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  pre {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: inherit;
    font-size: 13px;
    line-height: 1.6;
  }
}

.trial-cid {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.trial-runs {
  margin-top: 12px;
}

.trial-runs__title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .agent-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style lang="scss">
@use '@/styles/variables.scss' as *;

.agent-tools-drawer {
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

.el-popper.agent-workflow-hint-popper {
  max-width: min(280px, 80vw);
}

.el-popper.agent-workflow-hint-popper,
.el-popper.agent-workflow-hint-popper .el-popper__content {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.55;
}

.workflow-hint {
  &__title {
    font-weight: 600;
    margin-bottom: 4px;
  }

  &__desc {
    margin: 0;
    font-size: 12px;
    line-height: 1.55;
    opacity: 0.92;
  }
}
</style>
