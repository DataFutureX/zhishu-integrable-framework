<template>
  <el-aside
    :width="collapse ? '64px' : '224px'"
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
      ref="menuRef"
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

    <div v-if="$slots.actions" class="sidebar-bottom">
      <slot name="actions" />
      <div v-if="copyright && !collapse" class="sidebar-bottom__copyright">
        <span class="sidebar-bottom__rule" aria-hidden="true" />
        <p class="copyright">{{ copyright }}</p>
      </div>
    </div>

    <div v-else-if="copyright && !collapse" class="sidebar-footer">
      <span class="sidebar-footer__rule" aria-hidden="true" />
      <p class="copyright">{{ copyright }}</p>
    </div>
  </el-aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import SidebarMenuItems from '@/components/SidebarMenuItems.vue'
import LayoutLogo from '@/components/layout/LayoutLogo.vue'
import { useThemeStore } from '@/stores/useThemeStore'
import { ThemeStyle } from '@/types'
import type { MenuVO } from '@/types/menu'

const props = withDefaults(
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

/** Element Plus 的 el-menu 实例，用于强制同步激活状态 */
const menuRef = ref<{ updateActiveIndex?: (val: string) => void } | null>(null)

/**
 * 防御性同步：当路由或菜单数据变化时，强制 el-menu 重新计算激活项。
 * Element Plus 内部的 default-active watcher 在某些时序下（如页面刷新后菜单数据异步到达）
 * 可能未能正确展开子菜单，此处通过 nextTick 后调用 expose 的 updateActiveIndex 兜底。
 */
const syncActiveMenu = () => {
  const path = props.activeMenu
  if (!path) return
  nextTick(() => {
    menuRef.value?.updateActiveIndex?.(path)
  })
}

watch(() => props.activeMenu, syncActiveMenu)
watch(() => props.menus, syncActiveMenu, { deep: true })

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

  .sidebar-bottom {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    border-top: 1px solid color-mix(in srgb, var(--app-sidebar-text, #bfcbd9) 14%, transparent);

    &__copyright {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5px;
      padding: 6px 12px 12px;
    }

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
