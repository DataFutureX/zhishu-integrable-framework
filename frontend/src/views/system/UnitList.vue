<template>
  <div class="unit-list-page list-page">
    <PageHero
      title="单位管理"
      eyebrow="系统管理"
      :eyebrow-icon="OfficeBuilding"
      :metrics="heroMetrics"
      :enable-rate="pageEnableRate"
    >
      <template #description>
        共 <strong>{{ allUnitCount }}</strong> 个单位，{{ viewMode === 'tree' ? '当前树' : '本页' }}启用率
        <strong>{{ pageEnableRate }}%</strong>
        <span v-if="unitTypeOptions.length" class="page-hero__types">
          · 覆盖 {{ unitTypeOptions.length }} 种单位类型
        </span>
      </template>
      <template #actions>
        <el-button
          v-permission="PERMISSIONS.SYSTEM_UNIT_ADD"
          type="primary"
          size="small"
          :icon="Plus"
          @click="handleAdd()"
        >
          新增单位
        </el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="refreshCurrentView">刷新</el-button>
      </template>
    </PageHero>

    <!-- 主内容区 -->
    <section class="content-panel">
      <!-- 类型快筛 -->
      <div v-if="unitTypeOptions.length" class="type-strip">
        <span class="type-strip__label">类型快筛</span>
        <button
          type="button"
          class="type-chip"
          :class="{ 'is-active': !queryParams.unitType }"
          @click="applyTypeFilter('')"
        >
          全部
        </button>
        <button
          v-for="type in unitTypeOptions"
          :key="type"
          type="button"
          class="type-chip"
          :class="{ 'is-active': queryParams.unitType === type }"
          @click="applyTypeFilter(type)"
        >
          {{ type }}
          <span v-if="typeCountMap[type]" class="type-chip__count">{{ typeCountMap[type] }}</span>
        </button>
      </div>

      <!-- 状态快筛 -->
      <div class="status-strip">
        <span class="status-strip__label">状态快筛</span>
        <div class="status-strip__chips">
          <button
            type="button"
            class="status-chip"
            :class="{ 'is-active': queryParams.status == null }"
            @click="applyStatusFilter(undefined)"
          >
            全部
          </button>
          <button
            type="button"
            class="status-chip"
            :class="{ 'is-active': queryParams.status === 1 }"
            @click="applyStatusFilter(1)"
          >
            <span class="status-chip__dot status-chip__dot--enabled" />
            启用
          </button>
          <button
            type="button"
            class="status-chip"
            :class="{ 'is-active': queryParams.status === 0 }"
            @click="applyStatusFilter(0)"
          >
            <span class="status-chip__dot status-chip__dot--disabled" />
            停用
          </button>
        </div>
      </div>

      <!-- 筛选区 -->
      <div class="filter-section">
        <div class="filter-section__head" @click="filterExpanded = !filterExpanded">
          <div class="filter-section__title">
            <el-icon><Filter /></el-icon>
            <span>筛选条件</span>
            <el-tag v-if="activeFilterCount" size="small" round effect="plain" type="primary">
              {{ activeFilterCount }}
            </el-tag>
          </div>
          <el-icon class="filter-section__chevron" :class="{ 'is-expanded': filterExpanded }">
            <ArrowDown />
          </el-icon>
        </div>

        <el-collapse-transition>
          <div v-show="filterExpanded" class="filter-section__body">
            <el-form :inline="true" :model="queryParams" label-width="96px">
              <el-form-item label="单位编码">
                <el-input
                  v-model="queryParams.unitCode"
                  placeholder="请输入单位编码"
                  clearable
                  @keyup.enter="handleSearch"
                />
              </el-form-item>
              <el-form-item label="单位名称">
                <el-input
                  v-model="queryParams.unitName"
                  placeholder="请输入单位名称"
                  clearable
                  @keyup.enter="handleSearch"
                />
              </el-form-item>
              <el-form-item label="单位类型">
                <el-input
                  v-model="queryParams.unitType"
                  placeholder="请输入单位类型"
                  clearable
                  @keyup.enter="handleSearch"
                />
              </el-form-item>
              <el-form-item label="上级单位">
                <el-tree-select
                  v-model="queryParams.parentId"
                  :data="parentTreeOptions"
                  node-key="id"
                  :props="{ label: 'unitName', children: 'children' }"
                  check-strictly
                  clearable
                  :render-after-expand="false"
                  placeholder="全部"
                  style="width: 200px"
                />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
                <el-button :icon="Refresh" @click="handleReset">重置</el-button>
              </el-form-item>
            </el-form>

            <div v-if="filterChips.length" class="filter-chips">
              <span class="filter-chips__label">已应用</span>
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
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <!-- 列表工具栏 -->
      <div class="list-toolbar">
        <div class="list-toolbar__left">
          <div class="view-toggle">
            <button
              type="button"
              class="view-toggle__btn"
              :class="{ 'is-active': viewMode === 'tree' }"
              @click="switchView('tree')"
            >
              <el-icon><FolderOpened /></el-icon>
              树形
            </button>
            <button
              type="button"
              class="view-toggle__btn"
              :class="{ 'is-active': viewMode === 'table' }"
              @click="switchView('table')"
            >
              <el-icon><List /></el-icon>
              列表
            </button>
          </div>
          <span class="list-toolbar__hint">管理组织架构与单位归属关系</span>
        </div>
        <div v-if="viewMode === 'tree'" class="list-toolbar__actions">
          <el-button size="small" :icon="FolderOpened" @click="expandAllRows">全部展开</el-button>
          <el-button size="small" :icon="Fold" @click="collapseAllRows">全部折叠</el-button>
        </div>
      </div>

      <!-- 树形表格 -->
      <div v-if="viewMode === 'tree'" v-loading="loading" class="table-view">
        <el-table
          ref="tableRef"
          :data="treeData"
          row-key="id"
          class="modern-table unit-tree-table"
          default-expand-all
          :indent="36"
          :tree-props="{ children: 'children' }"
          :row-class-name="tableRowClassName"
          empty-text="暂无单位数据"
        >
          <el-table-column prop="unitName" label="单位名称" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <div
                class="name-cell"
                :class="[
                  `name-cell--${getUnitNodeKind(row as UnitVO)}`,
                  `name-cell--level-${Math.min(getUnitLevel(row.id), 4)}`,
                ]"
              >
                <el-icon class="type-icon" :class="`type-icon--${getUnitNodeKind(row as UnitVO)}`">
                  <component :is="getUnitNodeIcon(row as UnitVO)" />
                </el-icon>
                <span class="unit-name-text">{{ row.unitName }}</span>
                <span
                  v-if="row.unitType"
                  class="type-badge type-badge--inline"
                >
                  {{ row.unitType }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="unitCode" label="单位编码" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="unit-code">{{ row.unitCode || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unitType" label="单位类型" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.unitType" class="type-badge">{{ row.unitType }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="region" label="所属区域" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.region || '—' }}</template>
          </el-table-column>
          <el-table-column prop="contactPerson" label="联系人" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.contactPerson || '—' }}</template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="72" align="center">
            <template #default="{ row }">
              <span class="sort-value">{{ row.sort ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="76" align="center">
            <template #default="{ row }">
              <span
                class="status-pill"
                :class="row.status === 1 ? 'status-pill--enabled' : 'status-pill--disabled'"
              >
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right" align="center">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  v-permission="PERMISSIONS.SYSTEM_UNIT_ADD"
                  link
                  type="primary"
                  size="small"
                  :icon="Plus"
                  @click="handleAdd(row as UnitVO)"
                >
                  新增下级
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.SYSTEM_UNIT_EDIT"
                  link
                  type="primary"
                  size="small"
                  :icon="Edit"
                  @click="handleEdit(row as UnitVO)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.SYSTEM_UNIT_REMOVE"
                  link
                  type="danger"
                  size="small"
                  :icon="Delete"
                  @click="handleDelete(row as UnitVO)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页列表 -->
      <template v-else>
        <div v-loading="loading" class="table-view">
          <el-table
            :data="tableData"
            class="modern-table"
            :row-class-name="tableRowClassName"
            empty-text="暂无单位数据"
          >
            <el-table-column
              type="index"
              label="序号"
              width="60"
              align="center"
              fixed="left"
              :index="tableIndexMethod"
            />
            <el-table-column prop="unitName" label="单位名称" min-width="140" show-overflow-tooltip fixed="left">
              <template #default="{ row }">
                <div class="name-cell">
                  <span
                    class="status-dot"
                    :class="row.status === 1 ? 'status-dot--enabled' : 'status-dot--disabled'"
                  />
                  <span class="unit-name-text">{{ row.unitName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="unitCode" label="单位编码" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="unit-code">{{ row.unitCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="parentName" label="上级单位" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.parentName || '—' }}</template>
            </el-table-column>
            <el-table-column prop="unitType" label="单位类型" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.unitType" class="type-badge">{{ row.unitType }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="region" label="所属区域" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.region || '—' }}</template>
            </el-table-column>
            <el-table-column prop="contactPerson" label="联系人" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contactPerson || '—' }}</template>
            </el-table-column>
            <el-table-column prop="contactPhone" label="联系电话" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contactPhone || '—' }}</template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="72" align="center">
              <template #default="{ row }">{{ row.sort ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="72" align="center">
              <template #default="{ row }">
                <span
                  class="status-pill"
                  :class="row.status === 1 ? 'status-pill--enabled' : 'status-pill--disabled'"
                >
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.createTime ? formatDateTime(row.createTime) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="PERMISSIONS.SYSTEM_UNIT_EDIT"
                  link
                  type="primary"
                  size="small"
                  :icon="Edit"
                  @click="handleEdit(row as UnitVO)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.SYSTEM_UNIT_REMOVE"
                  link
                  type="danger"
                  size="small"
                  :icon="Delete"
                  @click="handleDelete(row as UnitVO)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="fetchPageList"
            @current-change="fetchPageList"
          />
        </div>
      </template>
    </section>

    <UnitFormDialog
      v-model:visible="dialogVisible"
      v-model:form="unitForm"
      :title="dialogTitle"
      :is-create="isCreate"
      :rules="formRules"
      :form-parent-tree-options="formParentTreeOptions"
      :submit-loading="submitLoading"
      @close="handleDialogClose"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Filter,
  ArrowDown,
  OfficeBuilding,
  FolderOpened,
  Fold,
  List,
  Collection,
  Location,
} from '@element-plus/icons-vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { useUnitList } from '@/composables/useUnitList'
import type { UnitVO } from '@/types/unit'
import { formatDateTime } from '@/utils/format'
import PageHero, { type PageHeroMetric } from '@/components/list-page/PageHero.vue'
import UnitFormDialog from '@/components/unit/UnitFormDialog.vue'
import { PERMISSIONS } from '@/constants/permissions'

