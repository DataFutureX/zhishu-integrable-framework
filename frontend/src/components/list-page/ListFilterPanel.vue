<template>
  <div class="filter-section">
    <div class="filter-section__head" @click="expanded = !expanded">
      <div class="filter-section__title">
        <el-icon><Filter /></el-icon>
        <span>{{ title }}</span>
        <el-tag v-if="activeCount" size="small" round effect="plain" type="primary">
          {{ activeCount }}
        </el-tag>
      </div>
      <el-icon class="filter-section__chevron" :class="{ 'is-expanded': expanded }">
        <ArrowDown />
      </el-icon>
    </div>

    <el-collapse-transition>
      <div v-show="expanded" class="filter-section__body">
        <slot />
        <div v-if="$slots.chips" class="filter-chips">
          <span class="filter-chips__label">已应用</span>
          <slot name="chips" />
        </div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown, Filter } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title?: string
    activeCount?: number
    defaultExpanded?: boolean
  }>(),
  {
    title: '筛选条件',
    activeCount: 0,
    defaultExpanded: false,
  },
)

const expanded = ref(props.defaultExpanded)
</script>
