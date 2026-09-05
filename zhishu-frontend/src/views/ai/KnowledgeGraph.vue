<template>
  <ListPageShell
    class="list-page--kg"
    :loading="loading"
    :show-hero="true"
    hero-title="知识图谱"
    hero-eyebrow="智能中心"
    :hero-eyebrow-icon="Share"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      工程 / 终端 / 告警 / 巡检 / 区域 / 负责人拓扑可视化；支持路径分析、告警影响面与 Neo4j 同步。
    </template>
    <template #heroActions>
      <el-tag size="small" :type="health?.connected ? 'success' : 'danger'" effect="plain">
        Neo4j {{ health?.connected ? '已连接' : '未连接' }}
      </el-tag>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      <el-button
        v-permission="PERMISSIONS.AI_KG_SYNC"
        type="primary"
        size="small"
        :loading="syncing"
        @click="handleSync(false)"
      >
        增量同步
      </el-button>
      <el-button
        v-permission="PERMISSIONS.AI_KG_SYNC"
        size="small"
        :loading="syncing"
        @click="handleSync(true)"
      >
        全量同步
      </el-button>
    </template>

    <div class="kg-home">
      <aside class="kg-side">
        <el-form label-position="top" class="kg-form">
          <el-form-item label="工程">
            <el-select
              v-model="projectId"
              filterable
              clearable
              placeholder="全部工程"
              style="width: 100%"
              @change="onProjectChange"
            >
              <el-option
                v-for="p in projects"
                :key="p.id"
                :label="p.projectName"
                :value="p.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="展开深度">
            <el-slider
              v-model="depth"
              :min="1"
              :max="3"
              :marks="{ 1: '1', 2: '2', 3: '3' }"
              @change="loadSubgraph"
            />
          </el-form-item>
          <el-form-item label="节点类型">
            <el-checkbox-group v-model="selectedTypes" @change="loadSubgraph">
              <el-checkbox v-for="t in typeOptions" :key="t" :label="t">{{ typeLabel(t) }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="搜索实体">
            <el-input
              v-model="keyword"
              clearable
              placeholder="名称 / 编码"
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button :icon="Search" @click="handleSearch" />
              </template>
            </el-input>
          </el-form-item>
        </el-form>

        <div v-if="topology" class="kg-panel">
          <div class="kg-panel__title">工程拓扑摘要</div>
          <div v-if="!topology.found" class="kg-panel__hint">{{ topology.message || '暂无摘要' }}</div>
          <ul v-else class="kg-topo">
            <li><span>工程</span><b>{{ topology.projectName || `#${topology.projectId}` }}</b></li>
            <li><span>站点</span><b>{{ topology.terminalCount }}</b></li>
            <li><span>告警</span><b>{{ topology.openAlertCount }}</b></li>
            <li><span>巡检计划</span><b>{{ topology.planCount }}</b></li>
            <li><span>巡检任务</span><b>{{ topology.taskCount }}</b></li>
            <li><span>未关闭异常</span><b>{{ topology.openIssueCount }}</b></li>
          </ul>
        </div>

        <div class="kg-panel">
          <div class="kg-panel__title">路径分析</div>
          <div class="kg-path">
            <div class="kg-path__row">
              <span>起点</span>
              <em>{{ formatEndpoint(pathFrom) }}</em>
            </div>
            <div class="kg-path__row">
              <span>终点</span>
              <em>{{ formatEndpoint(pathTo) }}</em>
            </div>
            <div class="kg-path__actions">
              <el-button size="small" :disabled="!pathFrom || !pathTo" :loading="pathLoading" @click="runPath">
                查找路径
              </el-button>
              <el-button size="small" text @click="clearPath">清除</el-button>
            </div>
            <div v-if="pathMessage" class="kg-panel__hint">{{ pathMessage }}</div>
          </div>
        </div>

        <div class="kg-panel">
          <div class="kg-panel__title">同步水位</div>
          <div v-if="!syncStatus" class="kg-panel__hint">暂无同步状态</div>
          <template v-else>
            <div class="kg-sync-meta">
              <div>上次成功：{{ formatTime(syncStatus.lastSuccessAt) }}</div>
              <div v-if="syncStatus.lastMessage" class="kg-panel__hint">{{ syncStatus.lastMessage }}</div>
            </div>
            <div v-if="syncStatus.watermarks?.length" class="kg-wm">
              <div v-for="w in syncStatus.watermarks" :key="w.sourceTable" class="kg-wm__row">
                <span class="kg-wm__table">{{ w.sourceTable }}</span>
                <el-tag size="small" :type="wmTagType(w.lastStatus)" effect="plain">
                  {{ w.lastStatus || '—' }}
                </el-tag>
                <span class="kg-wm__time">{{ formatTime(w.lastSyncAt) }}</span>
              </div>
            </div>
          </template>
        </div>

        <div v-if="searchHits.length" class="kg-hits">
          <div class="kg-hits__title">搜索结果</div>
          <button
            v-for="hit in searchHits"
            :key="`${hit.label}:${hit.bizId}`"
            type="button"
            class="kg-hit"
            @click="focusEntity(hit.label, hit.bizId)"
          >
            <span class="kg-hit__label">{{ typeLabel(hit.label) }}</span>
            <span class="kg-hit__name">{{ hit.name || hit.code || hit.bizId }}</span>
          </button>
        </div>
      </aside>

      <main class="kg-canvas-wrap">
        <div ref="chartRef" v-loading="loading" class="kg-canvas" />
        <div v-if="!subgraph.nodes.length && !loading" class="kg-empty">
          暂无图谱数据，请先执行同步；可选择「全部工程」或单个工程查看。
        </div>
        <aside v-if="selectedNode" class="kg-detail">
          <div class="kg-detail__head">
            <div class="kg-detail__title">节点详情</div>
            <el-button text size="small" @click="selectedNode = null">关闭</el-button>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="类型">{{ typeLabel(selectedNode.label) }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ selectedNode.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="bizId">{{ selectedNode.bizId }}</el-descriptions-item>
            <el-descriptions-item label="工程ID">{{ selectedNode.projectId ?? '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="propStr(selectedNode, 'status')" label="状态">
              {{ propStr(selectedNode, 'status') }}
            </el-descriptions-item>
            <el-descriptions-item v-if="propStr(selectedNode, 'observeTime')" label="观测时间">
              {{ propStr(selectedNode, 'observeTime') }}
            </el-descriptions-item>
            <el-descriptions-item v-if="propStr(selectedNode, 'region')" label="区域">
              {{ propStr(selectedNode, 'region') }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="kg-detail__actions">
            <el-button type="primary" plain @click="expandSelected">展开邻居</el-button>
            <el-button plain @click="setPathEndpoint('from')">设为起点</el-button>
            <el-button plain @click="setPathEndpoint('to')">设为终点</el-button>
            <el-button
              v-if="selectedNode.label === 'Alert'"
              type="danger"
              plain
              :loading="impactLoading"
              @click="loadAlertImpact"
            >
              查看影响面
            </el-button>
            <el-button v-if="bizLink(selectedNode)" type="primary" link @click="openBizPage">
              打开业务页
            </el-button>
            <el-button v-if="mapLink(selectedNode)" type="primary" link @click="openMap">
              地图定位
            </el-button>
          </div>
        </aside>
      </main>
    </div>
  </ListPageShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, OfficeBuilding, Refresh, Search, Share, View } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import { getProjectListApi } from '@/api/project'
import {
  getKgAlertImpact,
  getKgHealth,
  getKgNeighbors,
  getKgPath,
  getKgStats,
  getKgSubgraph,
  getKgSyncStatus,
  getKgTopology,
  searchKgEntities,
  triggerKgSync,
} from '@/api/ai'
import { PERMISSIONS } from '@/constants/permissions'
import { resolveKgBizId, resolveKgProjectId } from '@/utils/kgIds'
import type { ProjectEntity } from '@/types/project'
import type {
  KgHealthVO,
  KgNodeVO,
  KgPathEndpoint,
  KgSearchHit,
  KgStatsVO,
  KgSubgraphVO,
  KgSyncStatusVO,
  KgTopologySummary,
} from '@/types/knowledgeGraph'

defineOptions({ name: 'AIKnowledgeGraph' })

const LABEL_MAP: Record<string, string> = {
  Project: '工程',
  Terminal: '终端',
  Alert: '告警',
  InspectionPlan: '巡检计划',
  InspectionTask: '巡检任务',
  InspectionIssue: '巡检异常',
  InspectionCheckpoint: '检查点',
  Region: '区域',
  Person: '负责人',
}

const COLOR_MAP: Record<string, string> = {
  Project: '#2563eb',
  Terminal: '#059669',
  Alert: '#dc2626',
  InspectionPlan: '#7c3aed',
  InspectionTask: '#c026d3',
  InspectionIssue: '#ea580c',
  InspectionCheckpoint: '#0891b2',
  Region: '#ca8a04',
  Person: '#4f46e5',
}

const typeOptions = Object.keys(LABEL_MAP)
const typeLabel = (t: string) => LABEL_MAP[t] || t

const router = useRouter()
const chartRef = ref<HTMLDivElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)
const loading = ref(false)
const syncing = ref(false)
const pathLoading = ref(false)
const impactLoading = ref(false)
const health = ref<KgHealthVO | null>(null)
const stats = ref<KgStatsVO | null>(null)
const syncStatus = ref<KgSyncStatusVO | null>(null)
const topology = ref<KgTopologySummary | null>(null)
const projects = ref<ProjectEntity[]>([])
const projectId = ref<number | null>(null)
const depth = ref(1)
const selectedTypes = ref<string[]>([...typeOptions])
const keyword = ref('')
const searchHits = ref<KgSearchHit[]>([])
const subgraph = ref<KgSubgraphVO>({ nodes: [], edges: [] })
const selectedNode = ref<KgNodeVO | null>(null)
const pathFrom = ref<KgPathEndpoint | null>(null)
const pathTo = ref<KgPathEndpoint | null>(null)
const pathMessage = ref('')
const highlightNodeIds = ref<Set<string>>(new Set())
const highlightEdgeKeys = ref<Set<string>>(new Set())

const viewTitle = computed(() => {
  if (projectId.value == null) return '全部工程'
  const p = projects.value.find((x) => x.id === projectId.value)
  return p?.projectName || `工程 #${projectId.value}`
})

const heroMetrics = computed(() => [
  {
    key: 'nodes',
    label: '节点',
    value: stats.value?.nodeCount ?? '—',
    icon: Share,
    accent: 'primary' as const,
  },
  {
    key: 'edges',
    label: '关系',
    value: stats.value?.edgeCount ?? '—',
    icon: Connection,
    accent: 'primary' as const,
  },
  {
    key: 'view',
    label: '当前视图节点',
    value: subgraph.value.nodes.length,
    icon: View,
    accent: 'success' as const,
  },
  {
    key: 'scope',
    label: '当前范围',
    value: viewTitle.value,
    icon: OfficeBuilding,
    accent: 'primary' as const,
  },
])

function formatEndpoint(ep: KgPathEndpoint | null) {
  if (!ep) return '未选择（在节点详情中设置）'
  return `${typeLabel(ep.label)} · ${ep.name || ep.bizId}`
}

function formatTime(v?: string | null) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function wmTagType(status?: string | null): 'success' | 'danger' | 'warning' | 'info' {
  const s = (status || '').toUpperCase()
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s) return 'warning'
  return 'info'
}

function propStr(node: KgNodeVO, key: string) {
  const v = node.properties?.[key]
  if (v == null || v === '') return ''
  return String(v)
}

function edgeKey(source: string, target: string) {
  return source < target ? `${source}|${target}` : `${target}|${source}`
}

async function refreshAll() {
  loading.value = true
  try {
    const [h, s, st] = await Promise.all([
      getKgHealth().catch(() => null),
      getKgStats().catch(() => null),
      getKgSyncStatus().catch(() => null),
    ])
    health.value = h
    stats.value = s
    syncStatus.value = st
    await Promise.all([loadSubgraph(), loadTopology()])
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    projects.value = (await getProjectListApi()) || []
  } catch {
    projects.value = []
  }
}

async function loadTopology() {
  if (projectId.value == null) {
    topology.value = null
    return
  }
  try {
    topology.value = await getKgTopology(projectId.value)
  } catch {
    topology.value = null
  }
}

function onProjectChange() {
  if (projectId.value == null && depth.value > 2) {
    depth.value = 1
  }
  clearPathHighlight()
  loadSubgraph()
  loadTopology()
}

function normalizeSubgraph(g: KgSubgraphVO): KgSubgraphVO {
  return {
    nodes: (g.nodes || []).map((n) => {
      const bizId = resolveKgBizId(n)
      return {
        ...n,
        bizId: bizId || n.bizId,
        projectId: n.projectId == null || n.projectId === '' ? n.projectId : String(n.projectId),
      }
    }),
    edges: g.edges || [],
  }
}

async function loadSubgraph() {
  loading.value = true
  try {
    const types =
      selectedTypes.value.length && selectedTypes.value.length < typeOptions.length
        ? selectedTypes.value.join(',')
        : undefined
    subgraph.value = normalizeSubgraph(
      await getKgSubgraph({
        projectId: projectId.value ?? undefined,
        depth: depth.value,
        types,
      }),
    )
    await nextTick()
    renderChart()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '加载子图失败'
    ElMessage.error(msg)
    subgraph.value = { nodes: [], edges: [] }
    renderChart()
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  if (!keyword.value.trim()) {
    searchHits.value = []
    return
  }
  try {
    searchHits.value = await searchKgEntities(keyword.value.trim(), 20)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '搜索失败')
  }
}

async function focusEntity(label: string, bizId: number | string) {
  const id = String(bizId)
  if (!id) {
    ElMessage.warning('无效的节点 ID')
    return
  }
  try {
    const res = await getKgNeighbors(label, id, 1)
    if (!res.found) {
      ElMessage.warning(res.message || '未找到节点')
      return
    }
    clearPathHighlight()
    subgraph.value = normalizeSubgraph(res.subgraph)
    selectedNode.value =
      subgraph.value.nodes.find((n) => n.label === label && resolveKgBizId(n) === id) || null
    await nextTick()
    renderChart()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '展开失败')
  }
}

async function expandSelected() {
  if (!selectedNode.value) return
  const bizId = resolveKgBizId(selectedNode.value)
  if (!bizId) {
    ElMessage.warning('无法解析节点 bizId，请刷新后重试')
    return
  }
  await focusEntity(selectedNode.value.label, bizId)
}

function setPathEndpoint(which: 'from' | 'to') {
  if (!selectedNode.value) return
  const bizId = resolveKgBizId(selectedNode.value)
  if (!bizId) {
    ElMessage.warning('无法解析节点 bizId')
    return
  }
  const ep: KgPathEndpoint = {
    label: selectedNode.value.label,
    bizId,
    name: selectedNode.value.name,
  }
  if (which === 'from') pathFrom.value = ep
  else pathTo.value = ep
  pathMessage.value = ''
}

function clearPath() {
  pathFrom.value = null
  pathTo.value = null
  pathMessage.value = ''
  clearPathHighlight()
  renderChart()
}

function clearPathHighlight() {
  highlightNodeIds.value = new Set()
  highlightEdgeKeys.value = new Set()
}

async function runPath() {
  if (!pathFrom.value || !pathTo.value) return
  pathLoading.value = true
  try {
    const res = await getKgPath({
      fromLabel: pathFrom.value.label,
      fromBizId: pathFrom.value.bizId,
      toLabel: pathTo.value.label,
      toBizId: pathTo.value.bizId,
    })
    if (!res.found) {
      pathMessage.value = res.message || '未找到路径'
      ElMessage.warning(pathMessage.value)
      return
    }
    pathMessage.value = `路径长度 ${res.nodes.length} 节点 / ${res.edges.length} 边`
    clearPathHighlight()
    subgraph.value = normalizeSubgraph({ nodes: res.nodes, edges: res.edges })
    highlightNodeIds.value = new Set(subgraph.value.nodes.map((n) => n.id))
    highlightEdgeKeys.value = new Set(subgraph.value.edges.map((e) => edgeKey(e.source, e.target)))
    await nextTick()
    renderChart()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '路径查询失败')
  } finally {
    pathLoading.value = false
  }
}

