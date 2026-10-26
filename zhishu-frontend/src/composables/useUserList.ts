import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus'
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
import { useCrudList, type CrudQuery, type FilterChip } from '@/composables/useCrudList'
import { isMessageBoxCancel } from '@/utils/uiMessage'

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

/** 查询条件必须带分页字段，useCrudList 依赖它做序号与翻页 */
type UserListQuery = UserQueryDTO & CrudQuery

export function useUserList() {
  const roleOptions = ref<RoleVO[]>([])
  const tableData = ref<UserVO[]>([])
  const total = ref(0)
  const selectedRows = ref<UserVO[]>([])

  const defaultQuery = (): UserListQuery => ({
    pageNum: 1,
    pageSize: 20,
    username: '',
    realName: '',
    phone: '',
    roleId: undefined,
    status: undefined,
  })

  const defaultForm = (): UserFormModel => ({
    username: '',
    realName: '',
    email: '',
    phone: '',
    password: '',
    roleId: undefined,
    status: 1,
  })

  const crud = useCrudList<UserVO, UserFormModel, UserListQuery>({
    defaultQuery,
    defaultForm,
    createTitle: '新增用户',
    editTitle: '编辑用户',
    reload: () => fetchUserList(),
    deleteApi: (id) => deleteUserApi(id),
    rowLabel: (row) => `用户"${row.username}"`,
    chipBuilder: (query): FilterChip[] => {
      const chips: FilterChip[] = []
      if (query.username?.trim())
        chips.push({ key: 'username', label: `用户名：${query.username.trim()}` })
      if (query.realName?.trim())
        chips.push({ key: 'realName', label: `姓名：${query.realName.trim()}` })
      if (query.phone?.trim()) chips.push({ key: 'phone', label: `手机：${query.phone.trim()}` })
      if (query.roleId != null && query.roleId !== '') {
        const role = roleOptions.value.find((item) => String(item.id) === String(query.roleId))
        chips.push({ key: 'roleId', label: `角色：${role?.roleName || query.roleId}` })
      }
      if (query.status === 0 || query.status === 1) {
        chips.push({ key: 'status', label: query.status === 1 ? '启用' : '禁用' })
      }
      return chips
    },
  })

  const {
    loading,
    submitLoading,
    tableRef,
    queryParams,
    form: userForm,
    dialogVisible,
    dialogTitle,
    isCreate,
    filterChips,
    activeFilterCount,
  } = crud

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
    } finally {
      loading.value = false
    }
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

  const handleSelectionChange = (rows: UserVO[]) => {
    selectedRows.value = rows
  }

  const clearSelection = () => {
    tableRef.value?.clearSelection()
    selectedRows.value = []
  }

  const handleAdd = () => {
    crud.openCreate({ roleId: roleOptions.value[0]?.id })
  }

  const handleEdit = (row: UserVO) => {
    crud.openEdit(row.id, {
      id: row.id,
      username: row.username,
      realName: row.realName,
      email: row.email,
      phone: row.phone,
      password: '',
      roleId: row.roleId,
      status: row.status,
    })
  }

  const handleBatchDelete = async () => {
    await crud.handleBatchDelete(selectedRows.value, (row) => row.username)
    clearSelection()
  }

  const handleStatusChange = async (row: UserVO, _newStatus: number | string | boolean) => {
    try {
      await updateUserStatusApi(row.id, { status: row.status })
      ElMessage.success(`已${row.status === 1 ? '启用' : '禁用'}用户"${row.username}"`)
    } catch (error) {
      console.error('状态更新失败:', error)
      row.status = row.status === 1 ? 0 : 1
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
      if (isMessageBoxCancel(error)) return
      console.error('重置密码失败:', error)
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
    return roleMap[role] ?? roleMap[role.toLowerCase()] ?? role
  }

  const getRoleType = (role?: string): 'success' | 'warning' | 'danger' | 'info' => {
    if (!role) return 'info'
    const roleMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
      admin: 'danger',
      operator: 'warning',
      user: 'info',
    }
    return roleMap[role.toLowerCase()] ?? 'info'
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
    tableIndexMethod: crud.tableIndexMethod,
    handleSearch: crud.handleSearch,
    handleReset: crud.handleReset,
    applyStatusFilter,
    applyRoleFilter,
    removeFilterChip: crud.removeFilterChip,
    handleSelectionChange,
    clearSelection,
    handleAdd,
    handleEdit,
    handleDialogClose: crud.handleDialogClose,
    handleDelete: crud.handleDelete,
    handleBatchDelete,
    handleStatusChange,
    handleResetPassword,
    handleSubmit,
    getRoleText,
    getRoleType,
    tableRowClassName,
  }
}