const {
  loading,
  submitLoading,
  filterExpanded,
  viewMode,
  tableRef,
  tableData,
  treeData,
  total,
  queryParams,
  allUnitCount,
  pageEnableRate,
  pageEnabledCount,
  unitTypeOptions,
  typeCountMap,
  filterChips,
  activeFilterCount,
  parentTreeOptions,
  formParentTreeOptions,
  dialogVisible,
  dialogTitle,
  isCreate,
  unitForm,
  formRules,
  getUnitLevel,
  getUnitNodeKind,
  expandAllRows,
  collapseAllRows,
  fetchPageList,
  refreshCurrentView,
  switchView,
  tableIndexMethod,
  tableRowClassName,
  handleSearch,
  handleReset,
  applyStatusFilter,
  applyTypeFilter,
  removeFilterChip,
  handleAdd,
  handleEdit,
  handleDelete,
  handleSubmit,
  handleDialogClose,
  initUnitList,
} = useUnitList()

const getUnitNodeIcon = (row: UnitVO) => {
  const kind = getUnitNodeKind(row)
  if (kind === 'root') return OfficeBuilding
  if (kind === 'branch') return FolderOpened
  return Location
}

const heroMetrics = computed<PageHeroMetric[]>(() => [
  { key: 'total', label: '单位总数', value: allUnitCount.value, icon: OfficeBuilding, accent: 'primary' },
  { key: 'enabled', label: '当前启用', value: pageEnabledCount.value, icon: Collection, accent: 'success' },
])