async function loadAlertImpact() {
  if (!selectedNode.value || selectedNode.value.label !== 'Alert') return
  const bizId = resolveKgBizId(selectedNode.value)
  if (!bizId) {
    ElMessage.warning('无法解析告警 bizId')
    return
  }
  impactLoading.value = true
  try {
    const res = await getKgAlertImpact(bizId, 2)
    if (!res.found) {
      ElMessage.warning(res.message || '无影响面数据')
      return
    }
    clearPathHighlight()
    subgraph.value = normalizeSubgraph(res.subgraph)
    highlightNodeIds.value = new Set(res.subgraph.nodes.map((n) => n.id))
    await nextTick()
    renderChart()
    ElMessage.success(res.message || '已展示告警影响面')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '影响面加载失败')
  } finally {
    impactLoading.value = false
  }
}

function bizLink(node: KgNodeVO): string | null {
  const bizId = resolveKgBizId(node)
  switch (node.label) {
    case 'Project':
      return `/apps/archive/project-ledger?highlightId=${bizId}`
    case 'Terminal':
      return `/terminal/list?highlightId=${bizId}`
    case 'Alert':
      return `/data/alerts?highlightId=${bizId}`
    case 'InspectionPlan':
    case 'InspectionTask':
    case 'InspectionIssue':
    case 'InspectionCheckpoint':
      return `/apps/inspection/manage?highlightId=${bizId}&type=${node.label}`
    default:
      return null
  }
}

