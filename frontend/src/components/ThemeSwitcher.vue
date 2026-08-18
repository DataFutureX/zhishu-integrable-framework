<template>
  <el-dropdown trigger="click" @command="handleThemeChange">
    <div class="theme-switcher">
      <el-icon :size="18"><Brush /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="theme in themes"
          :key="theme.value"
          :command="theme.value"
          :class="{ 'is-active': currentTheme === theme.value }"
        >
          <div class="theme-option">
            <div
              class="theme-preview"
              :style="{
                backgroundColor: theme.config.sidebarBgColor,
                borderColor: theme.config.sidebarActiveColor,
              }"
            >
              <div
                class="theme-preview-active"
                :style="{ backgroundColor: theme.config.sidebarActiveColor }"
              ></div>
            </div>
            <span>{{ theme.label }}</span>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { ThemeStyle } from '@/types'
import { themeConfigs, themeLabels, themeOrder } from '@/config/themes'
import { useThemeStore } from '@/stores/useThemeStore'

const themeStore = useThemeStore()

const currentTheme = computed(() => themeStore.currentTheme)

const themes = themeOrder.map((value) => ({
  value,
  label: themeLabels[value],
  config: themeConfigs[value],
}))

const handleThemeChange = (theme: ThemeStyle) => {
  themeStore.setTheme(theme)
}
</script>

<style lang="scss" scoped>
.theme-switcher {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  cursor: pointer;
  color: #606266;
  transition: color 0.3s;

  &:hover {
    color: var(--app-primary);
  }
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-preview {
  width: 24px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
  position: relative;
  overflow: hidden;

  .theme-preview-active {
    position: absolute;
    left: 0;
    top: 0;
    width: 4px;
    height: 100%;
  }
}

.is-active {
  background-color: color-mix(in srgb, var(--app-primary) 8%, transparent);

  .theme-preview {
    border-color: var(--app-primary);
  }
}
</style>