useRouteActivate(async () => {
  await initUnitList()
})
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.unit-list-page {
  min-height: 100%;
}

.content-panel {
  border-radius: $border-radius-md;
  background: $bg-white;
  border: 1px solid $border-lighter;
  overflow: hidden;
}

.type-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid $border-lighter;
  background: #fafbfc;

  &__label {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }
}

.type-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 12px;
  border-radius: $border-radius-sm;
  border: 1px solid $border-lighter;
  background: #fff;
  font-size: 12px;
  color: $text-regular;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: $border-color;
    color: $text-primary;
  }

  &.is-active {
    font-weight: 600;
    color: $primary-color;
    border-color: $primary-color;
    background: rgba(9, 105, 218, 0.06);
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
    color: $text-secondary;
    background: #f6f8fa;
  }
}

.status-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid $border-lighter;
  background: #fafbfc;

  &__label {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }

  &__chips {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 12px;
  border-radius: $border-radius-sm;
  border: 1px solid $border-lighter;
  background: #fff;
  font-size: 12px;
  color: $text-regular;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: $border-color;
    color: $text-primary;
  }

  &.is-active {
    font-weight: 600;
    color: $primary-color;
    border-color: $primary-color;
    background: rgba(9, 105, 218, 0.06);
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;

    &--enabled {
      background: $success-color;
    }

    &--disabled {
      background: $text-placeholder;
    }
  }
}