function mapLink(node: KgNodeVO): { projectId?: string; terminalId?: string } | null {
  if (node.label === 'Terminal') {
    const terminalId = resolveKgBizId(node)
    const projectId = resolveKgProjectId(node) || undefined
    if (!terminalId && !projectId) return null
    return { terminalId: terminalId || undefined, projectId }
  }
  if (node.label === 'Project') {
    const projectId = resolveKgBizId(node)
    return projectId ? { projectId } : null
  }
  if (node.label === 'Alert') {
    // 告警挂在终端上：优先用 properties 中的关联，否则按工程缩放
    const props = node.properties || {}
    const terminalId =
      props.terminalId != null && props.terminalId !== ''
        ? String(props.terminalId)
        : undefined
    const projectId = resolveKgProjectId(node) || undefined
    if (!terminalId && !projectId) return null
    return { terminalId, projectId }
  }
  return null
}

function openBizPage() {
  if (!selectedNode.value) return
  const link = bizLink(selectedNode.value)
  if (!link) return
  const { href } = router.resolve(link)
  window.open(href, '_blank', 'noopener,noreferrer')
}

function openMap() {
  if (!selectedNode.value) return
  const link = mapLink(selectedNode.value)
  if (!link) return
  const query: Record<string, string> = {}
  if (link.projectId) query.projectId = String(link.projectId)
  if (link.terminalId) query.terminalId = String(link.terminalId)
  const { href } = router.resolve({
    path: '/map-overview/2d',
    query,
  })
  window.open(href, '_blank', 'noopener,noreferrer')
}

