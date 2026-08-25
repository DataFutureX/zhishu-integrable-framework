<template>
  <el-tooltip :content="tooltipText" placement="bottom">
    <el-button
      class="tab-bar-switcher"
      :class="{
        'tab-bar-switcher--active': layoutStore.showTabBar,
        'tab-bar-switcher--on-primary': onPrimary,
      }"
      circle
      @click="layoutStore.toggleTabBar()"
    >
      <el-icon :size="18"><Tickets /></el-icon>
    </el-button>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Tickets } from '@element-plus/icons-vue'
import { useLayoutStore } from '@/stores/useLayoutStore'

defineProps<{
  onPrimary?: boolean
}>()

const layoutStore = useLayoutStore()

const tooltipText = computed(() =>
  layoutStore.showTabBar ? '隐藏标签栏' : '显示标签栏',
)
</script>

<style scoped lang="scss">
.tab-bar-switcher {
  border: none;
  background: transparent;
  color: #909399;
  transition: color 0.2s ease, background 0.2s ease;

  &:hover {
    color: var(--app-primary);
    background: rgba(9, 105, 218, 0.1);
  }

  &--active {
    color: var(--app-primary);
    background: rgba(9, 105, 218, 0.12);
  }

  &--on-primary {
    color: rgba(255, 255, 255, 0.75);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.15);
    }

    &.tab-bar-switcher--active {
      color: #fff;
      background: rgba(255, 255, 255, 0.22);
    }
  }
}
</style>
