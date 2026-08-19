<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    hero-title="Tools"
    hero-eyebrow="数智中枢"
    :hero-eyebrow-icon="Operation"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      智能体可挂载的能力与 Tool 方法目录。描述来自后端 <code>@Tool</code>；在 Agents 中勾选能力后，运行时会注入对应工具。
    </template>
    <template #heroActions>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </template>

    <div class="tools-home">
      <div class="tools-toolbar">
        <el-input
          v-model="keyword"
          class="tools-toolbar__search"
          clearable
          placeholder="搜索能力名称、编码或方法名"
          :prefix-icon="Search"
        />
        <el-radio-group v-model="kindFilter" size="default">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="TOOL">Tools</el-radio-button>
          <el-radio-button value="OTHER">非 Tool</el-radio-button>
        </el-radio-group>
      </div>

      <el-empty
        v-if="!filteredCapabilities.length"
        class="tools-empty"
        :description="keyword || kindFilter !== 'ALL' ? '没有匹配的能力' : '暂无能力目录'"
        :image-size="88"
      />

      <div v-else class="tools-grid">
        <el-card
          v-for="cap in filteredCapabilities"
          :key="cap.code"
          shadow="never"
          class="tools-card"
        >
          <template #header>
            <div class="tools-card__header">
              <div class="tools-card__title-wrap">
                <span class="tools-card__title">{{ cap.label }}</span>
                <code class="tools-card__code">{{ cap.code }}</code>
              </div>
              <el-tag v-if="cap.toolBased" size="small" type="primary" effect="plain">Tools</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">非 Tool</el-tag>
            </div>
          </template>
          <p class="tools-card__desc">{{ cap.description }}</p>
          <ul v-if="resolveCapTools(cap).length" class="tools-card__list">
            <li v-for="tool in resolveCapTools(cap)" :key="tool.name">
              <code>{{ tool.name }}</code>
              <span>{{ tool.description || '暂无描述' }}</span>
            </li>
          </ul>
          <p v-else class="tools-card__empty">不绑定 Tool（如知识库增强走检索链路）</p>
        </el-card>
      </div>
    </div>
  </ListPageShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIToolsCatalog' })

import { computed, ref } from 'vue'
import { Cpu, Document, Operation, Refresh, Search } from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { listAgentCapabilities } from '@/api/ai'
import type { CapabilityVO, ToolInfoVO } from '@/types/aiAgent'

const loading = ref(false)
const capabilities = ref<CapabilityVO[]>([])
const keyword = ref('')
const kindFilter = ref<'ALL' | 'TOOL' | 'OTHER'>('ALL')

const resolveCapTools = (cap: CapabilityVO): ToolInfoVO[] => {
  if (cap.tools?.length) return cap.tools
  return (cap.toolNames || []).map((name) => ({ name, description: '' }))
}

const toolBasedCapCount = computed(
  () => capabilities.value.filter((c) => c.toolBased).length,
)

const toolMethodCount = computed(() => {
  const names = new Set<string>()
  capabilities.value.forEach((cap) => {
    resolveCapTools(cap).forEach((t) => names.add(t.name))
  })
  return names.size
})

const heroMetrics = computed(() => [
  {
    key: 'caps',
    label: '能力项',
    value: capabilities.value.length,
    icon: Cpu,
    accent: 'primary' as const,
  },
  {
    key: 'toolCaps',
    label: 'Tools 能力',
    value: toolBasedCapCount.value,
    icon: Operation,
    accent: 'primary' as const,
  },
  {
    key: 'methods',
    label: 'Tool 方法',
    value: toolMethodCount.value,
    icon: Document,
    accent: 'primary' as const,
  },
])

const filteredCapabilities = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return capabilities.value.filter((cap) => {
    if (kindFilter.value === 'TOOL' && !cap.toolBased) return false
    if (kindFilter.value === 'OTHER' && cap.toolBased) return false
    if (!q) return true
    const tools = resolveCapTools(cap)
    const hay = [cap.label, cap.code, cap.description, ...tools.map((t) => `${t.name} ${t.description}`)]
      .join(' ')
      .toLowerCase()
    return hay.includes(q)
  })
})

const fetchList = async () => {
  loading.value = true
  try {
    const caps = await listAgentCapabilities()
    capabilities.value = caps || []
  } catch (e) {
    console.error(e)
    capabilities.value = []
  } finally {
    loading.value = false
  }
}

useRouteActivate(fetchList)
</script>

<style scoped lang="scss">
.tools-home {
  --tools-ink: #0f2740;
  --tools-muted: #5b738a;
  --tools-line: rgba(26, 43, 60, 0.1);
  --tools-shelf: #f4f7fa;
  padding: 14px 12px 16px;
  min-height: 360px;
}

.tools-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--tools-line);
  border-radius: var(--app-radius-md);
  background: var(--tools-shelf);

  &__search {
    flex: 1;
    min-width: 200px;
    max-width: 360px;
  }
}

.tools-empty {
  padding: 48px 0;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}

.tools-card {
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;

  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  }

  :deep(.el-card__body) {
    padding: 14px 16px;
    background: var(--el-bg-color);
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  &__title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
  }

  &__title {
    position: relative;
    padding-left: 10px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
    }
  }

  &__code {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    padding: 1px 6px;
    border-radius: 4px;
  }

  &__desc {
    margin: 0 0 10px;
    font-size: 13px;
    color: var(--el-text-color-regular);
    line-height: 1.5;
  }

  &__list {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 8px;

    li {
      display: flex;
      flex-direction: column;
      gap: 2px;
      font-size: 12px;
      line-height: 1.45;
      color: var(--el-text-color-secondary);

      code {
        width: fit-content;
        font-size: 12px;
        color: var(--el-color-primary);
        background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
        padding: 1px 6px;
        border-radius: 4px;
      }
    }
  }

  &__empty {
    margin: 0;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }
}
</style>