async function handleSync(full: boolean) {
  syncing.value = true
  try {
    const res = await triggerKgSync(full)
    if (res.success) {
      ElMessage.success(res.message || '同步成功')
      await refreshAll()
    } else {
      ElMessage.warning(res.message || '同步未完成')
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '同步失败')
  } finally {
    syncing.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  chart.value ??= echarts.init(chartRef.value)
  const categories = typeOptions.map((name) => ({ name }))
  const catIndex = Object.fromEntries(typeOptions.map((t, i) => [t, i]))
  const hlNodes = highlightNodeIds.value
  const hlEdges = highlightEdgeKeys.value
  const option: echarts.EChartsOption = {
    tooltip: {
      formatter: (p: unknown) => {
        const data = (p as { data?: { name?: string } }).data
        if (!data) return ''
        return data.name || ''
      },
    },
    legend: [{ data: typeOptions.map(typeLabel), orient: 'horizontal', bottom: 0 }],
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        categories,
        label: { show: true, position: 'right', formatter: '{b}', fontSize: 11 },
        force: { repulsion: 180, edgeLength: [60, 140] },
        data: subgraph.value.nodes.map((n) => {
          const highlighted = hlNodes.size > 0 && hlNodes.has(n.id)
          return {
            id: n.id,
            name: n.name || `${typeLabel(n.label)}#${n.bizId}`,
            category: catIndex[n.label] ?? 0,
            symbolSize: n.label === 'Project' ? 42 : n.label === 'Terminal' ? 28 : 20,
            itemStyle: {
              color: COLOR_MAP[n.label] || '#64748b',
              borderColor: highlighted ? '#f59e0b' : undefined,
              borderWidth: highlighted ? 3 : 0,
              opacity: hlNodes.size > 0 && !highlighted ? 0.35 : 1,
            },
            raw: n,
          }
        }),
        links: subgraph.value.edges.map((e) => {
          const key = edgeKey(e.source, e.target)
          const highlighted = hlEdges.size > 0 && hlEdges.has(key)
          return {
            source: e.source,
            target: e.target,
            label: { show: false },
            lineStyle: {
              color: highlighted ? '#f59e0b' : '#94a3b8',
              width: highlighted ? 3 : 1.5,
              curveness: 0.08,
              opacity: hlEdges.size > 0 && !highlighted ? 0.25 : 0.7,
            },
          }
        }),
        lineStyle: { opacity: 0.7, width: 1.5 },
        emphasis: { focus: 'adjacency' },
      },
    ],
  }
  chart.value.setOption(option, true)
  chart.value.off('click')
  chart.value.on('click', (params) => {
    const raw = (params.data as { raw?: KgNodeVO } | undefined)?.raw
    if (!raw) return
    const bizId = resolveKgBizId(raw)
    selectedNode.value = {
      ...raw,
      bizId: bizId || raw.bizId,
      projectId: raw.projectId == null || raw.projectId === '' ? raw.projectId : String(raw.projectId),
    }
  })
}

