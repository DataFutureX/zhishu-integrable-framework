<template>
  <div class="graph-editor" v-loading="loading">
    <header class="graph-editor__bar">
      <div class="graph-editor__title">
        <el-button :icon="ArrowLeft" text @click="goBack">返回</el-button>
        <h2>{{ agentName || '工作流编排' }}</h2>
        <el-tag size="small" effect="plain">Graph</el-tag>
      </div>
      <div class="graph-editor__actions">
        <el-dropdown trigger="click" @command="applyTemplate">
          <el-button>从模板生成</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="REACT">ReAct</el-dropdown-item>
              <el-dropdown-item command="SEQUENTIAL">顺序多步</el-dropdown-item>
              <el-dropdown-item command="ROUTING">路由分发</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="runValidate" :loading="validating">校验</el-button>
        <el-button type="primary" :loading="saving" @click="runSave">保存</el-button>
      </div>
    </header>

    <div class="graph-editor__body">
      <aside class="graph-editor__palette">
        <p class="palette-title">节点</p>
        <div
          v-for="item in palette"
          :key="item.type"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart($event, item.type)"
        >
          {{ item.label }}
        </div>
        <p class="palette-hint">拖到画布添加；选中节点可编辑属性</p>
      </aside>

      <div class="graph-editor__canvas" @drop="onDrop" @dragover.prevent>
        <VueFlow
          :nodes="(nodes as any)"
          :edges="(edges as any)"
          :node-types="nodeTypes"
          fit-view-on-init
          :default-viewport="{ zoom: 0.9 }"
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @connect="onConnect"
        >
          <Background />
          <Controls />
        </VueFlow>
      </div>

      <aside class="graph-editor__props">
        <template v-if="selectedNode">
          <h3>节点属性</h3>
          <el-form label-width="72px" size="small">
            <el-form-item label="ID">
              <el-input :model-value="selectedNode.id" disabled />
            </el-form-item>
            <el-form-item label="类型">
              <el-select
                v-model="selectedNode.data.nodeType"
                style="width: 100%"
                @change="onNodeTypeChange"
              >
                <el-option
                  v-for="item in palette"
                  :key="item.type"
                  :label="item.label"
                  :value="item.type"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="名称">
              <el-select
                v-model="selectedNode.data.label"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                placeholder="选择或输入显示名称"
                @change="syncNodeCanvasLabel"
              >
                <el-option
                  v-for="name in namePresetsForSelected"
                  :key="name"
                  :label="name"
                  :value="name"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="showPromptField" label="提示词">
              <el-input v-model="selectedNode.data.systemPrompt" type="textarea" :rows="6" />
            </el-form-item>
            <el-form-item v-if="selectedNodeType === 'CONDITIONAL'" label="匹配输入">
              <el-select v-model="selectedNode.data.inputSource" style="width: 100%" placeholder="默认上一节点输出">
                <el-option label="上一节点输出 (lastOutput)" value="lastOutput" />
                <el-option label="用户原文 (userMessage)" value="userMessage" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedNodeType === 'TOOL_AGENT'" label="能力">
              <el-select
                v-model="selectedNode.data.capabilities"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                style="width: 100%"
                placeholder="选择能力"
              >
                <el-option
                  v-for="cap in capabilities"
                  :key="cap.code"
                  :label="cap.label"
                  :value="cap.code"
                />
              </el-select>
            </el-form-item>
            <el-button type="danger" link @click="removeSelectedNode">删除节点</el-button>
          </el-form>
        </template>
        <template v-else-if="selectedEdge">
          <h3>边属性</h3>
          <el-form label-width="72px" size="small">
            <el-form-item label="条件">
              <el-select
                v-model="selectedEdge.data.condition"
                filterable
                allow-create
                clearable
                default-first-option
                style="width: 100%"
                placeholder="ROUTER/CONDITIONAL 分支条件（可自定义）"
              >
                <el-option
                  v-for="item in edgeConditionPresets"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="标签">
              <el-select
                v-model="edgeLabelModel"
                filterable
                allow-create
                clearable
                default-first-option
                style="width: 100%"
                placeholder="选择或输入边标签"
              >
                <el-option
                  v-for="item in edgeLabelPresets"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
            <el-button type="danger" link @click="removeSelectedEdge">删除边</el-button>
          </el-form>
        </template>
        <template v-else>
          <p class="props-empty">选中节点或边以编辑</p>
        </template>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIAgentGraphEditor' })

