<template>
  <div class="wf-node" :class="[`wf-node--${nodeType.toLowerCase()}`, { 'wf-node--selected': selected }]">
    <Handle type="target" :position="Position.Left" class="wf-handle" />
    <div class="wf-node__head">
      <span class="wf-node__type">{{ typeLabel }}</span>
      <strong class="wf-node__title">{{ title }}</strong>
    </div>
    <div v-if="nodeType === 'TOOL_AGENT'" class="wf-node__caps">
      <template v-if="capabilityLabels.length">
        <span v-for="cap in capabilityLabels" :key="cap" class="wf-cap">{{ cap }}</span>
      </template>
      <span v-else class="wf-cap wf-cap--empty">未配置能力</span>
    </div>
    <Handle type="source" :position="Position.Right" class="wf-handle" />
  </div>
</template>

<script setup lang="ts">
import { computed, inject, type ComputedRef } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'

const props = defineProps<NodeProps>()

const injectedCapMap = inject<ComputedRef<Record<string, string>> | undefined>(
  'graphCapabilityLabelMap',
  undefined,
)

const nodeType = computed(() => String(props.data?.nodeType || 'LLM'))
const title = computed(() => String(props.data?.label || props.label || props.id))

const typeLabelMap: Record<string, string> = {
  START: '开始',
  LLM: '对话',
  TOOL_AGENT: '工具执行',
  ROUTER: '路由',
  CONDITIONAL: '条件',
  END: '结束',
}

const typeLabel = computed(() => typeLabelMap[nodeType.value] || nodeType.value)

const capabilityLabels = computed(() => {
  const codes = Array.isArray(props.data?.capabilities) ? (props.data.capabilities as string[]) : []
  const dict = injectedCapMap?.value || {}
  return codes.map((code) => dict[code] || code)
})
</script>

<style scoped lang="scss">
.wf-node {
  min-width: 140px;
  max-width: 220px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  font-size: 12px;
}

.wf-node--selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--el-color-primary) 35%, transparent);
}

.wf-node--start {
  border-color: var(--el-color-success);
}
.wf-node--end {
  border-color: var(--el-color-info);
}
.wf-node--tool_agent {
  border-color: var(--el-color-warning);
  min-width: 168px;
}
.wf-node--router {
  border-color: var(--el-color-primary);
}
.wf-node--conditional {
  border-color: #a855f7;
}

.wf-node__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wf-node__type {
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.wf-node__title {
  font-size: 13px;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.wf-node__caps {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.wf-cap {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--el-color-warning) 14%, transparent);
  color: var(--el-text-color-regular);
  font-size: 11px;
  line-height: 1.5;
}

.wf-cap--empty {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.wf-handle {
  width: 8px;
  height: 8px;
  background: var(--el-color-primary);
  border: 1px solid #fff;
}
</style>