function onResize() {
  chart.value?.resize()
}

let resizeObserver: ResizeObserver | null = null

onMounted(async () => {
  await loadProjects()
  await refreshAll()
  window.addEventListener('resize', onResize)
  if (chartRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => onResize())
    resizeObserver.observe(chartRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  chart.value?.dispose()
  chart.value = null
})
</script>

<style scoped lang="scss">
.list-page--kg {
  height: calc(100vh - 120px);
  min-height: 640px;
}

.list-page--kg :deep(.list-page__panel) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
  border: none;
  box-shadow: none;
}

.list-page--kg :deep(.list-page__table) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.kg-home {
  --kg-line: rgba(26, 43, 60, 0.1);
  --kg-shelf: #fff;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  padding: 0;
}

.kg-side {
  min-height: 0;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--kg-line);
  border-radius: var(--app-radius-md);
  background: var(--kg-shelf);
  box-shadow: var(--app-shadow-sm, 0 1px 2px rgba(15, 23, 42, 0.04));
}

.kg-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.kg-panel {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.kg-panel__title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.kg-panel__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.kg-topo {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.kg-topo li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.kg-topo span {
  color: var(--el-text-color-secondary);
}

.kg-path__row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 6px;
}

.kg-path__row span {
  flex: 0 0 36px;
  color: var(--el-text-color-secondary);
}

.kg-path__row em {
  font-style: normal;
  word-break: break-all;
}

.kg-path__actions {
  display: flex;
  gap: 6px;
  margin: 8px 0;
}

.kg-sync-meta {
  font-size: 12px;
  margin-bottom: 8px;
}

.kg-wm {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 180px;
  overflow: auto;
}

.kg-wm__row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 8px;
  font-size: 11px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--app-radius-md);
}