import { computed, markRaw, onMounted, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  VueFlow,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  type Connection,
  type EdgeChange,
  type NodeChange,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import GraphWorkflowNode from '@/components/ai/GraphWorkflowNode.vue'
import {
  compileWorkflowTemplate,
  getAgent,
  getAgentGraph,
  listAgentCapabilities,
  saveAgentGraph,
  validateAgentGraph,
} from '@/api/ai'
import type { CapabilityVO, WorkflowGraphDTO } from '@/types/aiAgent'

/** 避免 @vue-flow/core 的 GraphNode/GraphEdge 泛型在 vue-tsc 下过深实例化 */
type FlowNode = {
  id: string
  type?: string
  position: { x: number; y: number }
  label?: string
  data: Record<string, any>
  [key: string]: any
}
type FlowEdge = {
  id: string
  source: string
  target: string
  label?: string
  data: Record<string, any>
  [key: string]: any
}

const nodeTypes = {
  workflow: markRaw(GraphWorkflowNode),
}

const route = useRoute()
const router = useRouter()
const agentId = computed(() => Number(route.params.id))
const agentName = ref('')
const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const capabilities = ref<CapabilityVO[]>([])
const nodes = ref<FlowNode[]>([])
const edges = ref<FlowEdge[]>([])
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)

const capabilityLabelMap = computed(() =>
  Object.fromEntries(capabilities.value.map((c) => [c.code, c.label])),
)
provide('graphCapabilityLabelMap', capabilityLabelMap)

const palette = [
  { type: 'START', label: 'START 开始', defaultName: '开始' },
  { type: 'LLM', label: 'LLM 对话', defaultName: '对话' },
  { type: 'TOOL_AGENT', label: 'TOOL_AGENT 工具', defaultName: '工具执行' },
  { type: 'ROUTER', label: 'ROUTER 路由', defaultName: '路由' },
  { type: 'CONDITIONAL', label: 'CONDITIONAL 条件', defaultName: '条件分支' },
  { type: 'END', label: 'END 结束', defaultName: '结束' },
]

const namePresets: Record<string, string[]> = {
  START: ['开始'],
  LLM: ['对话', '意图澄清', '结果润色', '知识问答'],
  TOOL_AGENT: ['工具执行', '执行', '数据查询'],
  ROUTER: ['路由', '入口路由'],
  CONDITIONAL: ['条件分支', '规则分支'],
  END: ['结束'],
}

const edgeConditionPresets = [
  { label: '默认（无条件）', value: '' },
  { label: 'DATA', value: 'DATA' },
  { label: 'KNOWLEDGE', value: 'KNOWLEDGE' },
  { label: '告警', value: '告警' },
  { label: 'contains:告警', value: 'contains:告警' },
  { label: 'contains:离线', value: 'contains:离线' },
  { label: '!contains:告警', value: '!contains:告警' },
  { label: 'equals:DATA', value: 'equals:DATA' },
]

const edgeLabelPresets = ['', 'DATA', 'KNOWLEDGE', '默认', '告警']

const selectedNode = computed((): FlowNode | null => {
  const id = selectedNodeId.value
  if (!id) return null
  const found = nodes.value.find((n) => n.id === id)
  if (!found) return null
  if (!found.data) found.data = {}
  return found
})
const selectedEdge = computed((): FlowEdge | null => {
  const id = selectedEdgeId.value
  if (!id) return null
  const found = edges.value.find((e) => e.id === id)
  if (!found) return null
  if (!found.data) found.data = { condition: '' }
  return found
})

