<template>
  <el-tooltip :content="tooltipText" placement="bottom">
    <el-button class="layout-mode-switcher" circle @click="layoutStore.toggleLayoutMode()">
      <el-icon :size="18">
        <component :is="layoutStore.isHybridLayout ? Menu : Grid" />
      </el-icon>
    </el-button>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Grid, Menu } from '@element-plus/icons-vue'
import { useLayoutStore } from '@/stores/useLayoutStore'

const layoutStore = useLayoutStore()

const tooltipText = computed(() =>
  layoutStore.isHybridLayout ? '切换为侧边栏菜单' : '切换为顶部菜单',
)
</script>

<style scoped lang="scss">
.layout-mode-switcher {
  border: none;
  background: transparent;
  color: #606266;

  &:hover {
    color: var(--app-primary);
    background: rgba(9, 105, 218, 0.1);
  }
}
</style>
