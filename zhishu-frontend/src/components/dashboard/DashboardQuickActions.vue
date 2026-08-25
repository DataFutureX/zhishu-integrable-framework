<template>
  <section class="dash-quick">
    <header class="dash-quick__head">
      <div class="dash-quick__title-group">
        <h2 class="dash-quick__title">快捷入口</h2>
        <span class="dash-quick__hint">按权限展示常用功能</span>
      </div>
    </header>

    <div v-if="visibleActions.length" class="dash-quick__grid">
      <button
        v-for="action in visibleActions"
        :key="action.key"
        type="button"
        class="quick-tile"
        @click="handleNavigate(action.path)"
      >
        <div
          class="quick-tile__icon"
          :class="`quick-tile__icon--${action.accent}`"
        >
          <el-icon :size="18">
            <component :is="action.icon" />
          </el-icon>
        </div>
        <div class="quick-tile__main">
          <span class="quick-tile__label">{{ action.label }}</span>
          <span class="quick-tile__desc">{{ action.desc }}</span>
        </div>
      </button>
    </div>

    <el-empty v-else description="暂无可用快捷入口" :image-size="56" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  DASHBOARD_QUICK_ACTIONS,
  type DashboardQuickActionDef,
} from '@/config/dashboardQuickActions'
import { useMenuStore } from '@/stores/useMenuStore'
import { normalizeMenuPath } from '@/utils/menuNavigation'
import type { MenuVO } from '@/types/menu'

const router = useRouter()
const menuStore = useMenuStore()

const collectMenuPaths = (menus: MenuVO[]): Set<string> => {
  const paths = new Set<string>()

  const walk = (items: MenuVO[]) => {
    for (const item of items) {
      if (item.path && item.menuType !== 'DIRECTORY') {
        paths.add(normalizeMenuPath(item.path))
      }
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }

  walk(menus)
  return paths
}

const accessiblePaths = computed(() => collectMenuPaths(menuStore.sidebarMenus))

const visibleActions = computed<DashboardQuickActionDef[]>(() => {
  if (!menuStore.sidebarMenus.length) {
    return DASHBOARD_QUICK_ACTIONS
  }

  return DASHBOARD_QUICK_ACTIONS.filter((action) =>
    accessiblePaths.value.has(normalizeMenuPath(action.path)),
  )
})

const handleNavigate = (path: string) => {
  router.push(path)
}
</script>

<style scoped lang="scss">
.dash-quick {
  height: 100%;
  padding: 14px 16px 16px;
  border: 1px solid var(--app-border-color);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    min-height: 24px;
  }

  &__title-group {
    display: flex;
    align-items: baseline;
    gap: 10px;
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
    color: var(--app-text-primary);
  }

  &__hint {
    font-size: 12px;
    color: var(--app-text-secondary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }
}

.quick-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: color-mix(in srgb, var(--app-surface-muted) 88%, transparent);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--app-primary) 28%, var(--app-border-color));
    background: color-mix(in srgb, var(--app-primary) 5%, var(--app-surface-bg));
    transform: translateY(-1px);
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 9px;
    flex-shrink: 0;

    &--primary {
      background: color-mix(in srgb, var(--app-primary) 12%, transparent);
      color: var(--app-primary);
    }

    &--success {
      background: rgba(103, 194, 58, 0.12);
      color: #67c23a;
    }

    &--warning {
      background: rgba(230, 162, 60, 0.12);
      color: #e6a23c;
    }

    &--info {
      background: rgba(144, 147, 153, 0.12);
      color: var(--app-text-regular);
    }

    &--danger {
      background: rgba(245, 108, 108, 0.12);
      color: #f56c6c;
    }
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }

  &__label {
    font-size: 13px;
    font-weight: 650;
    color: var(--app-text-primary);
    line-height: 1.3;
  }

  &__desc {
    font-size: 11px;
    color: var(--app-text-secondary);
    line-height: 1.35;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

@media (max-width: 1280px) {
  .dash-quick__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .dash-quick__grid {
    grid-template-columns: 1fr;
  }
}
</style>
