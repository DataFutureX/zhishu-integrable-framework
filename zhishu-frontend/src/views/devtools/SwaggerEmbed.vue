<template>
  <div class="swagger-embed">
    <div class="swagger-embed__toolbar">
      <div class="swagger-embed__title">
        <el-icon :size="18"><Document /></el-icon>
        <span>后端接口</span>
      </div>
      <div class="swagger-embed__actions">
        <el-button text type="primary" :icon="Refresh" @click="reloadFrame">刷新</el-button>
        <el-button text type="primary" :icon="Link" @click="openInNewTab">新窗口打开</el-button>
      </div>
    </div>
    <iframe
      v-if="frameSrc"
      :key="frameKey"
      class="swagger-embed__frame"
      :src="frameSrc"
      title="Swagger UI"
      referrerpolicy="no-referrer-when-downgrade"
    />
    <el-empty v-else :description="emptyDescription" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Document, Link, Refresh } from '@element-plus/icons-vue'
import { isDemoMode } from '@/config/demo'
import { resolveSwaggerUiUrl } from '@/utils/swagger'

const frameKey = ref(0)
const frameSrc = computed(() => resolveSwaggerUiUrl())
const emptyDescription = computed(() =>
  isDemoMode ? '演示模式未连接后端，接口文档不可用' : '未配置后端接口文档地址',
)

const reloadFrame = () => {
  frameKey.value += 1
}

const openInNewTab = () => {
  if (!frameSrc.value) return
  window.open(frameSrc.value, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped lang="scss">
.swagger-embed {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: var(--app-content-padding, 16px);
  gap: 12px;
  box-sizing: border-box;
  background: var(--app-content-bg, #f6f8fa);
}

.swagger-embed__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--app-border-color, #d0d7de);
  border-radius: var(--app-radius-md, 6px);
  background: var(--app-surface-bg, #fff);
  flex-shrink: 0;
}

.swagger-embed__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary, #1f2328);
}

.swagger-embed__actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.swagger-embed__frame {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 1px solid var(--app-border-color, #d0d7de);
  border-radius: var(--app-radius-md, 6px);
  background: var(--app-surface-bg, #fff);
}

.swagger-embed :deep(.el-empty) {
  flex: 1;
  margin: 0;
  border: 1px solid var(--app-border-color, #d0d7de);
  border-radius: var(--app-radius-md, 6px);
  background: var(--app-surface-bg, #fff);
}
</style>
