<template>
  <ListPageShell
    :loading="loading"
    hero-title="菜单管理"
    hero-eyebrow="系统管理"
    :hero-eyebrow-icon="Menu"
    :hero-metrics="heroMetrics"
    :hero-enable-rate="pageEnableRate"
  >
    <template #heroDescription>
      共 <strong>{{ totalMenuCount }}</strong> 个菜单项，启用率 <strong>{{ pageEnableRate }}%</strong>
      <span class="page-hero__types">· 覆盖 {{ menuTypeCount }} 种菜单类型</span>
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.SYSTEM_MENU_ADD"
        type="primary"
        size="small"
        :icon="Plus"
        @click="handleAdd()"
      >
        新增菜单
      </el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchTree">刷新</el-button>
    </template>

    <template #strip>
      <div class="menu-filter-strip">
        <div class="type-strip">
          <span class="type-strip__label">类型</span>
          <button
            type="button"
            class="type-chip"
            :class="{ 'is-active': !queryParams.menuType }"
            @click="applyTypeFilter('')"
          >
            全部
          </button>
          <button
            v-for="type in menuTypes"
            :key="type.value"
            type="button"
            class="type-chip"
            :class="{
              'is-active': queryParams.menuType === type.value,
              [`type-chip--${getMenuTypeKey(type.value)}`]: true,
            }"
            @click="applyTypeFilter(type.value)"
          >
            <el-icon class="type-chip__icon">
              <component :is="getMenuTypeIcon(type.value)" />
            </el-icon>
            {{ type.label }}
            <span v-if="typeCountMap[type.value]" class="type-chip__count">{{ typeCountMap[type.value] }}</span>
          </button>
        </div>

        <StatusFilterStrip
          :model-value="queryParams.status"
          :options="statusFilterOptions"
          @update:model-value="applyStatusFilter"
        />
      </div>
    </template>

    <template #filter>
      <ListFilterPanel :active-count="activeFilterCount" :default-expanded="false">
        <el-form :inline="true" :model="queryParams" label-width="80px">
          <el-form-item label="菜单名称">
            <el-input
              v-model="queryParams.title"
              placeholder="请输入菜单名称"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="路径">
            <el-input
              v-model="queryParams.path"
              placeholder="请输入路径"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
        <template v-if="filterChips.length" #chips>
          <el-tag
            v-for="chip in filterChips"
            :key="chip.key"
            closable
            round
            effect="plain"
            @close="removeFilterChip(chip.key)"
          >
            {{ chip.label }}
          </el-tag>
        </template>
      </ListFilterPanel>
    </template>

    <template #toolbar>
      <div class="menu-toolbar">
        <ListToolbar title="菜单树">
          <template #hint>
            <span class="menu-legend">
              <span class="menu-legend__item menu-legend__item--directory">目录</span>
              <span class="menu-legend__item menu-legend__item--menu">菜单</span>
              <span class="menu-legend__item menu-legend__item--page">页面</span>
              <span class="menu-legend__item menu-legend__item--button">按钮</span>
            </span>
            <template v-if="hasActiveFilter"> · 当前显示 {{ displayMenuCount }} 项</template>
          </template>
        </ListToolbar>
        <div class="menu-toolbar__actions">
          <el-button size="small" :icon="FolderOpened" @click="expandAllRows">全部展开</el-button>
          <el-button size="small" :icon="Fold" @click="collapseAllRows">全部折叠</el-button>
        </div>
      </div>
    </template>

    <el-table
      ref="tableRef"
      :data="displayTableData"
      row-key="id"
      class="modern-table menu-tree-table"
      default-expand-all
      :indent="36"
      :tree-props="{ children: 'children' }"
      :row-class-name="tableRowClassName"
      empty-text="暂无菜单数据"
    >
      <el-table-column prop="title" label="菜单名称" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          <div
            class="name-cell"
            :class="[
              `name-cell--${getMenuTypeKey(row.menuType)}`,
              `name-cell--level-${Math.min(getMenuLevel(row.id), 4)}`,
            ]"
          >
            <el-icon class="type-icon" :class="`type-icon--${getMenuTypeKey(row.menuType)}`">
              <component :is="getMenuTypeIcon(row.menuType)" />
            </el-icon>
            <span class="menu-title-text">{{ row.title }}</span>
            <span class="type-badge type-badge--inline" :class="`type-badge--${getMenuTypeKey(row.menuType)}`">
              {{ getMenuTypeText(row.menuType) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.path" class="menu-path">{{ row.path }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="routeName" label="路由/权限标识" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.routeName" class="menu-code">{{ row.routeName }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="icon" label="图标" width="88" align="center">
        <template #default="{ row }">
          <div v-if="row.icon" class="icon-cell" :title="row.icon">
            <el-icon class="icon-cell__glyph"><component :is="row.icon" /></el-icon>
          </div>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="72" align="center">
        <template #default="{ row }">
          <span class="sort-value">{{ row.sort ?? '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="visible" label="可见" width="76" align="center">
        <template #default="{ row }">
          <span
            class="visible-pill"
            :class="row.visible === 1 ? 'visible-pill--yes' : 'visible-pill--no'"
          >
            {{ row.visible === 1 ? '可见' : '隐藏' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="76" align="center">
        <template #default="{ row }">
          <span
            class="status-pill"
            :class="row.status === 1 ? 'status-pill--enabled' : 'status-pill--disabled'"
          >
            {{ row.status === 1 ? '启用' : '禁用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              v-if="row.menuType !== 'BUTTON'"
              v-permission="PERMISSIONS.SYSTEM_MENU_ADD"
              link
              type="primary"
              size="small"
              :icon="Plus"
              @click="handleAdd(row as MenuVO)"
            >
              新增
            </el-button>
            <el-button
              v-permission="PERMISSIONS.SYSTEM_MENU_EDIT"
              link
              type="primary"
              size="small"
              :icon="Edit"
              @click="handleEdit(row as MenuVO)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="PERMISSIONS.SYSTEM_MENU_REMOVE"
              link
              type="danger"
              size="small"
              :icon="Delete"
              @click="handleDelete(row as MenuVO)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <template #extra>
      <MenuFormDialog
        v-model:visible="dialogVisible"
        v-model:form="menuForm"
        :title="dialogTitle"
        :is-create="isCreate"
        :rules="formRules"
        :parent-tree-options="parentTreeOptions"
        :submit-loading="submitLoading"
        @close="handleDialogClose"
        @submit="handleSubmit"
      />
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Menu,
  FolderOpened,
  Fold,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import MenuFormDialog from '@/components/menu/MenuFormDialog.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { menuTypes, useMenuList } from '@/composables/useMenuList'
import type { MenuVO } from '@/types/menu'
import { PERMISSIONS } from '@/constants/permissions'

const {
  loading,
  submitLoading,
  tableRef,
  queryParams,
  displayTableData,
  totalMenuCount,
  menuTypeCount,
  pageEnableRate,
  enabledMenuCount,
  typeCountMap,
  hasActiveFilter,
  activeFilterCount,
  filterChips,
  dialogVisible,
  dialogTitle,
  isCreate,
  menuForm,
  formRules,
  parentTreeOptions,
  displayMenuCount,
  getMenuLevel,
  getMenuTypeText,
  getMenuTypeKey,
  getMenuTypeIcon,
  tableRowClassName,
  expandAllRows,
  collapseAllRows,
  applyTypeFilter,
  applyStatusFilter,
  removeFilterChip,
  handleSearch,
  handleReset,
  fetchTree,
  handleAdd,
  handleEdit,
  handleDelete,
  handleSubmit,
  handleDialogClose,
} = useMenuList()

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1, dot: 'enabled' as const },
  { label: '禁用', value: 0, dot: 'disabled' as const },
]

const heroMetrics = computed(() => [
  { key: 'total', label: '菜单总数', value: totalMenuCount.value, icon: Menu, accent: 'primary' as const },
  { key: 'enabled', label: '已启用', value: enabledMenuCount.value, icon: FolderOpened, accent: 'success' as const },
])

useRouteActivate(() => {
  fetchTree()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.menu-filter-strip {
  :deep(.status-strip) {
    border-top: 1px solid var(--app-border-color);
  }
}

.type-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 16px;
  background: var(--app-surface-muted);

  &__label {
    font-size: 12px;
    color: var(--app-text-secondary);
    flex-shrink: 0;
  }
}

.type-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 28px;
  padding: 0 12px;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-bg);
  font-size: 12px;
  color: var(--app-text-regular);
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &__icon {
    font-size: 13px;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--app-primary) 35%, var(--app-border-color));
    color: var(--app-text-primary);
  }

  &.is-active {
    font-weight: 600;
    color: var(--app-primary);
    border-color: var(--app-primary);
    background: color-mix(in srgb, var(--app-primary) 6%, transparent);
  }

  &--directory.is-active {
    color: #e6a23c;
    border-color: #e6a23c;
    background: rgba(230, 162, 60, 0.1);
  }

  &--menu.is-active {
    color: $success-color;
    border-color: $success-color;
    background: rgba(103, 194, 58, 0.1);
  }

  &--page.is-active {
    color: var(--app-primary);
    border-color: var(--app-primary);
    background: color-mix(in srgb, var(--app-primary) 8%, transparent);
  }

  &--button.is-active {
    color: #606266;
    border-color: #909399;
    background: rgba(144, 147, 153, 0.12);
  }

  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 9px;
    font-size: 11px;
    font-weight: 600;
    color: var(--app-text-secondary);
    background: var(--app-content-bg);
  }
}

.menu-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 0 4px 4px;

  :deep(.list-toolbar) {
    flex: 1;
    min-width: 0;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}

.menu-legend {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: var(--app-text-secondary);

    &::before {
      content: '';
      width: 8px;
      height: 8px;
      border-radius: 2px;
    }

    &--directory::before {
      background: #e6a23c;
    }

    &--menu::before {
      background: $success-color;
    }

    &--page::before {
      background: var(--app-primary);
    }

    &--button::before {
      background: #909399;
    }
  }
}

.menu-tree-table {
  :deep(td.el-table__cell) {
    padding-top: 11px;
    padding-bottom: 11px;
  }

  // 展开箭头与菜单名称同一行、垂直居中
  :deep(.el-table__body td.el-table__cell:first-child > .cell) {
    display: inline-flex;
    align-items: center;
    flex-wrap: nowrap;
    vertical-align: middle;
    line-height: 1;
  }

  :deep(.el-table__indent) {
    flex-shrink: 0;
    height: 0;
  }

  :deep(.el-table__expand-icon) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    margin-right: 8px;
    font-size: 14px;
    color: var(--app-text-secondary);
    border-radius: 4px;
    background: var(--app-surface-muted);
    vertical-align: middle;

    &:hover {
      color: var(--app-primary);
      background: color-mix(in srgb, var(--app-primary) 10%, transparent);
    }

    &.el-table__expand-icon--expanded {
      color: var(--app-primary);
    }
  }

  :deep(.el-table__placeholder) {
    display: inline-flex;
    flex-shrink: 0;
    width: 20px;
    height: 20px;
    margin-right: 8px;
  }

  :deep(.menu-row--directory > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 #e6a23c;
  }

  :deep(.menu-row--menu > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 rgba(103, 194, 58, 0.7);
  }

  :deep(.menu-row--page > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 color-mix(in srgb, var(--app-primary) 70%, transparent);
  }

  :deep(.menu-row--button > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 rgba(144, 147, 153, 0.55);
  }

  :deep(.menu-row--level-0 > td.el-table__cell) {
    background: color-mix(in srgb, var(--app-surface-muted) 72%, transparent);
  }

  :deep(.menu-row--level-2 > td.el-table__cell),
  :deep(.menu-row--level-3 > td.el-table__cell),
  :deep(.menu-row--level-4 > td.el-table__cell) {
    background: color-mix(in srgb, var(--app-content-bg) 55%, transparent);
  }
}

.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 20px;
  vertical-align: middle;

  &--directory .menu-title-text {
    font-size: 14px;
    font-weight: 700;
  }

  &--menu .menu-title-text {
    font-weight: 600;
  }

  &--button .menu-title-text {
    font-size: 13px;
    font-weight: 400;
    color: var(--app-text-secondary);
  }
}

