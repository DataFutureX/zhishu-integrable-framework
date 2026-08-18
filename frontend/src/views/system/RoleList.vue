<template>
  <ListPageShell
    :loading="loading"
    hero-title="角色管理"
    hero-eyebrow="系统管理"
    :hero-eyebrow-icon="Key"
    :hero-metrics="heroMetrics"
    :hero-enable-rate="pageEnableRate"
  >
    <template #heroDescription>
      共 <strong>{{ total }}</strong> 个角色，本页启用率 <strong>{{ pageEnableRate }}%</strong>
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.SYSTEM_ROLE_ADD"
        type="primary"
        size="small"
        :icon="Plus"
        @click="handleAdd"
      >
        新增角色
      </el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
    </template>

    <template #strip>
      <StatusFilterStrip
        :model-value="queryParams.status"
        :options="statusFilterOptions"
        @update:model-value="applyStatusFilter"
      />
    </template>

    <template #filter>
      <ListFilterPanel :active-count="activeFilterCount" :default-expanded="true">
        <el-form :inline="true" :model="queryParams" label-width="96px">
          <el-form-item label="角色编码">
            <el-input
              v-model="queryParams.roleCode"
              placeholder="请输入角色编码"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="角色名称">
            <el-input
              v-model="queryParams.roleName"
              placeholder="请输入角色名称"
              clearable
              @keyup.enter="handleSearch"
            />
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
      <ListToolbar title="角色列表">
        <template #hint>
          配置系统角色与菜单/按钮权限
        </template>
      </ListToolbar>
    </template>

    <el-table
      :data="tableData"
      class="modern-table"
      :row-class-name="tableRowClassName"
      empty-text="暂无角色数据"
    >
      <el-table-column
        type="index"
        label="序号"
        width="60"
        align="center"
        fixed="left"
        :index="tableIndexMethod"
      />
      <el-table-column prop="roleName" label="角色名称" min-width="130" show-overflow-tooltip fixed="left">
        <template #default="{ row }">
          <div class="name-cell">
            <span
              class="status-dot"
              :class="row.status === 1 ? 'status-dot--enabled' : 'status-dot--disabled'"
            />
            <span class="role-name-text">{{ row.roleName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="角色编码" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="role-code">{{ row.roleCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '—' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" align="center">
        <template #default="{ row }">{{ row.sort ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="88" align="center">
        <template #default="{ row }">
          <span
            class="status-pill"
            :class="row.status === 1 ? 'status-pill--enabled' : 'status-pill--disabled'"
          >
            {{ row.status === 1 ? '启用' : '禁用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-permission="PERMISSIONS.SYSTEM_ROLE_EDIT"
            link
            type="primary"
            size="small"
            :icon="Edit"
            @click="handleEdit(row as RoleVO)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="PERMISSIONS.SYSTEM_ROLE_ASSIGN_MENU"
            link
            type="warning"
            size="small"
            :icon="Menu"
            @click="handleAssignMenus(row as RoleVO)"
          >
            分配权限
          </el-button>
          <el-button
            v-permission="PERMISSIONS.SYSTEM_ROLE_REMOVE"
            link
            type="danger"
            size="small"
            :icon="Delete"
            @click="handleDelete(row as RoleVO)"
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
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </template>

    <template #extra>
      <!-- 新增/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="520px"
        :close-on-click-modal="false"
        @close="handleDialogClose"
      >
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
          <el-form-item label="角色编码" prop="roleCode">
            <el-input
              v-model="formData.roleCode"
              placeholder="请输入角色编码"
              :disabled="!isCreate"
              maxlength="50"
            />
          </el-form-item>
          <el-form-item label="角色名称" prop="roleName">
            <el-input v-model="formData.roleName" placeholder="请输入角色名称" maxlength="50" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="formData.description"
              type="textarea"
              :rows="3"
              placeholder="请输入角色描述"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="formData.sort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="formData.status">
              <el-radio :label="1">启用</el-radio>
              <el-radio :label="0">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
        </template>
      </el-dialog>

      <!-- 分配权限对话框 -->
      <el-dialog
        v-model="menuDialogVisible"
        :title="`分配权限 - ${assigningRole?.roleName || ''}`"
        width="520px"
        :close-on-click-modal="false"
      >
        <el-tree
          ref="menuTreeRef"
          v-loading="menuTreeLoading"
          :data="menuTreeData"
          show-checkbox
          node-key="id"
          :props="{ label: 'title', children: 'children' }"
          :default-checked-keys="checkedMenuIds"
          default-expand-all
        />
        <template #footer>
          <el-button @click="menuDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="menuSubmitLoading" @click="handleMenuSubmit">
            确定
          </el-button>
        </template>
      </el-dialog>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Key,
  Menu,
  Collection,
} from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import StatusFilterStrip from '@/components/list-page/StatusFilterStrip.vue'
import ListFilterPanel from '@/components/list-page/ListFilterPanel.vue'
import ListToolbar from '@/components/list-page/ListToolbar.vue'
import {
  getRolePageApi,
  createRoleApi,
  updateRoleApi,
  deleteRoleApi,
  getRoleMenuIdsApi,
  assignRoleMenusApi,
} from '@/api/role'
import { getMenuTreeApi } from '@/api/menu'
import type { RoleVO, RoleQueryDTO, RoleCreateDTO, RoleUpdateDTO } from '@/types/role'
import type { MenuVO } from '@/types/menu'
import { PERMISSIONS } from '@/constants/permissions'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<RoleVO[]>([])
const total = ref(0)

const queryParams = reactive<Required<Pick<RoleQueryDTO, 'pageNum' | 'pageSize'>> & RoleQueryDTO>({
  roleCode: '',
  roleName: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20,
})

const statusFilterOptions = [
  { label: '全部', value: undefined },
  { label: '启用', value: 1, dot: 'enabled' as const },
  { label: '禁用', value: 0, dot: 'disabled' as const },
]

const pageEnableRate = computed(() => {
  if (!tableData.value.length) return 0
  const enabled = tableData.value.filter((row) => row.status === 1).length
  return Math.round((enabled / tableData.value.length) * 100)
})

const pageEnabledCount = computed(() => tableData.value.filter((row) => row.status === 1).length)

const heroMetrics = computed(() => [
  { key: 'total', label: '角色总数', value: total.value, icon: Key, accent: 'primary' as const },
  { key: 'enabled', label: '本页启用', value: pageEnabledCount.value, icon: Collection, accent: 'success' as const },
])

const activeFilterCount = computed(() => filterChips.value.length)

const filterChips = computed(() => {
  const chips: { key: string; label: string }[] = []
  if (queryParams.roleCode?.trim()) {
    chips.push({ key: 'roleCode', label: `编码：${queryParams.roleCode.trim()}` })
  }
  if (queryParams.roleName?.trim()) {
    chips.push({ key: 'roleName', label: `名称：${queryParams.roleName.trim()}` })
  }
  if (queryParams.status === 0 || queryParams.status === 1) {
    chips.push({ key: 'status', label: queryParams.status === 1 ? '启用' : '禁用' })
  }
  return chips
})

function tableRowClassName({ row }: { row: RoleVO }) {
  return row.status === 1 ? '' : 'role-row--disabled'
}

function applyStatusFilter(status: number | undefined) {
  queryParams.status = status
  queryParams.pageNum = 1
  fetchList()
}

function removeFilterChip(key: string) {
  if (key === 'roleCode') queryParams.roleCode = ''
  else if (key === 'roleName') queryParams.roleName = ''
  else if (key === 'status') queryParams.status = undefined
  queryParams.pageNum = 1
  fetchList()
}

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const formRef = ref<FormInstance>()
const isCreate = ref(true)
const editingId = ref<number | string>()

interface FormData {
  roleCode: string
  roleName: string
  description: string
  sort: number
  status: number
}

const defaultForm = (): FormData => ({
  roleCode: '',
  roleName: '',
  description: '',
  sort: 0,
  status: 1,
})

const formData = reactive<FormData>(defaultForm())

const formRules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const menuDialogVisible = ref(false)
const menuTreeLoading = ref(false)
const menuSubmitLoading = ref(false)
const menuTreeData = ref<MenuVO[]>([])
const checkedMenuIds = ref<(number | string)[]>([])
const assigningRole = ref<RoleVO | null>(null)
const menuTreeRef = ref<InstanceType<typeof ElTree>>()

const buildQueryParams = (): RoleQueryDTO => {
  const params: RoleQueryDTO = {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
  }
  if (queryParams.roleCode?.trim()) params.roleCode = queryParams.roleCode.trim()
  if (queryParams.roleName?.trim()) params.roleName = queryParams.roleName.trim()
  if (queryParams.status !== undefined && queryParams.status !== null) {
    params.status = queryParams.status
  }
  return params
}

const fetchList = async () => {
  loading.value = true
  try {
    const pageData = await getRolePageApi(buildQueryParams())
    tableData.value = pageData.records
    total.value = pageData.total
  } catch (error) {
    console.error('获取角色列表失败:', error)
  } finally {
    loading.value = false
  }
}

function tableIndexMethod(index: number) {
  const pageNum = queryParams.pageNum ?? 1
  const pageSize = queryParams.pageSize ?? 20
  return (pageNum - 1) * pageSize + index + 1
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchList()
}

const handleReset = () => {
  queryParams.roleCode = ''
  queryParams.roleName = ''
  queryParams.status = undefined
  handleSearch()
}

const resetForm = () => {
  Object.assign(formData, defaultForm())
  formRef.value?.clearValidate()
}

const handleAdd = () => {
  isCreate.value = true
  dialogTitle.value = '新增角色'
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: RoleVO) => {
  isCreate.value = false
  dialogTitle.value = '编辑角色'
  editingId.value = row.id
  Object.assign(formData, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1,
  })
  dialogVisible.value = true
}

const handleDelete = async (row: RoleVO) => {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteRoleApi(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除角色失败:', error)
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isCreate.value) {
        const payload: RoleCreateDTO = {
          roleCode: formData.roleCode.trim(),
          roleName: formData.roleName.trim(),
          description: formData.description.trim() || undefined,
          sort: formData.sort,
          status: formData.status,
        }
        await createRoleApi(payload)
        ElMessage.success('新增成功')
      } else {
        const payload: RoleUpdateDTO = {
          id: editingId.value!,
          roleName: formData.roleName.trim(),
          description: formData.description.trim() || undefined,
          sort: formData.sort,
          status: formData.status,
        }
        await updateRoleApi(payload)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      fetchList()
    } catch (error) {
      console.error('保存角色失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleAssignMenus = async (row: RoleVO) => {
  assigningRole.value = row
  menuDialogVisible.value = true
  menuTreeLoading.value = true
  try {
    const [tree, menuIds] = await Promise.all([
      getMenuTreeApi(),
      getRoleMenuIdsApi(row.id),
    ])
    menuTreeData.value = tree
    checkedMenuIds.value = menuIds || []
  } catch (error) {
    console.error('加载菜单树失败:', error)
  } finally {
    menuTreeLoading.value = false
  }
}

const handleMenuSubmit = async () => {
  if (!assigningRole.value || !menuTreeRef.value) return

  menuSubmitLoading.value = true
  try {
    const checkedKeys = menuTreeRef.value.getCheckedKeys(false) as (number | string)[]
    const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys() as (number | string)[]
    const menuIds = [...checkedKeys, ...halfCheckedKeys]

    await assignRoleMenusApi(assigningRole.value.id, { menuIds })
    ElMessage.success('菜单分配成功')
    menuDialogVisible.value = false
  } catch (error) {
    console.error('分配菜单失败:', error)
  } finally {
    menuSubmitLoading.value = false
  }
}

const handleDialogClose = () => {
  resetForm()
}

useRouteActivate(() => {
  fetchList()
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

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
    background: var(--app-text-secondary);
  }
}

.role-name-text {
  font-weight: 500;
  color: var(--app-text-primary);
}

.role-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--app-text-regular);
}

.status-pill {
  display: inline-block;
  padding: 2px 10px;
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

:deep(.role-row--disabled) {
  td {
    color: var(--app-text-secondary);
  }
}
</style>
