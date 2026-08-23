<template>
  <el-popover
    :placement="placement"
    :width="236"
    trigger="click"
    popper-class="layout-settings-popover"
  >
    <template #reference>
      <el-button
        class="layout-settings-trigger"
        :class="{ 'layout-settings-trigger--on-primary': onPrimary }"
        circle
      >
        <el-icon :size="18"><Brush /></el-icon>
      </el-button>
    </template>

    <div class="layout-settings-panel">
      <header class="layout-settings-panel__head">
        <h3 class="layout-settings-panel__title">布局设置</h3>
        <p class="layout-settings-panel__desc">自定义导航、主题与内容区显示偏好</p>
      </header>

      <section class="setting-section">
        <h4 class="setting-section__title">布局</h4>

        <div class="setting-item">
          <label class="setting-item__label">页面导航</label>
          <el-radio-group v-model="navContextDisplay" class="setting-radio-group setting-radio-group--triple">
            <el-radio-button value="tabs">标签栏</el-radio-button>
            <el-radio-button value="breadcrumb">面包屑</el-radio-button>
            <el-radio-button value="none">不显示</el-radio-button>
          </el-radio-group>
          <p class="setting-hint">标签栏与面包屑互斥；顶部菜单模式下标签栏在独立导航行展示</p>
        </div>

        <div class="setting-item">
          <label class="setting-item__label">菜单模式</label>
          <el-radio-group v-model="layoutMode" class="setting-radio-group setting-radio-group--triple">
            <el-radio-button value="hybrid">顶部菜单</el-radio-button>
            <el-radio-button value="sidebar">侧边栏</el-radio-button>
            <el-radio-button value="immersive">无顶栏</el-radio-button>
          </el-radio-group>
          <p class="setting-hint">无顶栏模式下，门户、通知、设置与账户入口固定在侧栏底部</p>
        </div>
      </section>

      <section class="setting-section">
        <h4 class="setting-section__title">外观</h4>

        <div class="setting-item">
          <label class="setting-item__label">主题风格</label>
          <div class="theme-grid">
            <button
              v-for="theme in themes"
              :key="theme.value"
              type="button"
              class="theme-card"
              :class="{ 'theme-card--active': currentTheme === theme.value }"
              @click="handleThemeChange(theme.value)"
            >
              <div class="theme-card__preview">
                <div
                  class="theme-card__header"
                  :style="{ background: theme.config.headerBg }"
                />
                <div
                  class="theme-card__sidebar"
                  :style="{ backgroundColor: theme.config.sidebarBgColor }"
                />
                <div
                  class="theme-card__accent"
                  :style="{ backgroundColor: theme.config.primaryColor }"
                />
              </div>
              <span class="theme-card__label">{{ theme.label }}</span>
            </button>
          </div>
        </div>
      </section>

      <section class="setting-section">
        <h4 class="setting-section__title">内容区</h4>

        <div class="setting-inset">
          <div class="setting-item setting-item--compact">
            <label class="setting-item__label">显示密度</label>
            <el-radio-group v-model="density" class="setting-radio-group">
              <el-radio-button value="comfortable">舒适</el-radio-button>
              <el-radio-button value="compact">紧凑</el-radio-button>
            </el-radio-group>
          </div>

          <div class="setting-item setting-item--compact">
            <label class="setting-item__label">配色方案</label>
            <el-radio-group v-model="contentScheme" class="setting-radio-group">
              <el-radio-button value="light">浅色</el-radio-button>
              <el-radio-button value="dark">深色</el-radio-button>
            </el-radio-group>
          </div>

          <p class="setting-hint setting-hint--inset">
            紧凑模式缩小页内边距与列表行高；暗色配色适合长时间运维，与侧栏主题独立
          </p>
        </div>
      </section>

      <footer class="layout-settings-panel__version">
        <span>系统版本</span>
        <span>{{ APP_VERSION }}</span>
      </footer>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Brush } from '@element-plus/icons-vue'
import { ThemeStyle } from '@/types'
import { themeConfigs, themeLabels, themeOrder } from '@/config/themes'
import {
  useLayoutStore,
  type LayoutMode,
  type NavContextDisplay,
  type LayoutDensity,
  type ContentScheme,
} from '@/stores/useLayoutStore'
import { useThemeStore } from '@/stores/useThemeStore'
import { APP_VERSION } from '@/constants/app'

withDefaults(
  defineProps<{
    onPrimary?: boolean
    /** 弹出层位置，顶栏场景用 bottom-end，侧栏底部场景用 top-start/right-start */
    placement?: 'bottom-end' | 'top-start' | 'right-start'
  }>(),
  {
    onPrimary: false,
    placement: 'bottom-end',
  },
)

