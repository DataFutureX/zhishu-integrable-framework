<template>
  <el-collapse v-if="traces?.length" v-model="activeNames" class="trace-panel">
    <el-collapse-item name="traces">
      <template #title>
        <div class="trace-panel__head">
          <span class="trace-panel__title">执行轨迹</span>
          <el-tag size="small" type="info" effect="plain" round>{{ traces!.length }} 步</el-tag>
          <span v-if="totalDurationMs != null" class="trace-panel__sum">合计 {{ formatMs(totalDurationMs) }}</span>
        </div>
      </template>

      <ol class="trace-timeline">
        <li
          v-for="(t, idx) in traces"
          :key="idx"
          class="trace-step"
          :class="[`trace-step--${tone(t.type)}`, { 'trace-step--has-detail': !!t.detail }]"
        >
          <div class="trace-step__rail" aria-hidden="true">
            <span class="trace-step__dot" />
          </div>
          <div class="trace-step__body">
            <div class="trace-step__meta">
              <el-tag size="small" :type="tagType(t.type)" effect="plain" class="trace-step__type">
                {{ typeLabel(t.type) }}
              </el-tag>
              <strong class="trace-step__name" :title="t.name">{{ t.name || '—' }}</strong>
              <span v-if="t.durationMs != null" class="trace-step__ms">{{ formatMs(t.durationMs) }}</span>
              <span v-if="t.timestamp" class="trace-step__time">{{ formatClock(t.timestamp) }}</span>
            </div>
            <div v-if="t.detail" class="trace-step__detail">
              <pre class="trace-step__pre">{{ formatDetail(t.detail) }}</pre>
            </div>
          </div>
        </li>
      </ol>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AgentTraceEvent } from '@/types/aiChat'

const props = defineProps<{
  traces?: AgentTraceEvent[] | null
  /** 默认是否展开；变更时同步展开/收起（便于演示自动收起） */
  defaultExpand?: boolean
}>()

const activeNames = ref<string[]>(props.defaultExpand ? ['traces'] : [])

watch(
  () => props.defaultExpand,
  (expand) => {
    activeNames.value = expand ? ['traces'] : []
  },
)

const totalDurationMs = computed(() => {
  const list = props.traces
  if (!list?.length) return null
  let sum = 0
  let has = false
  for (const t of list) {
    if (t.durationMs != null && t.durationMs >= 0) {
      sum += t.durationMs
      has = true
    }
  }
  return has ? sum : null
})

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    NODE_START: '节点开始',
    NODE_END: '节点结束',
    TOOL_CALL: '调用工具',
    TOOL_RESULT: '工具结果',
    ROUTE: '路由分支',
  }
  return map[type] || type
}

function tagType(type: string): 'success' | 'warning' | 'info' | 'primary' | 'danger' | undefined {
  if (type === 'TOOL_CALL' || type === 'TOOL_RESULT') return 'warning'
  if (type === 'ROUTE') return 'success'
  if (type === 'NODE_END') return 'info'
  if (type === 'NODE_START') return 'primary'
  return undefined
}

function tone(type: string): string {
  if (type === 'TOOL_CALL' || type === 'TOOL_RESULT') return 'tool'
  if (type === 'ROUTE') return 'route'
  if (type === 'NODE_END') return 'end'
  if (type === 'NODE_START') return 'start'
  return 'default'
}

function formatMs(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`
  const m = Math.floor(ms / 60_000)
  const s = Math.round((ms % 60_000) / 1000)
  return `${m}m${s}s`
}

function formatClock(ts: number): string {
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** 尝试把 JSON 详情美化，失败则原样返回 */
function formatDetail(detail: string): string {
  const raw = detail.trim()
  if (!raw) return ''
  if ((raw.startsWith('{') && raw.endsWith('}')) || (raw.startsWith('[') && raw.endsWith(']'))) {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
      /* ignore */
    }
  }
  return detail
}
</script>

<style scoped lang="scss">
.trace-panel {
  margin-top: 10px;
  border: none;
  --el-collapse-header-height: 36px;
  background: transparent;

  :deep(.el-collapse-item__header) {
    border: none;
    background: color-mix(in srgb, var(--el-fill-color-light) 80%, transparent);
    border-radius: 8px;
    padding: 0 10px;
    height: 36px;
    line-height: 36px;
  }

  :deep(.el-collapse-item__wrap) {
    border: none;
    background: transparent;
  }

  :deep(.el-collapse-item__content) {
    padding: 10px 4px 2px 4px;
  }

  :deep(.el-collapse-item__arrow) {
    margin-right: 4px;
  }
}

.trace-panel__head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.trace-panel__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.trace-panel__sum {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
}

.trace-timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-step {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  column-gap: 10px;
  position: relative;

  &:not(:last-child) .trace-step__rail::after {
    content: '';
    position: absolute;
    left: 7px;
    top: 18px;
    bottom: -2px;
    width: 2px;
    background: var(--el-border-color-lighter);
    border-radius: 1px;
  }
}

.trace-step__rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 6px;
}

.trace-step__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--el-color-info-light-5);
  border: 2px solid var(--el-color-info);
  box-sizing: border-box;
  z-index: 1;
}

.trace-step--start .trace-step__dot {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-7);
}
.trace-step--end .trace-step__dot {
  border-color: var(--el-color-info);
  background: var(--el-color-info-light-7);
}
.trace-step--tool .trace-step__dot {
  border-color: var(--el-color-warning);
  background: var(--el-color-warning-light-7);
}
.trace-step--route .trace-step__dot {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-7);
}

.trace-step__body {
  min-width: 0;
  padding: 2px 0 12px;
}

.trace-step__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  min-width: 0;
}

.trace-step__type {
  flex-shrink: 0;
}

.trace-step__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.trace-step__ms {
  margin-left: auto;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  padding: 1px 6px;
  border-radius: 999px;
  flex-shrink: 0;
}

.trace-step__time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.trace-step__detail {
  margin-top: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.trace-step__pre {
  margin: 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  max-height: 220px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
}

/* 窄气泡内：耗时不必挤到最右 */
@media (max-width: 640px) {
  .trace-step__ms {
    margin-left: 0;
  }
}
</style>
