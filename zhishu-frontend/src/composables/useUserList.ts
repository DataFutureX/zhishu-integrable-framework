import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormRules, type TableInstance } from 'element-plus'
import type { UserCreateDTO, UserQueryDTO, UserUpdateDTO, UserVO } from '@/api/user'
import {
  createUserApi,
  deleteUserApi,
  getUserPageApi,
  resetUserPasswordApi,
  updateUserApi,
  updateUserStatusApi,
} from '@/api/user'
import { getRoleListApi } from '@/api/role'
import type { RoleVO } from '@/types/role'

export interface UserFormModel {
  id?: string | number
  username: string
  realName: string
  email: string
  phone: string
  password: string
  roleId?: string | number
  status: number
}

export function useUserList() {
  const loading = ref(false)
  const submitLoading = ref(false)
  const tableRef = ref<TableInstance>()
  const roleOptions = ref<RoleVO[]>([])
  const tableData = ref<UserVO[]>([])
  const total = ref(0)
  const selectedRows = ref<UserVO[]>([])

  const queryParams = reactive<UserQueryDTO>({
    pageNum: 1,
    pageSize: 20,
    username: '',
    realName: '',
    phone: '',
    roleId: undefined,
    status: undefined,
  })

  const dialogVisible = ref(false)
  const dialogTitle = ref('新增用户')
  const isCreate = ref(true)

  const userForm = reactive<UserFormModel>({
    username: '',
    realName: '',
    email: '',
    phone: '',
    password: '',
    roleId: undefined,
    status: 1,
  })

  const userRules: FormRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
    ],
    realName: [
      { required: true, message: '请输入真实姓名', trigger: 'blur' },
      { max: 50, message: '真实姓名不能超过 50 个字符', trigger: 'blur' },
    ],
    email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
    phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }],
    password: [
      {
        required: true,
        message: '请输入密码',
        trigger: 'blur',
        validator: (_rule, value, callback) => {
          if (isCreate.value && !value) {
            callback(new Error('请输入密码'))
          } else if (value && value.length < 6) {
            callback(new Error('密码长度不能少于 6 位'))
          } else {
            callback()
          }
        },
      },
    ],
    roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
  }

  const pageEnableRate = computed(() => {
    if (!tableData.value.length) return 0
    const enabled = tableData.value.filter((row) => row.status === 1).length
    return Math.round((enabled / tableData.value.length) * 100)
  })

  const roleCountMap = computed(() => {
    const map: Record<string, number> = {}
    tableData.value.forEach((row) => {
      if (row.roleId != null) {
        const key = String(row.roleId)
        map[key] = (map[key] || 0) + 1
      }
    })
    return map
  })

  const filterChips = computed(() => {
    const chips: { key: string; label: string }[] = []
    if (queryParams.username?.trim()) {
      chips.push({ key: 'username', label: `用户名：${queryParams.username.trim()}` })
    }
    if (queryParams.realName?.trim()) {
      chips.push({ key: 'realName', label: `姓名：${queryParams.realName.trim()}` })
    }
    if (queryParams.phone?.trim()) {
      chips.push({ key: 'phone', label: `手机：${queryParams.phone.trim()}` })
    }
    if (queryParams.roleId != null && queryParams.roleId !== '') {
      const role = roleOptions.value.find((item) => String(item.id) === String(queryParams.roleId))
      chips.push({ key: 'roleId', label: `角色：${role?.roleName || queryParams.roleId}` })
    }
    if (queryParams.status === 0 || queryParams.status === 1) {
      chips.push({ key: 'status', label: queryParams.status === 1 ? '启用' : '禁用' })
    }
    return chips
  })

  const activeFilterCount = computed(() => filterChips.value.length)

  const resetUserForm = () => {
    userForm.id = undefined
    userForm.username = ''
    userForm.realName = ''
    userForm.email = ''
    userForm.phone = ''
    userForm.password = ''
    userForm.roleId = undefined
    userForm.status = 1
  }

  const fetchRoleOptions = async () => {
    try {
      roleOptions.value = await getRoleListApi()
    } catch (error) {
      console.error('获取角色列表失败:', error)
    }
  }

  const fetchUserList = async () => {
    loading.value = true
    try {
      const params: UserQueryDTO = {
        pageNum: queryParams.pageNum,
        pageSize: queryParams.pageSize,
      }
      if (queryParams.username?.trim()) params.username = queryParams.username.trim()
      if (queryParams.realName?.trim()) params.realName = queryParams.realName.trim()
      if (queryParams.phone?.trim()) params.phone = queryParams.phone.trim()
      if (queryParams.roleId !== undefined && queryParams.roleId !== null) {
        params.roleId = queryParams.roleId
      }
      if (queryParams.status !== undefined && queryParams.status !== null) {
        params.status = queryParams.status
      }

      const pageData = await getUserPageApi(params)
      tableData.value = pageData.records
      total.value = pageData.total
    } catch (error) {
      console.error('获取用户列表失败:', error)
      ElMessage.error('获取用户列表失败，请稍后重试')
    } finally {
      loading.value = false
    }
  }

  const tableIndexMethod = (index: number) => {
    const pageNum = queryParams.pageNum ?? 1
    const pageSize = queryParams.pageSize ?? 20
    return (pageNum - 1) * pageSize + index + 1
  }

  const handleSearch = () => {
    queryParams.pageNum = 1
    void fetchUserList()
  }

  const handleReset = () => {
    queryParams.username = ''
    queryParams.realName = ''
    queryParams.phone = ''
    queryParams.roleId = undefined
    queryParams.status = undefined
    handleSearch()
  }

  const applyStatusFilter = (status: number | undefined) => {
    queryParams.status = status
    queryParams.pageNum = 1
    void fetchUserList()
  }

  const applyRoleFilter = (roleId: string | number | undefined) => {
    queryParams.roleId = roleId
    queryParams.pageNum = 1
    void fetchUserList()
  }

  const removeFilterChip = (key: string) => {
    if (key === 'username') queryParams.username = ''
    else if (key === 'realName') queryParams.realName = ''
    else if (key === 'phone') queryParams.phone = ''
    else if (key === 'roleId') queryParams.roleId = undefined
    else if (key === 'status') queryParams.status = undefined
    queryParams.pageNum = 1
    void fetchUserList()
  }

  const handleSelectionChange = (rows: UserVO[]) => {
    selectedRows.value = rows
  }

  const clearSelection = () => {
    tableRef.value?.clearSelection()
    selectedRows.value = []
  }

  const handleAdd = () => {
    isCreate.value = true
    dialogTitle.value = '新增用户'
    resetUserForm()
    userForm.roleId = roleOptions.value[0]?.id
    dialogVisible.value = true
  }

  const handleEdit = (row: UserVO) => {
    isCreate.value = false
    dialogTitle.value = '编辑用户'
    Object.assign(userForm, {
      id: row.id,
      username: row.username,
      realName: row.realName,
      email: row.email,
      phone: row.phone,
      password: '',
      roleId: row.roleId,
      status: row.status,
    })
    dialogVisible.value = true
  }

  const handleDialogClose = () => {
    resetUserForm()
  }

  const handleDelete = async (row: UserVO) => {
    try {
      await ElMessageBox.confirm(
        `确定要删除用户"${row.username}"吗？此操作不可恢复！`,
        '警告',
        {
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
          type: 'warning',
          draggable: true,
        },
      )
      await deleteUserApi(row.id)
      ElMessage.success('删除成功')
      void fetchUserList()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除失败:', error)
        ElMessage.error('删除失败，请稍后重试')
      }
    }
  }

  const handleBatchDelete = async () => {
    const count = selectedRows.value.length
    const usernames = selectedRows.value.map((r) => r.username).join('、')
    try {
      await ElMessageBox.confirm(
        `确定要删除以下 ${count} 个用户吗？此操作不可恢复！\n\n${usernames}`,
        '警告',
        {
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
          type: 'warning',
          draggable: true,
        },
      )
      await Promise.all(selectedRows.value.map((row) => deleteUserApi(row.id)))
      ElMessage.success(`成功删除 ${count} 个用户`)
      clearSelection()
      void fetchUserList()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('批量删除失败:', error)
        ElMessage.error('批量删除失败，请稍后重试')
      }
    }
  }

  const handleStatusChange = async (row: UserVO, _newStatus: number | string | boolean) => {
    try {
      await updateUserStatusApi(row.id, { status: row.status })
      ElMessage.success(`已${row.status === 1 ? '启用' : '禁用'}用户"${row.username}"`)
    } catch (error) {
      console.error('状态更新失败:', error)
      row.status = row.status === 1 ? 0 : 1
      ElMessage.error(error instanceof Error ? error.message : '状态更新失败，请稍后重试')
    }
  }

  const handleResetPassword = async (row: UserVO) => {
    try {
      const { value } = await ElMessageBox.prompt(
        `请输入用户 "${row.username}" 的新密码（至少6位）`,
        '重置密码',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputType: 'password',
          inputPattern: /^.{6,64}$/,
          inputErrorMessage: '密码长度须在 6-64 位之间',
        },
      )
      await resetUserPasswordApi(row.id, { newPassword: value })
      ElMessage.success('密码已重置，用户需重新登录')
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      console.error('重置密码失败:', error)
      ElMessage.error(error instanceof Error ? error.message : '重置密码失败')
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      if (isCreate.value) {
        const createData: UserCreateDTO = {
          username: userForm.username,
          realName: userForm.realName,
          email: userForm.email || undefined,
          phone: userForm.phone || undefined,
          password: userForm.password,
          roleId: userForm.roleId!,
          status: userForm.status,
        }
        await createUserApi(createData)
        ElMessage.success('创建成功')
      } else {
        const updateData: UserUpdateDTO = {
          id: userForm.id!,
          realName: userForm.realName,
          email: userForm.email || undefined,
          phone: userForm.phone || undefined,
          roleId: userForm.roleId,
          status: userForm.status,
        }
        if (userForm.password) {
          updateData.password = userForm.password
        }
        await updateUserApi(updateData)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      void fetchUserList()
    } catch (error) {
      console.error('提交失败:', error)
      ElMessage.error(isCreate.value ? '创建失败，请稍后重试' : '更新失败，请稍后重试')
    } finally {
      submitLoading.value = false
    }
  }

  const getRoleText = (role?: string) => {
    if (!role) return '-'
    const roleMap: Record<string, string> = {
      admin: '管理员',
      operator: '操作员',
      user: '普通用户',
    }
    return roleMap[role] || role
  }

  const getRoleType = (role?: string): 'success' | 'warning' | 'danger' | 'info' => {
    if (!role) return 'info'
    const roleMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
      admin: 'danger',
      operator: 'warning',
      user: 'info',
    }
    return roleMap[role] || 'info'
  }

  const tableRowClassName = ({ row }: { row: UserVO }) =>
    row.status === 1 ? '' : 'user-row--disabled'

  return {
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
  }
}
