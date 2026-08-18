<template>
  <ListPageShell
    :loading="loading"
    hero-title="用户管理"
    hero-eyebrow="系统管理"
    :hero-eyebrow-icon="UserFilled"
    :hero-metrics="heroMetrics"
    :hero-enable-rate="pageEnableRate"
  >
    <template #heroDescription>
      共 <strong>{{ total }}</strong> 个用户，本页启用率 <strong>{{ pageEnableRate }}%</strong>
      <span v-if="roleOptions.length" class="page-hero__types">
        · 覆盖 {{ roleOptions.length }} 种角色
      </span>
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.SYSTEM_USER_ADD"
        type="primary"
        size="small"
        :icon="Plus"
        @click="handleAdd"
      >
        新增用户
      </el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchUserList">刷新</el-button>
    </template>

    <template #strip>
      <StatusFilterStrip
        :model-value="queryParams.status"
        :options="statusFilterOptions"
        @update:model-value="applyStatusFilter"
      />
      <div v-if="roleOptions.length" class="role-strip">
        <span class="role-strip__label">角色快筛</span>
        <button
          type="button"
          class="role-chip"
          :class="{ 'is-active': queryParams.roleId == null }"
          @click="applyRoleFilter(undefined)"
        >
          全部
        </button>
        <button
          v-for="role in roleOptions"
          :key="role.id"
          type="button"
          class="role-chip"
          :class="{ 'is-active': String(queryParams.roleId) === String(role.id) }"
          @click="applyRoleFilter(role.id)"
        >
          {{ role.roleName }}
          <span v-if="roleCountMap[String(role.id)]" class="role-chip__count">
            {{ roleCountMap[String(role.id)] }}
          </span>
        </button>
      </div>
    </template>

    <template #filter>
      <ListFilterPanel :active-count="activeFilterCount">
        <el-form :inline="true" :model="queryParams" label-width="96px">
          <el-form-item label="用户名">
            <el-input
              v-model="queryParams.username"
              placeholder="请输入用户名"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input
              v-model="queryParams.realName"
              placeholder="请输入真实姓名"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input
              v-model="queryParams.phone"
              placeholder="请输入手机号"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="角色">
            <el-select v-model="queryParams.roleId" placeholder="全部角色" clearable style="width: 160px">
              <el-option
                v-for="role in roleOptions"
                :key="role.id"
                :label="role.roleName"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
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
      <ListToolbar title="用户列表">
        <template #hint>
          管理系统账号与角色
          <template v-if="selectedRows.length"> · 已选 {{ selectedRows.length }} 项</template>
        </template>
      </ListToolbar>
    </template>

    <el-table
      ref="tableRef"
      :data="tableData"
      class="modern-table"
      :row-class-name="tableRowClassName"
      empty-text="暂无用户数据"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" align="center" fixed="left" />
      <el-table-column
        type="index"
        label="序号"
        width="60"
        align="center"
        fixed="left"
        :index="tableIndexMethod"
      />
      <el-table-column prop="username" label="用户名" min-width="130" show-overflow-tooltip fixed="left">
        <template #default="{ row }">
          <div class="name-cell">
            <span
              class="status-dot"
              :class="row.status === 1 ? 'status-dot--enabled' : 'status-dot--disabled'"
            />
            <span class="username-text">{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="realName" label="真实姓名" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.realName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.phone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色" width="120" align="center">
        <template #default="{ row }">
          <span class="role-badge" :class="`role-badge--${getRoleType(row.role || row.roleName)}`">
            {{ row.roleName || getRoleText(row.role) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            v-permission="PERMISSIONS.SYSTEM_USER_EDIT"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(newStatus: number | string | boolean) => handleStatusChange(row as UserVO, newStatus)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-permission="PERMISSIONS.SYSTEM_USER_EDIT"
            link
            type="primary"
            size="small"
            :icon="Edit"
            @click="handleEdit(row as UserVO)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="PERMISSIONS.SYSTEM_USER_EDIT"
            link
            type="warning"
            size="small"
            :icon="Lock"
            @click="handleResetPassword(row as UserVO)"
          >
            重置密码
          </el-button>
          <el-button
            v-permission="PERMISSIONS.SYSTEM_USER_REMOVE"
            link
            type="danger"
            size="small"
            :icon="Delete"
            @click="handleDelete(row as UserVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #pagination>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchUserList"
        @current-change="fetchUserList"
      />
    </template>

    <template #extra>
      <Transition name="batch-bar">
        <div v-if="selectedRows.length" class="batch-float-bar">
          <div class="batch-float-bar__info">
            <el-icon><User /></el-icon>
            已选择 <strong>{{ selectedRows.length }}</strong> 个用户
          </div>
          <div class="batch-float-bar__actions">
            <el-button type="danger" :icon="Delete" @click="handleBatchDelete">批量删除</el-button>
            <el-button :icon="Close" @click="clearSelection">取消选择</el-button>
          </div>
        </div>
      </Transition>

      <UserFormDialog
        v-model:visible="dialogVisible"
        v-model:form="userForm"
        :title="dialogTitle"
        :is-create="isCreate"
        :rules="userRules"
        :role-options="roleOptions"
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
  Lock,
  UserFilled,
  User,
  Close,
  Collection,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import UserFormDialog from '@/components/user/UserFormDialog.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { useUserList } from '@/composables/useUserList'
import type { UserVO } from '@/api/user'
import { PERMISSIONS } from '@/constants/permissions'

const {
  loading,
  submitLoading,
  tableRef,
  roleOptions,
  queryParams,
  tableData,
  total,
  selectedRows,
  dialogVisible,
  dialogTitle,
  isCreate,
  userForm,
  userRules,
  pageEnableRate,
  roleCountMap,
  filterChips,
  activeFilterCount,
  fetchRoleOptions,
  fetchUserList,
  tableIndexMethod,
  handleSearch,
  handleReset,
  applyStatusFilter,
  applyRoleFilter,
  removeFilterChip,
  handleSelectionChange,
  clearSelection,
  handleAdd,
  handleEdit,
  handleDialogClose,
  handleDelete,
  handleBatchDelete,
  handleStatusChange,
  handleResetPassword,
  handleSubmit,
  getRoleText,
  getRoleType,
  tableRowClassName,
} = useUserList()

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1, dot: 'enabled' as const },
  { label: '禁用', value: 0, dot: 'disabled' as const },
]

const heroMetrics = computed(() => {
  const roleCount = new Set(
    tableData.value.map((row) => row.roleId ?? row.roleName).filter(Boolean),
  ).size
  return [
    { key: 'total', label: '用户总数', value: total.value, icon: UserFilled, accent: 'primary' as const },
    { key: 'roles', label: '本页角色', value: roleCount, icon: Collection },
  ]
})

useRouteActivate(async () => {
  await fetchRoleOptions()
  void fetchUserList()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.role-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);

  &__label {
    font-size: 12px;
    color: var(--app-text-secondary);
    flex-shrink: 0;
  }
}

.role-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
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

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
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

.username-text {
  font-weight: 500;
  color: var(--app-text-primary);
}

.role-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--app-radius-sm);
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
  background: var(--app-surface-muted);

  &--danger {
    color: #f56c6c;
    background: rgba(245, 108, 108, 0.12);
  }

  &--warning {
    color: #e6a23c;
    background: rgba(230, 162, 60, 0.12);
  }

  &--success {
    color: $success-color;
    background: rgba(103, 194, 58, 0.12);
  }

  &--info {
    color: var(--app-text-secondary);
    background: var(--app-content-bg);
  }
}

:deep(.user-row--disabled) {
  td {
    color: var(--app-text-secondary);
  }
}

.batch-float-bar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-md);
  color: var(--app-text-primary);

  &__info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    white-space: nowrap;
    color: var(--app-text-regular);

    .el-icon {
      color: var(--app-primary);
    }

    strong {
      color: var(--app-primary);
      font-weight: 600;
    }
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}

.batch-bar-enter-active,
.batch-bar-leave-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.batch-bar-enter-from,
.batch-bar-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(20px);
}
</style>
