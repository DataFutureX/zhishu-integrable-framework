<template>
  <div class="ai-placeholder">
    <el-empty :description="description">
      <template #image>
        <el-icon :size="64" color="var(--el-color-primary)"><MagicStick /></el-icon>
      </template>
      <p class="ai-placeholder__hint">{{ hint }}</p>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'

const route = useRoute()

const description = computed(
  () => (route.meta?.title as string | undefined) || 'AI 模块',
)

const hint = 'AI 能力已迁入知枢；简报管理请使用本页或万象工作台简报收件箱。'
</script>

<style scoped lang="scss">
.ai-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 24px;
}

.ai-placeholder__hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
  max-width: 420px;
  line-height: 1.6;
}
</style>