.filter-section {
  border-bottom: 1px solid $border-lighter;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    cursor: pointer;
    user-select: none;
    transition: background 0.15s ease;

    &:hover {
      background: #fafbfc;
    }
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  &__chevron {
    transition: transform 0.25s ease;
    color: $text-secondary;

    &.is-expanded {
      transform: rotate(180deg);
    }
  }

  &__body {
    padding: 0 16px 12px;

    .el-form--inline .el-form-item {
      margin-bottom: 12px;
    }
  }
}

.filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 4px;

  &__label {
    font-size: 12px;
    color: $text-secondary;
  }
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 16px;
  border-bottom: 1px solid $border-lighter;
  background: #fafbfc;

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    min-width: 0;
  }

  &__hint {
    font-size: 12px;
    color: $text-secondary;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}

.view-toggle {
  display: inline-flex;
  padding: 2px;
  border-radius: $border-radius-sm;
  background: $bg-color;
  border: 1px solid $border-lighter;

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border: none;
    border-radius: $border-radius-sm;
    font-size: 13px;
    color: $text-secondary;
    background: transparent;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      color: $text-primary;
    }

    &.is-active {
      color: $primary-color;
      background: #fff;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
    }
  }
}

.table-view {
  padding: 0 4px 8px;
}

.modern-table {
  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(th.el-table__cell) {
    background: #f6f8fa !important;
    color: $text-regular;
    font-weight: 600;
    border-bottom: 1px solid $border-lighter;
  }

  :deep(td.el-table__cell) {
    border-bottom: 1px solid $border-lighter;
  }

  :deep(.el-table__row:hover > td.el-table__cell) {
    background: color-mix(in srgb, var(--app-primary) 6%, transparent) !important;
  }
}

.unit-tree-table {
  :deep(td.el-table__cell) {
    padding-top: 11px;
    padding-bottom: 11px;
  }

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

  :deep(.unit-row--root > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 var(--app-primary);
  }

  :deep(.unit-row--branch > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 rgba(230, 162, 60, 0.75);
  }

  :deep(.unit-row--leaf > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 color-mix(in srgb, var(--app-primary) 35%, transparent);
  }

  :deep(.unit-row--level-0 > td.el-table__cell) {
    background: color-mix(in srgb, var(--app-surface-muted) 72%, transparent);
  }

  :deep(.unit-row--level-2 > td.el-table__cell),
  :deep(.unit-row--level-3 > td.el-table__cell),
  :deep(.unit-row--level-4 > td.el-table__cell) {
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

  &--root .unit-name-text {
    font-size: 14px;
    font-weight: 700;
  }

  &--branch .unit-name-text {
    font-weight: 600;
  }

  &--leaf .unit-name-text {
    font-size: 13px;
    font-weight: 500;
  }
}

.type-icon {
  flex-shrink: 0;
  font-size: 15px;

  &--root {
    color: var(--app-primary);
  }

  &--branch {
    color: #e6a23c;
  }

  &--leaf {
    color: var(--app-text-secondary);
  }
}

.unit-name-text {
  font-weight: 500;
  color: var(--app-text-primary);
  line-height: 1.4;
}

.unit-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--app-text-regular);
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

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--app-radius-sm);
  font-size: 12px;
  font-weight: 500;
  color: var(--app-primary);
  background: color-mix(in srgb, var(--app-primary) 12%, transparent);

  &--inline {
    flex-shrink: 0;
    padding: 0 6px;
    min-height: 18px;
    line-height: 18px;
    font-size: 11px;
  }
}

.text-muted {
  color: $text-placeholder;
  font-size: 13px;
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

.content-panel .pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid $border-lighter;
  background: #fafbfc;
}

:deep(.unit-row--disabled) {
  td {
    color: var(--app-text-secondary);
  }

  .unit-name-text {
    color: var(--app-text-secondary);
  }
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--enabled {
    background: $success-color;
  }

  &--disabled {
    background: $text-placeholder;
  }
}
</style>
