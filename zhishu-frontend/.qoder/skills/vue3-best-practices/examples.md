# Vue3 常用代码示例

## 基础组件模板

### 通用列表页面

```vue
<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="action-card">
      <el-button type="primary" @click="handleAdd">新增</el-button>
      <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
        批量删除
      </el-button>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table
        :data="tableData"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getList, createItem, updateItem, deleteItem } from '@/api/example'
import type { ExampleItem } from '@/types/example'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: ''
})

// 表格数据
const loading = ref(false)
const tableData = ref<ExampleItem[]>([])
const selectedIds = ref<number[]>([])

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive({
  id: 0,
  name: '',
  status: 'active' as 'active' | 'inactive'
})

const rules = reactive<FormRules>({
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在2-50个字符', trigger: 'blur' }
  ]
})

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const res = await getList({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword,
      status: searchForm.status
    })
    tableData.value = res.list
    pagination.total = res.total
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

// 搜索
function handleSearch() {
  pagination.page = 1
  fetchData()
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = ''
  handleSearch()
}

// 选择变化
function handleSelectionChange(selection: ExampleItem[]) {
  selectedIds.value = selection.map(item => item.id)
}

// 新增
function handleAdd() {
  dialogTitle.value = '新增'
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑
function handleEdit(row: ExampleItem) {
  dialogTitle.value = '编辑'
  isEdit.value = true
  formData.id = row.id
  formData.name = row.name
  formData.status = row.status
  dialogVisible.value = true
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          await updateItem(formData.id, formData)
          ElMessage.success('更新成功')
        } else {
          await createItem(formData)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        fetchData()
      } catch (error) {
        ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 对话框关闭
function handleDialogClose() {
  formRef.value?.resetFields()
  formData.id = 0
  formData.name = ''
  formData.status = 'active'
}

// 删除
async function handleDelete(row: ExampleItem) {
  await ElMessageBox.confirm('确认删除该记录吗？', '提示', {
    type: 'warning'
  })

  try {
    await deleteItem(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 批量删除
async function handleBatchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条记录吗？`, '提示', {
    type: 'warning'
  })

  try {
    await Promise.all(selectedIds.value.map(id => deleteItem(id)))
    ElMessage.success('批量删除成功')
    fetchData()
  } catch (error) {
    ElMessage.error('批量删除失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.search-card,
.action-card,
.table-card {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
```

## Composables 示例

### useTable - 通用表格Hook

```typescript
// composables/useTable.ts
import { ref, reactive } from 'vue'
import type { Ref } from 'vue'

interface PaginationConfig {
  page: number
  pageSize: number
  total: number
}

interface UseTableOptions<T, P> {
  apiFn: (params: P) => Promise<{ list: T[]; total: number }>
  defaultParams?: Partial<P>
  onSuccess?: (data: T[]) => void
}

export function useTable<T, P extends Record<string, any>>(
  options: UseTableOptions<T, P>
) {
  const { apiFn, defaultParams = {}, onSuccess } = options

  const loading = ref(false)
  const tableData = ref<T[]>([]) as Ref<T[]>

  const pagination = reactive<PaginationConfig>({
    page: 1,
    pageSize: 10,
    total: 0
  })

  const searchParams = reactive<P>(defaultParams as P)

  async function fetchData() {
    loading.value = true
    try {
      const params = {
        ...searchParams,
        page: pagination.page,
        pageSize: pagination.pageSize
      }
      
      const res = await apiFn(params as P)
      tableData.value = res.list
      pagination.total = res.total
      onSuccess?.(res.list)
    } catch (error) {
      console.error('Fetch data failed:', error)
    } finally {
      loading.value = false
    }
  }

  function resetSearch() {
    Object.assign(searchParams, defaultParams)
    pagination.page = 1
    fetchData()
  }

  function handlePageChange(page: number) {
    pagination.page = page
    fetchData()
  }

  function handleSizeChange(size: number) {
    pagination.pageSize = size
    pagination.page = 1
    fetchData()
  }

  return {
    loading,
    tableData,
    pagination,
    searchParams,
    fetchData,
    resetSearch,
    handlePageChange,
    handleSizeChange
  }
}
```

### 使用示例

```typescript
<script setup lang="ts">
import { useTable } from '@/composables/useTable'
import { getUserList } from '@/api/user'
import type { User } from '@/types/user'

const {
  loading,
  tableData,
  pagination,
  searchParams,
  fetchData,
  resetSearch
} = useTable<User, { keyword?: string; status?: string }>({
  apiFn: getUserList,
  defaultParams: {
    keyword: '',
    status: ''
  },
  onSuccess: (data) => {
    console.log('获取到', data.length, '条数据')
  }
})

onMounted(() => {
  fetchData()
})
</script>
```

## 权限指令

```typescript
// directives/permission.ts
import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const userStore = useUserStore()
    
    if (value && !userStore.permissions.includes(value)) {
      el.parentNode?.removeChild(el)
    }
  }
}
```

### 使用

```vue
<template>
  <el-button v-permission="'user:create'">新增用户</el-button>
  <el-button v-permission="'user:delete'">删除用户</el-button>
</template>
```

## 错误边界组件

```vue
<!-- components/ErrorBoundary.vue -->
<template>
  <div v-if="hasError" class="error-boundary">
    <el-result icon="error" title="页面出错了" :sub-title="errorMessage">
      <template #extra>
        <el-button type="primary" @click="handleRetry">重试</el-button>
      </template>
    </el-result>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const hasError = ref(false)
const errorMessage = ref('')

onErrorCaptured((error) => {
  hasError.value = true
  errorMessage.value = error.message
  return false
})

function handleRetry() {
  hasError.value = false
  errorMessage.value = ''
}
</script>

<style scoped>
.error-boundary {
  padding: 40px;
  text-align: center;
}
</style>
```

## 路由守卫

```typescript
// router/guards.ts
import type { Router } from 'vue-router'
import { useUserStore } from '@/stores/useUserStore'
import { ElMessage } from 'element-plus'

export function setupRouterGuards(router: Router) {
  const whiteList = ['/login', '/register']

  router.beforeEach(async (to, from, next) => {
    const userStore = useUserStore()
    
    if (userStore.isLoggedIn) {
      if (to.path === '/login') {
        next('/')
      } else {
        // 检查权限
        if (to.meta.roles && !to.meta.roles.includes(userStore.user?.role)) {
          ElMessage.error('无权访问')
          next('/403')
        } else {
          next()
        }
      }
    } else {
      if (whiteList.includes(to.path)) {
        next()
      } else {
        next(`/login?redirect=${to.path}`)
      }
    }
  })
}
```