const layoutStore = useLayoutStore()
const themeStore = useThemeStore()

const navContextDisplay = computed({
  get: () => layoutStore.navContextDisplay,
  set: (value: NavContextDisplay) => layoutStore.setNavContextDisplay(value),
})

const layoutMode = computed({
  get: () => layoutStore.layoutMode,
  set: (value: LayoutMode) => layoutStore.setLayoutMode(value),
})

const density = computed({
  get: () => layoutStore.density,
  set: (value: LayoutDensity) => layoutStore.setDensity(value),
})

const contentScheme = computed({
  get: () => layoutStore.contentScheme,
  set: (value: ContentScheme) => layoutStore.setContentScheme(value),
})

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

<style scoped lang="scss">
.layout-settings-trigger {
  border: none;
  background: transparent;
  color: #606266;
  transition: color 0.2s ease, background 0.2s ease;

  &:hover {
    color: var(--app-primary, #0969da);
    background: color-mix(in srgb, var(--app-primary, #0969da) 10%, transparent);
  }

  &--on-primary {
    color: rgba(255, 255, 255, 0.9);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.15);
    }
  }
}

.layout-settings-panel {
  max-height: min(72vh, 560px);
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 4px;
  }

  &__head {
    margin-bottom: 6px;
  }

  &__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.4;
    color: #303133;
  }

  &__desc {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: #909399;
  }

  &__version {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 4px;
    padding-top: 12px;
    border-top: 1px solid #d0d7de;
    font-size: 12px;
    color: #909399;

    span:last-child {
      color: #606266;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
    }
  }
}

.setting-section {
  padding: 12px 0;
  border-bottom: 1px solid #f6f8fa;

  &:last-of-type {
    border-bottom: none;
    padding-bottom: 0;
  }

  &__title {
    margin: 0 0 12px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: #909399;
    text-transform: uppercase;
  }
}

.setting-item {
  & + & {
    margin-top: 14px;
  }

  &--compact + &--compact {
    margin-top: 10px;
  }

  &__label {
    display: block;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    line-height: 1.4;
    color: #606266;
  }
}

.setting-inset {
  padding: 12px;
  border-radius: 8px;
  background: #f6f8fa;
  border: 1px solid #eef1f6;
}

.setting-radio-group {
  display: flex;
  width: 100%;

  :deep(.el-radio-button) {
    flex: 1;
    min-width: 0;
  }

  :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 8px 6px;
    font-size: 12px;
  }

  &--triple :deep(.el-radio-button__inner) {
    padding: 8px 4px;
    font-size: 11px;
    letter-spacing: -0.02em;
  }
}

.setting-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: #a8abb2;

  &--inset {
    margin: 10px 0 0;
    padding-top: 10px;
    border-top: 1px dashed #e4e7ed;
  }
}

.theme-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.theme-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid #d0d7de;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
  text-align: left;
  width: 100%;

  &:hover {
    border-color: color-mix(in srgb, var(--app-primary, #0969da) 28%, #d0d7de);
    background: color-mix(in srgb, var(--app-primary, #0969da) 4%, transparent);
  }

  &--active {
    border-color: var(--app-primary, #0969da);
    background: color-mix(in srgb, var(--app-primary, #0969da) 8%, transparent);
    box-shadow: 0 0 0 1px color-mix(in srgb, var(--app-primary, #0969da) 20%, transparent);
  }

  &__preview {
    position: relative;
    width: 44px;
    height: 28px;
    flex-shrink: 0;
    border: 1px solid #dcdfe6;
    border-radius: 5px;
    overflow: hidden;
    background: #f6f8fa;
  }

  &__header {
    height: 8px;
  }

  &__sidebar {
    position: absolute;
    left: 0;
    bottom: 0;
    width: 12px;
    height: 18px;
  }

  &__accent {
    position: absolute;
    right: 5px;
    bottom: 5px;
    width: 7px;
    height: 7px;
    border-radius: 50%;
  }

  &__label {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    font-weight: 500;
    color: #606266;
    text-align: left;
  }

  &--active .theme-card__label {
    color: var(--app-primary, #0969da);
    font-weight: 600;
  }
}
</style>

<style lang="scss">
.layout-settings-popover.el-popover {
  padding: 14px 12px 12px !important;
  border-radius: 12px !important;
  box-shadow: 0 10px 32px rgba(15, 23, 42, 0.14) !important;
}
</style>
