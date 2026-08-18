import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormRules, type TableInstance } from 'element-plus'
import { Folder, Document, Link, Key } from '@element-plus/icons-vue'
import { getMenuTreeApi, createMenuApi, updateMenuApi, deleteMenuApi } from '@/api/menu'
import type { MenuVO, MenuCreateDTO, MenuUpdateDTO } from '@/types/menu'

export interface MenuFormModel {
  parentId: number | string
  title: string
  menuType: string
  path: string
  routeName: string
  redirect: string
  icon: string
  component: string
  meta: string
  sort: number
  visible: number
  requiresAuth: number
  status: number
}

export const menuTypes = [
  { value: 'DIRECTORY', label: '目录' },
  { value: 'MENU', label: '菜单' },
  { value: 'PAGE', label: '页面' },
  { value: 'BUTTON', label: '按钮' },
] as const

function flattenMenus(nodes: MenuVO[]): MenuVO[] {
  const result: MenuVO[] = []
  const walk = (list: MenuVO[]) => {
    list.forEach((node) => {
      result.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(nodes)
  return result
}

function stripButtonNodes(nodes: MenuVO[]): MenuVO[] {
  return nodes
    .filter((node) => node.menuType !== 'BUTTON')
    .map((node) => ({
      ...node,
      children: node.children?.length ? stripButtonNodes(node.children) : undefined,
    }))
}

export function useMenuList() {
  const loading = ref(false)
  const submitLoading = ref(false)
  const rawTableData = ref<MenuVO[]>([])
  const tableRef = ref<TableInstance>()

  const queryParams = reactive({
    title: '',
    path: '',
    menuType: '' as string,
    status: undefined as number | undefined,
  })

  const dialogVisible = ref(false)
  const dialogTitle = ref('新增菜单')
  const isCreate = ref(true)
  const editingId = ref<number | string>()

  const defaultForm = (parentId: number | string = 0): MenuFormModel => ({
    parentId,
    title: '',
    menuType: 'MENU',
    path: '',
    routeName: '',
    redirect: '',
    icon: '',
    component: '',
    meta: '',
    sort: 0,
    visible: 1,
    requiresAuth: 1,
    status: 1,
  })

  const menuForm = reactive<MenuFormModel>(defaultForm())

  const formRules = computed<FormRules>(() => ({
    parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
    title: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
    menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
    ...(menuForm.menuType === 'BUTTON'
      ? {
          routeName: [{ required: true, message: '请输入权限标识', trigger: 'blur' }],
        }
      : {}),
  }))

  function matchesNode(node: MenuVO): boolean {
    if (queryParams.status === 0 || queryParams.status === 1) {
      if ((node.status ?? 1) !== queryParams.status) return false
    }
    if (queryParams.menuType && node.menuType !== queryParams.menuType) return false
    if (queryParams.title?.trim() && !node.title.includes(queryParams.title.trim())) return false
    if (queryParams.path?.trim() && !(node.path || '').includes(queryParams.path.trim())) return false
    return true
  }

  function filterMenuTree(nodes: MenuVO[]): MenuVO[] {
    const result: MenuVO[] = []
    for (const node of nodes) {
      const children = node.children?.length ? filterMenuTree(node.children) : []
      if (matchesNode(node) || children.length > 0) {
        result.push({
          ...node,
          children: children.length ? children : undefined,
        })
      }
    }
    return result
  }

  const allMenus = computed(() => flattenMenus(rawTableData.value))

  const displayTableData = computed(() => {
    if (!hasActiveFilter.value) return rawTableData.value
    return filterMenuTree(rawTableData.value)
  })

  const menuLevelMap = computed(() => {
    const map = new Map<string | number, number>()
    const walk = (nodes: MenuVO[], level: number) => {
      nodes.forEach((node) => {
        map.set(node.id, level)
        if (node.children?.length) walk(node.children, level + 1)
      })
    }
    walk(displayTableData.value, 0)
    return map
  })

  const getMenuLevel = (id: string | number) => menuLevelMap.value.get(id) ?? 0

  const displayMenuCount = computed(() => flattenMenus(displayTableData.value).length)

  const totalMenuCount = computed(() => allMenus.value.length)

  const menuTypeCount = computed(() => {
    return new Set(allMenus.value.map((node) => node.menuType).filter(Boolean)).size
  })

  const pageEnableRate = computed(() => {
    if (!allMenus.value.length) return 0
    const enabled = allMenus.value.filter((node) => (node.status ?? 1) === 1).length
    return Math.round((enabled / allMenus.value.length) * 100)
  })

  const enabledMenuCount = computed(() => allMenus.value.filter((node) => (node.status ?? 1) === 1).length)

  const typeCountMap = computed(() => {
    const map: Record<string, number> = {}
    allMenus.value.forEach((node) => {
      if (node.menuType) {
        map[node.menuType] = (map[node.menuType] || 0) + 1
      }
    })
    return map
  })

  const filterChips = computed(() => {
    const chips: { key: string; label: string }[] = []
    if (queryParams.title?.trim()) {
      chips.push({ key: 'title', label: `名称：${queryParams.title.trim()}` })
    }
    if (queryParams.path?.trim()) {
      chips.push({ key: 'path', label: `路径：${queryParams.path.trim()}` })
    }
    if (queryParams.menuType) {
      const type = menuTypes.find((item) => item.value === queryParams.menuType)
      chips.push({ key: 'menuType', label: `类型：${type?.label || queryParams.menuType}` })
    }
    if (queryParams.status === 0 || queryParams.status === 1) {
      chips.push({ key: 'status', label: queryParams.status === 1 ? '启用' : '禁用' })
    }
    return chips
  })

  const activeFilterCount = computed(() => filterChips.value.length)

  const hasActiveFilter = computed(() => activeFilterCount.value > 0)

  const parentTreeOptions = computed(() => {
    return [{ id: 0, title: '根目录', children: stripButtonNodes(rawTableData.value) }]
  })

  const getMenuTypeText = (type: string) => {
    const map: Record<string, string> = {
      DIRECTORY: '目录',
      MENU: '菜单',
      PAGE: '页面',
      BUTTON: '按钮',
    }
    return map[type] || type
  }

  const getMenuTypeKey = (type: string) => {
    const map: Record<string, string> = {
      DIRECTORY: 'directory',
      MENU: 'menu',
      PAGE: 'page',
      BUTTON: 'button',
    }
    return map[type] || 'default'
  }

  const getMenuTypeIcon = (type: string) => {
    const map: Record<string, typeof Folder> = {
      DIRECTORY: Folder,
      MENU: Document,
      PAGE: Link,
      BUTTON: Key,
    }
    return map[type] || Document
  }

  function tableRowClassName({ row }: { row: MenuVO }) {
    const level = Math.min(getMenuLevel(row.id), 4)
    const classes = [`menu-row--${getMenuTypeKey(row.menuType)}`, `menu-row--level-${level}`]
    if ((row.status ?? 1) !== 1) classes.push('menu-row--disabled')
    return classes.join(' ')
  }

  function setRowsExpansion(nodes: MenuVO[], expanded: boolean) {
    nodes.forEach((node) => {
      tableRef.value?.toggleRowExpansion(node, expanded)
      if (node.children?.length) setRowsExpansion(node.children, expanded)
    })
  }

  function expandAllRows() {
    setRowsExpansion(displayTableData.value, true)
  }

  function collapseAllRows() {
    setRowsExpansion(displayTableData.value, false)
  }

  function applyTypeFilter(type: string) {
    queryParams.menuType = type
  }

  function applyStatusFilter(status: number | undefined) {
    queryParams.status = status
  }

  function removeFilterChip(key: string) {
    if (key === 'title') queryParams.title = ''
    else if (key === 'path') queryParams.path = ''
    else if (key === 'menuType') queryParams.menuType = ''
    else if (key === 'status') queryParams.status = undefined
  }

  const handleSearch = () => {
    // 筛选由 computed 响应式驱动，此处保留交互一致性
  }

  const handleReset = () => {
    queryParams.title = ''
    queryParams.path = ''
    queryParams.menuType = ''
    queryParams.status = undefined
  }

  const resetForm = (parentId: number | string = 0) => {
    Object.assign(menuForm, defaultForm(parentId))
  }

  const fetchTree = async () => {
    loading.value = true
    try {
      rawTableData.value = await getMenuTreeApi()
    } catch (error) {
      console.error('获取菜单树失败:', error)
    } finally {
      loading.value = false
    }
  }

  const handleAdd = (parent?: MenuVO) => {
    isCreate.value = true
    dialogTitle.value = parent
      ? `新增${parent.menuType === 'MENU' ? '权限' : '子菜单'} - ${parent.title}`
      : '新增菜单'
    editingId.value = undefined
    resetForm(parent?.id ?? 0)
    if (parent?.menuType === 'MENU') {
      menuForm.menuType = 'BUTTON'
      menuForm.visible = 0
    }
    dialogVisible.value = true
  }

  const handleEdit = (row: MenuVO) => {
    isCreate.value = false
    dialogTitle.value = '编辑菜单'
    editingId.value = row.id
    Object.assign(menuForm, {
      parentId: row.parentId ?? 0,
      title: row.title,
      menuType: row.menuType,
      path: row.path || '',
      routeName: row.routeName || '',
      redirect: row.redirect || '',
      icon: row.icon || '',
      component: row.component || '',
      meta: row.meta || '',
      sort: row.sort ?? 0,
      visible: row.visible ?? 1,
      requiresAuth: row.requiresAuth ?? 1,
      status: row.status ?? 1,
    })
    dialogVisible.value = true
  }

  const handleDelete = async (row: MenuVO) => {
    try {
      await ElMessageBox.confirm(`确定删除菜单「${row.title}」吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteMenuApi(row.id)
      ElMessage.success('删除成功')
      fetchTree()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除菜单失败:', error)
      }
    }
  }

  const buildPayloadFields = () => {
    const isButton = menuForm.menuType === 'BUTTON'
    return {
      parentId: menuForm.parentId,
      title: menuForm.title.trim(),
      menuType: menuForm.menuType,
      path: isButton ? undefined : menuForm.path.trim() || undefined,
      routeName: menuForm.routeName.trim() || undefined,
      redirect: isButton ? undefined : menuForm.redirect.trim() || undefined,
      icon: isButton ? undefined : menuForm.icon.trim() || undefined,
      component: isButton ? undefined : menuForm.component.trim() || undefined,
      meta: isButton ? undefined : menuForm.meta.trim() || undefined,
      sort: menuForm.sort,
      visible: isButton ? 0 : menuForm.visible,
      requiresAuth: menuForm.requiresAuth,
      status: menuForm.status,
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      if (isCreate.value) {
        const payload: MenuCreateDTO = buildPayloadFields()
        await createMenuApi(payload)
        ElMessage.success('新增成功')
      } else {
        const payload: MenuUpdateDTO = {
          id: editingId.value!,
          ...buildPayloadFields(),
        }
        await updateMenuApi(payload)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      fetchTree()
    } catch (error) {
      console.error('保存菜单失败:', error)
    } finally {
      submitLoading.value = false
    }
  }

  const handleDialogClose = () => {
    resetForm()
  }

  return {
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
  }
}
