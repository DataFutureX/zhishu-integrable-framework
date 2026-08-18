<template>
  <el-aside
    :width="collapse ? '64px' : '200px'"
    class="layout-aside"
    :class="{ 'layout-aside--secondary': variant === 'secondary' }"
    :style="{ backgroundColor: themeStore.themeConfig.sidebarBgColor }"
  >
    <LayoutLogo
      v-if="variant === 'primary'"
      :collapse="collapse"
      :variant="logoVariant"
    />

    <el-menu
      :key="menuKey"
      :default-active="activeMenu"
      :collapse="collapse"
      :unique-opened="true"
      router
      :background-color="themeStore.themeConfig.sidebarBgColor"
      :text-color="themeStore.themeConfig.sidebarTextColor"
      :active-text-color="themeStore.themeConfig.sidebarActiveColor"
    >
      <SidebarMenuItems :menus="menus" />
    </el-menu>

    <div v-if="copyright && !collapse" class="sidebar-footer">
      <span class="sidebar-footer__rule" aria-hidden="true" />
      <p class="copyright">{{ copyright }}</p>
    </div>
  </el-aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import SidebarMenuItems from '@/components/SidebarMenuItems.vue'
import LayoutLogo from '@/components/layout/LayoutLogo.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { ThemeStyle } from '@/types'
import type { MenuVO } from '@/types/menu'

withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary'
    collapse?: boolean
    menus: MenuVO[]
    activeMenu: string
    menuKey?: string
    copyright?: string
  }>(),
  {
    variant: 'secondary',
    collapse: false,
    menuKey: '',
    copyright: '',
  },
)

const themeStore = useThemeStore()

const logoVariant = computed(() => {
  if (themeStore.currentTheme === ThemeStyle.BLUE) return 'hydro'
  if (themeStore.currentTheme === ThemeStyle.LIGHT) return 'light'
  return 'default'
})
</script>

<style lang="scss" scoped>
.layout-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  overflow-x: hidden;
  box-shadow: none;
  border-right: 1px solid var(--app-border-color, #d0d7de);

  &--secondary {
    box-shadow: none;

    :deep(.el-menu) {
      padding-top: 8px;
    }
  }

  :deep(.el-menu) {
    flex: 1;
    overflow-y: auto;
    border-right: none;

    .el-menu-item,
    .el-sub-menu__title {
      transition:
        background-color 0.15s,
        color 0.15s;

      &:hover {
        background-color: color-mix(in srgb, var(--app-primary) 8%, transparent) !important;
      }
    }

    .el-menu-item.is-active {
      background: color-mix(in srgb, var(--app-primary) 12%, transparent);
      position: relative;
      font-weight: 600;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 2px;
        height: 60%;
        background: var(--app-primary);
        border-radius: 0;
        box-shadow: none;
      }
    }
  }

  .sidebar-footer {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 5px;
    padding: 11px 12px 12px;
    background: linear-gradient(
      180deg,
      transparent 0%,
      color-mix(in srgb, var(--app-sidebar-bg, #1a2f38) 92%, #000) 38%
    );
    pointer-events: none;

    &__rule {
      display: block;
      width: 18px;
      height: 1px;
      border-radius: 1px;
      background: color-mix(in srgb, var(--app-primary) 55%, transparent);
      opacity: 0.8;
    }

    .copyright {
      margin: 0;
      max-width: 100%;
      padding: 0 2px;
      font-family: 'Outfit', 'Noto Sans SC', sans-serif;
      font-size: 11px;
      font-weight: 500;
      line-height: 1.35;
      letter-spacing: 0.02em;
      text-align: center;
      color: color-mix(in srgb, var(--app-sidebar-text, #bfcbd9) 78%, transparent);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