.type-icon {
  flex-shrink: 0;
  font-size: 15px;

  &--directory {
    color: #e6a23c;
  }

  &--menu {
    color: $success-color;
  }

  &--page {
    color: var(--app-primary);
  }

  &--button {
    color: #909399;
  }

  &--default {
    color: var(--app-text-secondary);
  }
}

.menu-title-text {
  font-weight: 500;
  color: var(--app-text-primary);
  line-height: 1.4;
}

.menu-path,
.menu-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--app-text-regular);
}

.menu-code {
  color: var(--app-text-secondary);
}

.icon-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--app-surface-muted);
  color: var(--app-text-regular);

  &__glyph {
    font-size: 15px;
  }
}

.sort-value {
  font-variant-numeric: tabular-nums;
  color: var(--app-text-regular);
}

.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}

.text-muted {
  color: $text-placeholder;
  font-size: 13px;
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--app-radius-sm);
  font-size: 12px;
  font-weight: 500;

  &--inline {
    flex-shrink: 0;
    padding: 0 6px;
    min-height: 18px;
    line-height: 18px;
    font-size: 11px;
  }

  &--directory {
    color: #e6a23c;
    background: rgba(230, 162, 60, 0.12);
  }

  &--menu {
    color: $success-color;
    background: rgba(103, 194, 58, 0.12);
  }

  &--page {
    color: var(--app-primary);
    background: color-mix(in srgb, var(--app-primary) 12%, transparent);
  }

  &--button {
    color: #909399;
    background: rgba(144, 147, 153, 0.12);
  }

  &--default {
    color: var(--app-text-secondary);
    background: var(--app-content-bg);
  }
}

.visible-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--app-radius-sm);
  font-size: 12px;
  font-weight: 500;

  &--yes {
    color: var(--app-primary);
    background: color-mix(in srgb, var(--app-primary) 12%, transparent);
  }

  &--no {
    color: var(--app-text-secondary);
    background: var(--app-content-bg);
  }
}

.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--app-radius-sm);
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
  background: var(--app-surface-muted);

  &--enabled {
    color: $success-color;
    background: rgba(103, 194, 58, 0.12);
  }

  &--disabled {
    color: var(--app-text-secondary);
    background: var(--app-content-bg);
  }
}

:deep(.menu-row--disabled) {
  td {
    color: var(--app-text-secondary);
  }

  .menu-title-text {
    color: var(--app-text-secondary);
  }
}
</style>