const selectedNodeType = computed(() => {
  const n = selectedNode.value
  if (!n) return ''
  return String(n.data?.nodeType || '')
})

const showPromptField = computed(() =>
  ['LLM', 'TOOL_AGENT', 'ROUTER'].includes(selectedNodeType.value),
)

const namePresetsForSelected = computed(() => {
  const type = selectedNodeType.value
  const presets = namePresets[type] || []
  const current = selectedNode.value?.data?.label
  if (current && typeof current === 'string' && !presets.includes(current)) {
    return [current, ...presets]
  }
  return presets
})

const edgeLabelModel = computed({
  get: () => {
    const label = selectedEdge.value?.label
    return typeof label === 'string' ? label : ''
  },
  set: (val: string) => {
    const edge = selectedEdge.value
    if (!edge) return
    edge.label = val || ''
  },
})

function onNodeTypeChange(type: string) {
  const node = selectedNode.value
  if (!node?.data) return
  const meta = palette.find((p) => p.type === type)
  const presets = namePresets[type] || []
  const current = String(node.data.label || '')
  // 名称仍是旧类型默认值时，随类型切换同步
  if (!current || Object.values(namePresets).flat().includes(current)) {
    node.data.label = meta?.defaultName || presets[0] || type
  }
  if (type !== 'TOOL_AGENT') {
    node.data.capabilities = []
  } else if (!Array.isArray(node.data.capabilities)) {
    node.data.capabilities = []
  }
  if (type === 'CONDITIONAL') {
    if (!node.data.inputSource) {
      node.data.inputSource = 'lastOutput'
    }
  }
  syncNodeCanvasLabel()
}

function syncNodeCanvasLabel() {
  const node = selectedNode.value
  if (!node?.data) return
  const text = String(node.data.label || node.data.nodeType || node.id)
  node.label = text
}

function goBack() {
  router.push('/ai/agents')
}

function onNodesChange(changes: NodeChange[]) {
  nodes.value = (applyNodeChanges as (...args: unknown[]) => FlowNode[])(changes, nodes.value)
}

function onEdgesChange(changes: EdgeChange[]) {
  edges.value = (applyEdgeChanges as (...args: unknown[]) => FlowEdge[])(changes, edges.value)
}

function onConnect(connection: Connection) {
  edges.value = (addEdge as (...args: unknown[]) => FlowEdge[])(
    {
      ...connection,
      id: `e_${connection.source}_${connection.target}_${Date.now().toString(36)}`,
      data: { condition: '' },
    },
    edges.value,
  )
}

function onDragStart(event: DragEvent, type: string) {
  event.dataTransfer?.setData('application/vueflow', type)
  event.dataTransfer!.effectAllowed = 'move'
}

function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('application/vueflow')
  if (!type) return
  const meta = palette.find((p) => p.type === type)
  const displayName = meta?.defaultName || type
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = { x: event.clientX - bounds.left - 60, y: event.clientY - bounds.top - 20 }
  const id = `${type.toLowerCase()}_${Date.now().toString(36)}`
  nodes.value = [
    ...nodes.value,
    {
      id,
      type: 'workflow',
      position,
      label: displayName,
      data: {
        nodeType: type,
        label: displayName,
        systemPrompt: '',
        capabilities: [] as string[],
        ...(type === 'CONDITIONAL' ? { inputSource: 'lastOutput' } : {}),
      },
    },
  ]
  selectedNodeId.value = id
  selectedEdgeId.value = null
}

function onNodeClick(e: { node: FlowNode } | any) {
  selectedNodeId.value = e.node.id
  selectedEdgeId.value = null
}

function onEdgeClick(e: { edge: FlowEdge } | any) {
  const edge = e.edge as FlowEdge
  if (!edge.data) {
    edge.data = { condition: '' }
  } else if (edge.data.condition == null) {
    edge.data.condition = ''
  }
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
}