.kg-wm__table {
  grid-column: 1 / -1;
  font-weight: 500;
  word-break: break-all;
}

.kg-wm__time {
  color: var(--el-text-color-secondary);
  text-align: right;
}

.kg-hits {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kg-hits__title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kg-hit {
  text-align: left;
  border: 1px solid var(--el-border-color-lighter);
  background: #fff;
  border-radius: var(--app-radius-md);
  padding: 8px 10px;
  cursor: pointer;
}

.kg-hit:hover {
  border-color: var(--el-color-primary);
}

.kg-hit__label {
  display: block;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.kg-hit__name {
  font-size: 13px;
}

.kg-canvas-wrap {
  position: relative;
  min-width: 0;
  min-height: 0;
  height: 100%;
  border: 1px solid var(--kg-line);
  border-radius: var(--app-radius-md);
  background: #fff;
  overflow: hidden;
  box-shadow: var(--app-shadow-sm, 0 1px 2px rgba(15, 23, 42, 0.04));
}

.kg-canvas {
  width: 100%;
  height: 100%;
}

.kg-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--el-text-color-secondary);
  pointer-events: none;
}

.kg-detail {
  position: absolute;
  top: 12px;
  right: 12px;
  bottom: 12px;
  width: min(300px, calc(100% - 24px));
  z-index: 2;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--app-radius-md);
  padding: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  overflow: auto;
}

.kg-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.kg-detail__title {
  font-weight: 600;
}

.kg-detail__actions {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kg-detail__actions .el-button {
  width: 100%;
  margin: 0;
}

@media (max-width: 1100px) {
  .list-page--kg {
    height: auto;
    min-height: calc(100vh - 120px);
  }

  .kg-home {
    grid-template-columns: 1fr;
    min-height: 560px;
  }

  .kg-canvas-wrap {
    min-height: 520px;
  }
}
</style>
