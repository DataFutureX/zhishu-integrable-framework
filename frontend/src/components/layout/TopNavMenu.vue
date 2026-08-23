<template>
  <nav
    class="top-nav-shell"
    :class="{
      'top-nav-shell--hydro': theme === 'hydro',
      'top-nav-shell--light': theme === 'light',
    }"
  >
    <el-menu
      :key="activeTopMenuPath"
      class="top-nav-menu"
      mode="horizontal"
      :default-active="activeTopMenuPath"
      :ellipsis="false"
      text-color="rgba(255, 255, 255, 0.88)"
      active-text-color="#ffffff"
      @select="handleSelect"
    >
      <el-menu-item
        v-for="menu in topMenus"
        :key="menu.id"
        :index="normalizeMenuPath(menu.path)"
      >
        <el-icon class="top-nav-menu__icon">
          <component :is="resolveMenuIcon(menu.icon)" />
        </el-icon>
        <span class="top-nav-menu__label">{{ menu.title }}</span>
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<script setup lang="ts">
import { useLayoutMenu } from '@/composables/useLayoutMenu'
import { resolveMenuIcon } from '@/utils/menuNavigation'

withDefaults(
  defineProps<{
    theme?: 'default' | 'hydro' | 'light'
  }>(),
  { theme: 'default' },
)

const { topMenus, activeTopMenuPath, navigateToTopMenu, normalizeMenuPath } = useLayoutMenu()

const handleSelect = (index: string) => {
  const menu = topMenus.value.find((item) => normalizeMenuPath(item.path) === index)
  if (menu) {
    navigateToTopMenu(menu)
  }
}
</script>

<style scoped lang="scss">
// 简约：无托盘容器；不单调：字重/透明度/底线微差
.top-nav-shell {
  --nav-item-text: rgba(255, 255, 255, 0.72);
  --nav-item-text-hover: #ffffff;
  --nav-item-text-active: #ffffff;
  --nav-item-bg-hover: rgba(255, 255, 255, 0.08);
  --nav-item-bg-active: transparent;
  --nav-item-indicator: rgba(255, 255, 255, 0.95);
  --nav-icon-muted: 0.72;
  --nav-icon-size: 15px;
  --nav-item-height: 40px;
  --nav-gap: 4px;

  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.top-nav-menu {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  border-bottom: none !important;
  background: transparent;
  padding: 0;
  overflow-x: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }

  &.el-menu--horizontal {
    display: flex;
    flex-wrap: nowrap;
    align-items: stretch;
    width: 100%;
    height: 100%;
    border-bottom: none !important;
    gap: var(--nav-gap);
  }

  :deep(.el-menu-item) {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 100%;
    min-height: var(--nav-item-height);
    line-height: 1;
    margin: 0;
    padding: 0 14px;
    border-bottom: none !important;
    border-radius: 0;
    background: transparent !important;
    box-shadow: none !important;
    font-family: 'Noto Sans SC', 'Outfit', sans-serif;
    font-weight: 450;
    font-size: 13.5px;
    letter-spacing: 0.02em;
    color: var(--nav-item-text);
    transition: color 0.18s ease, background-color 0.18s ease;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: 0;
      width: 18px;
      height: 2px;
      margin-left: -9px;
      border-radius: 1px;
      background: var(--nav-item-indicator);
      opacity: 0;
      transform: scaleX(0.4);
      transition:
        opacity 0.2s ease,
        transform 0.2s ease,
        width 0.2s ease,
        margin-left 0.2s ease;
    }

    .top-nav-menu__icon {
      margin-right: 0 !important;
      width: var(--nav-icon-size);
      height: var(--nav-icon-size);
      font-size: var(--nav-icon-size);
      color: inherit;
      opacity: var(--nav-icon-muted);
      transition: opacity 0.18s ease, color 0.18s ease;
    }

    .top-nav-menu__label {
      color: inherit;
      white-space: nowrap;
    }

    &:hover {
      color: var(--nav-item-text-hover);
      background: var(--nav-item-bg-hover) !important;

      .top-nav-menu__icon {
        opacity: 1;
      }
    }

    &.is-active {
      color: var(--nav-item-text-active) !important;
      font-weight: 600;
      background: var(--nav-item-bg-active) !important;

      .top-nav-menu__icon,
      .top-nav-menu__label {
        color: var(--nav-item-text-active) !important;
        opacity: 1;
      }

      &::after {
        opacity: 1;
        width: 22px;
        margin-left: -11px;
        transform: scaleX(1);
      }
    }

    &:focus-visible {
      outline: none;
      color: var(--nav-item-text-active);

      &::after {
        opacity: 0.45;
        transform: scaleX(0.7);
      }
    }
  }
}

// 浅色：扁平顶栏文字导航 + 主色短线
.top-nav-shell--light {
  --nav-item-text: #656d76;
  --nav-item-text-hover: #1f2328;
  --nav-item-text-active: var(--app-primary);
  --nav-item-bg-hover: transparent;
  --nav-item-bg-active: transparent;
  --nav-item-indicator: var(--app-primary);
  --nav-icon-muted: 0.65;
  --nav-gap: 2px;

  .top-nav-menu :deep(.el-menu-item) {
    padding: 0 16px;

    &:hover {
      background: transparent !important;
    }

    &.is-active {
      background: transparent !important;
    }
  }
}

// 夜空：同样扁平，亮色短线
.top-nav-shell--hydro {
  --nav-item-text: rgba(186, 230, 253, 0.7);
  --nav-item-text-hover: #e0f2fe;
  --nav-item-text-active: #7dd3fc;
  --nav-item-bg-hover: transparent;
  --nav-item-bg-active: transparent;
  --nav-item-indicator: #38bdf8;
  --nav-icon-muted: 0.7;
  --nav-gap: 2px;

  .top-nav-menu :deep(.el-menu-item) {
    font-size: 13px;
    letter-spacing: 0.04em;

    &:hover,
    &.is-active {
      background: transparent !important;
    }
  }
}

@media (max-width: 1100px) {
  .top-nav-menu :deep(.el-menu-item) {
    padding: 0 12px;
    gap: 5px;
    font-size: 13px;
  }
}
</style>