function removeSelectedNode() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selectedNodeId.value = null
}

function removeSelectedEdge() {
  if (!selectedEdgeId.value) return
  edges.value = edges.value.filter((e) => e.id !== selectedEdgeId.value)
  selectedEdgeId.value = null
}

function fromDto(graph: WorkflowGraphDTO) {
  nodes.value = (graph.nodes || []).map((n) => ({
    id: n.id,
    type: 'workflow',
    position: { x: n.positionX ?? 100, y: n.positionY ?? 100 },
    label: n.label || n.type,
    data: {
      nodeType: n.type,
      label: n.label || n.type,
      systemPrompt: String((n.data as any)?.systemPrompt || ''),
      capabilities: Array.isArray((n.data as any)?.capabilities)
        ? ([...(n.data as any).capabilities] as string[])
        : [],
    },
  }))
  edges.value = (graph.edges || []).map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label || e.condition || '',
    data: { condition: e.condition || '' },
  }))
}

function toDto(): WorkflowGraphDTO {
  return {
    version: 1,
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: String(n.data?.nodeType || 'LLM'),
      label: String(n.data?.label || n.label || n.id),
      positionX: n.position.x,
      positionY: n.position.y,
      data: {
        systemPrompt: n.data?.systemPrompt || '',
        capabilities: n.data?.capabilities || [],
      },
    })),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      condition: (e.data as any)?.condition || null,
      label: typeof e.label === 'string' ? e.label : null,
    })),
  }
}

async function load() {
  if (!agentId.value) return
  loading.value = true
  try {
    const [agent, graph, caps] = await Promise.all([
      getAgent(agentId.value),
      getAgentGraph(agentId.value),
      listAgentCapabilities(),
    ])
    agentName.value = agent.name
    capabilities.value = caps
    fromDto(graph)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载编排失败')
  } finally {
    loading.value = false
  }
}

async function applyTemplate(type: string) {
  try {
    const graph = await compileWorkflowTemplate(type, agentId.value)
    fromDto(graph)
    ElMessage.success(`已从 ${type} 生成 Graph，请保存`)
  } catch (e: any) {
    ElMessage.error(e?.message || '模板编译失败')
  }
}

async function runValidate() {
  validating.value = true
  try {
    const result = await validateAgentGraph(agentId.value, toDto())
    if (result.valid) {
      ElMessage.success('校验通过')
    } else {
      ElMessage.warning(result.errors?.join('；') || '校验失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '校验失败')
  } finally {
    validating.value = false
  }
}

async function runSave() {
  saving.value = true
  try {
    const result = await validateAgentGraph(agentId.value, toDto())
    if (!result.valid) {
      ElMessage.warning(result.errors?.join('；') || '校验失败，未保存')
      return
    }
    await saveAgentGraph(agentId.value, toDto())
    ElMessage.success('编排已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.graph-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 560px;
  background: var(--el-bg-color);
  border-radius: 8px;
  overflow: hidden;
}
.graph-editor__bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.graph-editor__title {
  display: flex;
  align-items: center;
  gap: 8px;
  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }
}
.graph-editor__actions {
  display: flex;
  gap: 8px;
}
.graph-editor__body {
  flex: 1;
  display: grid;
  grid-template-columns: 180px 1fr 260px;
  min-height: 0;
}
.graph-editor__palette,
.graph-editor__props {
  padding: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
  overflow: auto;
}
.graph-editor__props {
  border-right: none;
  border-left: 1px solid var(--el-border-color-lighter);
}
.palette-title {
  margin: 0 0 8px;
  font-weight: 600;
  font-size: 13px;
}
.palette-item {
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: grab;
  font-size: 12px;
  background: var(--el-fill-color-blank);
}
.palette-hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.graph-editor__canvas {
  min-height: 0;
  height: 100%;
}
.props-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
:deep(.vue-flow) {
  height: 100%;
  width: 100%;
}
</style>
