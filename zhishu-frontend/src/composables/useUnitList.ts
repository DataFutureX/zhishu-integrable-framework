import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormRules, type TableInstance } from 'element-plus'
import {
  getUnitPageApi,
  getUnitTreeApi,
  createUnitApi,
  updateUnitApi,
  deleteUnitApi,
} from '@/api/unit'
import type { UnitVO, UnitQueryDTO, UnitCreateDTO, UnitUpdateDTO } from '@/types/unit'
import { UnitStatus } from '@/types/unit'

type ViewMode = 'tree' | 'table'

const ROOT_PARENT_ID = 0

export interface UnitFormModel {
  parentId?: number | string
  unitCode: string
  unitName: string
  unitType: string
  region: string
  address: string
  contactPerson: string
  contactPhone: string
  sort: number
  status: number
  remark: string
}

function flattenUnits(nodes: UnitVO[]): UnitVO[] {
  const result: UnitVO[] = []
  const walk = (list: UnitVO[]) => {
    list.forEach((node) => {
      result.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(nodes)
  return result
}

function filterTreeExcludeId(nodes: UnitVO[], excludeId: number | string): UnitVO[] {
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({
      ...node,
      children: node.children?.length ? filterTreeExcludeId(node.children, excludeId) : undefined,
    }))
}

function filterUnitTree(nodes: UnitVO[], predicate: (unit: UnitVO) => boolean): UnitVO[] {
  const result: UnitVO[] = []
  for (const node of nodes) {
    const children = node.children?.length ? filterUnitTree(node.children, predicate) : []
    if (predicate(node) || children.length > 0) {
      result.push({
        ...node,
        children: children.length ? children : undefined,
      })
    }
  }
  return result
}

const defaultUnitForm = (): UnitFormModel => ({
  parentId: undefined,
  unitCode: '',
  unitName: '',
  unitType: '',
  region: '',
  address: '',
  contactPerson: '',
  contactPhone: '',
  sort: 0,
  status: UnitStatus.ENABLED,
  remark: '',
})

export function useUnitList() {
  const tableRef = ref<TableInstance>()

  const loading = ref(false)
  const submitLoading = ref(false)
  const filterExpanded = ref(true)
  const viewMode = ref<ViewMode>('tree')
  const tableData = ref<UnitVO[]>([])
  const treeData = ref<UnitVO[]>([])
  const total = ref(0)
  const unitTree = ref<UnitVO[]>([])

  const queryParams = reactive<Required<Pick<UnitQueryDTO, 'pageNum' | 'pageSize'>> & UnitQueryDTO>({
    unitCode: '',
    unitName: '',
    unitType: '',
    parentId: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 20,
  })

  const dialogVisible = ref(false)
  const dialogTitle = ref('新增单位')
  const isCreate = ref(true)
  const editingId = ref<number | string>()

  const unitForm = reactive<UnitFormModel>(defaultUnitForm())

  const formRules: FormRules = {
    unitName: [{ required: true, message: '请输入单位名称', trigger: 'blur' }],
  }

  const allUnitCount = computed(() => flattenUnits(unitTree.value).length)

  const currentViewUnits = computed(() =>
    viewMode.value === 'table' ? tableData.value : flattenUnits(treeData.value),
  )

  const pageEnableRate = computed(() => {
    const rows = currentViewUnits.value
    if (!rows.length) return 0
    const enabled = rows.filter((row) => row.status === 1).length
    return Math.round((enabled / rows.length) * 100)
  })

  const pageEnabledCount = computed(() => currentViewUnits.value.filter((row) => row.status === 1).length)

  const unitTypeOptions = computed(() => {
    const types = new Set<string>()
    flattenUnits(unitTree.value).forEach((unit) => {
      if (unit.unitType?.trim()) types.add(unit.unitType.trim())
    })
    return Array.from(types).sort()
  })

  const typeCountMap = computed(() => {
    const map: Record<string, number> = {}
    flattenUnits(unitTree.value).forEach((unit) => {
      if (unit.unitType?.trim()) {
        const key = unit.unitType.trim()
        map[key] = (map[key] || 0) + 1
      }
    })
    return map
  })

  const filterChips = computed(() => {
    const chips: { key: string; label: string }[] = []
    if (queryParams.unitCode?.trim()) {
      chips.push({ key: 'unitCode', label: `编码：${queryParams.unitCode.trim()}` })
    }
    if (queryParams.unitName?.trim()) {
      chips.push({ key: 'unitName', label: `名称：${queryParams.unitName.trim()}` })
    }
    if (queryParams.unitType?.trim()) {
      chips.push({ key: 'unitType', label: `类型：${queryParams.unitType.trim()}` })
    }
    if (queryParams.parentId != null && queryParams.parentId !== '') {
      chips.push({ key: 'parentId', label: '已选上级单位' })
    }
    if (queryParams.status === 0 || queryParams.status === 1) {
      chips.push({ key: 'status', label: queryParams.status === 1 ? '启用' : '停用' })
    }
    return chips
  })

  const activeFilterCount = computed(() => filterChips.value.length)

  const withRootOption = (nodes: UnitVO[]): UnitVO[] => [
    {
      id: ROOT_PARENT_ID,
      unitCode: '',
      unitName: '顶级单位',
      children: nodes,
    },
  ]

  const parentTreeOptions = computed(() => withRootOption(unitTree.value))

  const formParentTreeOptions = computed(() => {
    if (!editingId.value) return parentTreeOptions.value
    return withRootOption(filterTreeExcludeId(unitTree.value, editingId.value))
  })

  const buildQueryPayload = (): UnitQueryDTO => {
    const payload: UnitQueryDTO = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
    }
    if (queryParams.unitCode?.trim()) payload.unitCode = queryParams.unitCode.trim()
    if (queryParams.unitName?.trim()) payload.unitName = queryParams.unitName.trim()
    if (queryParams.unitType?.trim()) payload.unitType = queryParams.unitType.trim()
    if (queryParams.parentId !== undefined && queryParams.parentId !== null && queryParams.parentId !== '') {
      payload.parentId = queryParams.parentId === ROOT_PARENT_ID ? ROOT_PARENT_ID : queryParams.parentId
    }
    if (queryParams.status !== undefined && queryParams.status !== null) {
      payload.status = queryParams.status
    }
    return payload
  }

  const fetchUnitTree = async () => {
    try {
      unitTree.value = await getUnitTreeApi()
    } catch (error) {
      console.error('获取单位树失败:', error)
    }
  }

  const fetchTreeList = async () => {
    loading.value = true
    try {
      const status = queryParams.status
      let data = await getUnitTreeApi(status)

      if (queryParams.unitCode?.trim()) {
        const code = queryParams.unitCode.trim()
        data = filterUnitTree(data, (unit) => (unit.unitCode || '').includes(code))
      }
      if (queryParams.unitName?.trim()) {
        const name = queryParams.unitName.trim()
        data = filterUnitTree(data, (unit) => unit.unitName.includes(name))
      }
      if (queryParams.unitType?.trim()) {
        const type = queryParams.unitType.trim()
        data = filterUnitTree(data, (unit) => (unit.unitType || '').includes(type))
      }
      if (queryParams.parentId != null && queryParams.parentId !== '') {
        data = filterUnitTree(data, (unit) => String(unit.parentId) === String(queryParams.parentId))
      }

      treeData.value = data
    } catch (error) {
      console.error('获取单位树形列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  const fetchPageList = async () => {
    loading.value = true
    try {
      const pageData = await getUnitPageApi(buildQueryPayload())
      tableData.value = pageData.records
      total.value = pageData.total
    } catch (error) {
      console.error('获取单位列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  const refreshCurrentView = async () => {
    if (viewMode.value === 'tree') {
      await fetchTreeList()
    } else {
      await fetchPageList()
    }
  }

  const switchView = async (mode: ViewMode) => {
    viewMode.value = mode
    await refreshCurrentView()
  }

  const tableIndexMethod = (index: number) => {
    const pageNum = queryParams.pageNum ?? 1
    const pageSize = queryParams.pageSize ?? 20
    return (pageNum - 1) * pageSize + index + 1
  }

  const unitLevelMap = computed(() => {
    const map = new Map<string | number, number>()
    const walk = (nodes: UnitVO[], level: number) => {
      nodes.forEach((node) => {
        map.set(node.id, level)
        if (node.children?.length) walk(node.children, level + 1)
      })
    }
    walk(treeData.value, 0)
    return map
  })

  const getUnitLevel = (id: string | number) => unitLevelMap.value.get(id) ?? 0

  const getUnitNodeKind = (row: UnitVO): 'root' | 'branch' | 'leaf' => {
    const level = getUnitLevel(row.id)
    if (level === 0) return 'root'
    if (row.children?.length) return 'branch'
    return 'leaf'
  }

  const tableRowClassName = ({ row }: { row: UnitVO }) => {
    const level = Math.min(getUnitLevel(row.id), 4)
    const kind = getUnitNodeKind(row)
    const classes = [`unit-row--${kind}`, `unit-row--level-${level}`]
    if ((row.status ?? 1) !== 1) classes.push('unit-row--disabled')
    return classes.join(' ')
  }

  function setRowsExpansion(nodes: UnitVO[], expanded: boolean) {
    nodes.forEach((node) => {
      tableRef.value?.toggleRowExpansion(node, expanded)
      if (node.children?.length) setRowsExpansion(node.children, expanded)
    })
  }

  function expandAllRows() {
    setRowsExpansion(treeData.value, true)
  }

  function collapseAllRows() {
    setRowsExpansion(treeData.value, false)
  }

  const handleSearch = () => {
    queryParams.pageNum = 1
    void refreshCurrentView()
  }

  const handleReset = () => {
    queryParams.unitCode = ''
    queryParams.unitName = ''
    queryParams.unitType = ''
    queryParams.parentId = undefined
    queryParams.status = undefined
    queryParams.pageNum = 1
    void refreshCurrentView()
  }

  const applyStatusFilter = (status: number | undefined) => {
    queryParams.status = status
    queryParams.pageNum = 1
    void refreshCurrentView()
  }

  const applyTypeFilter = (type: string) => {
    queryParams.unitType = type
    queryParams.pageNum = 1
    void refreshCurrentView()
  }

  const removeFilterChip = (key: string) => {
    if (key === 'unitCode') queryParams.unitCode = ''
    else if (key === 'unitName') queryParams.unitName = ''
    else if (key === 'unitType') queryParams.unitType = ''
    else if (key === 'parentId') queryParams.parentId = undefined
    else if (key === 'status') queryParams.status = undefined
    queryParams.pageNum = 1
    void refreshCurrentView()
  }

  const resetUnitForm = () => {
    Object.assign(unitForm, defaultUnitForm())
  }

  const handleAdd = (parent?: UnitVO) => {
    isCreate.value = true
    dialogTitle.value = parent ? `新增下级单位 - ${parent.unitName}` : '新增单位'
    editingId.value = undefined
    resetUnitForm()
    if (parent) {
      unitForm.parentId = parent.id
    }
    dialogVisible.value = true
  }

  const handleEdit = (row: UnitVO) => {
    isCreate.value = false
    dialogTitle.value = '编辑单位'
    editingId.value = row.id
    Object.assign(unitForm, {
      parentId: row.parentId && row.parentId !== ROOT_PARENT_ID ? row.parentId : undefined,
      unitCode: row.unitCode,
      unitName: row.unitName,
      unitType: row.unitType || '',
      region: row.region || '',
      address: row.address || '',
      contactPerson: row.contactPerson || '',
      contactPhone: row.contactPhone || '',
      sort: row.sort ?? 0,
      status: row.status ?? UnitStatus.ENABLED,
      remark: row.remark || '',
    })
    dialogVisible.value = true
  }

  const handleDelete = async (row: UnitVO) => {
    try {
      await ElMessageBox.confirm(`确定删除单位「${row.unitName}」吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteUnitApi(row.id)
      ElMessage.success('删除成功')
      await fetchUnitTree()
      await refreshCurrentView()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('删除单位失败:', error)
      }
    }
  }

  const normalizeParentId = (parentId?: number | string) => {
    if (parentId === undefined || parentId === null || parentId === '') {
      return ROOT_PARENT_ID
    }
    return parentId
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      const parentId = normalizeParentId(unitForm.parentId)
      if (isCreate.value) {
        const payload: UnitCreateDTO = {
          parentId,
          unitCode: unitForm.unitCode.trim() || undefined,
          unitName: unitForm.unitName.trim(),
          unitType: unitForm.unitType.trim() || undefined,
          region: unitForm.region.trim() || undefined,
          address: unitForm.address.trim() || undefined,
          contactPerson: unitForm.contactPerson.trim() || undefined,
          contactPhone: unitForm.contactPhone.trim() || undefined,
          sort: unitForm.sort,
          status: unitForm.status,
          remark: unitForm.remark.trim() || undefined,
        }
        await createUnitApi(payload)
        ElMessage.success('新增成功')
      } else {
        const payload: UnitUpdateDTO = {
          id: editingId.value!,
          parentId,
          unitCode: unitForm.unitCode.trim() || undefined,
          unitName: unitForm.unitName.trim(),
          unitType: unitForm.unitType.trim() || undefined,
          region: unitForm.region.trim() || undefined,
          address: unitForm.address.trim() || undefined,
          contactPerson: unitForm.contactPerson.trim() || undefined,
          contactPhone: unitForm.contactPhone.trim() || undefined,
          sort: unitForm.sort,
          status: unitForm.status,
          remark: unitForm.remark.trim() || undefined,
        }
        await updateUnitApi(payload)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      await fetchUnitTree()
      await refreshCurrentView()
    } catch (error) {
      console.error('保存单位失败:', error)
    } finally {
      submitLoading.value = false
    }
  }

  const handleDialogClose = () => {
    resetUnitForm()
    editingId.value = undefined
  }

  const initUnitList = async () => {
    await fetchUnitTree()
    await fetchTreeList()
  }

  return {
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
    getUnitLevel,
    getUnitNodeKind,
    expandAllRows,
    collapseAllRows,
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
  }
}
