import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'
import { ElMessageBox, type TableInstance } from 'element-plus'
import { isMessageBoxCancel } from '@/utils/uiMessage'

/**
 * 列表页 CRUD 骨架（查询条件 / 分页 / 弹窗 / 删除确认）。
 *
 * 用户、单位、菜单三份 use*List 里 loading / queryParams / dialogVisible / isCreate /
 * filterChips / tableIndexMethod / handleDelete 的写法完全同构，只是字段不同；
 * 字段与文案通过 options 注入，行为（重置页码、取消不报错、成功后刷新）在这里统一。
 */

export interface CrudQuery {
  pageNum: number
  pageSize: number
}

export interface FilterChip {
  key: string
  label: string
}

export interface UseCrudListOptions<
  TRow extends { id: string | number },
  TForm extends object,
  TQuery extends object,
> {
  /** 查询条件初值（分页列表需含 pageNum / pageSize；树形列表可不传） */
  defaultQuery: () => TQuery
  /** 表单初值 */
  defaultForm: () => TForm
  /** 新增 / 编辑弹窗标题 */
  createTitle?: string
  editTitle?: string
  /** 列表刷新（查询、重置、增删改成功后调用） */
  reload: () => void | Promise<unknown>
  /** 单条删除；不传则不提供 handleDelete */
  deleteApi?: (id: TRow['id']) => Promise<unknown>
  /** 删除确认文案里的行标识（用户名 / 单位名…） */
  rowLabel?: (row: TRow) => string
  /** 自定义删除确认文案（默认「确定要删除「xx」？」） */
  deleteConfirmText?: (row: TRow) => string
  /** 删除成功后的回调（用于弹成功提示等） */
  onDeleted?: (row: TRow) => void
  /** 由查询条件生成筛选 chip，用于顶部条件回显 */
  chipBuilder?: (query: TQuery) => FilterChip[]
  /** 移除某个 chip 时的字段清理（默认按 defaultQuery 的同名字段复位） */
  clearChipField?: (key: string, query: TQuery) => void
  /** 前端过滤型列表（如菜单树）：查询 / 重置 / 移除 chip 不重新请求接口 */
  clientSideFiltering?: boolean
}

export interface CrudList<
  TRow extends { id: string | number },
  TForm extends object,
  TQuery extends object,
> {
  loading: Ref<boolean>
  submitLoading: Ref<boolean>
  tableRef: Ref<TableInstance | undefined>
  queryParams: TQuery
  form: TForm
  dialogVisible: Ref<boolean>
  dialogTitle: Ref<string>
  isCreate: Ref<boolean>
  editingId: Ref<TRow['id'] | undefined>
  filterChips: ComputedRef<FilterChip[]>
  activeFilterCount: ComputedRef<number>
  resetQuery: () => void
  resetForm: () => void
  tableIndexMethod: (index: number) => number
  handleSearch: () => void
  handleReset: () => void
  removeFilterChip: (key: string) => void
  openCreate: (overrides?: Partial<TForm>) => void
  openEdit: (id: TRow['id'], values: Partial<TForm>) => void
  handleDialogClose: () => void
  handleDelete: (row: TRow) => Promise<void>
  handleBatchDelete: (rows: TRow[], labelOf?: (row: TRow) => string) => Promise<void>
}

export function useCrudList<
  TRow extends { id: string | number },
  TForm extends object,
  TQuery extends object,
>(options: UseCrudListOptions<TRow, TForm, TQuery>): CrudList<TRow, TForm, TQuery> {
  const loading = ref(false)
  const submitLoading = ref(false)
  const tableRef = ref<TableInstance>()
  const queryParams = reactive(options.defaultQuery()) as TQuery
  const form = reactive(options.defaultForm()) as TForm

  const dialogVisible = ref(false)
  const dialogTitle = ref(options.createTitle ?? '新增')
  const isCreate = ref(true)
  const editingId = ref<TRow['id'] | undefined>(undefined) as Ref<TRow['id'] | undefined>

  const filterChips = computed<FilterChip[]>(() =>
    options.chipBuilder ? options.chipBuilder(queryParams) : [],
  )
  const activeFilterCount = computed(() => filterChips.value.length)

  const resetQuery = () => {
    Object.assign(queryParams, options.defaultQuery())
  }

  const resetForm = () => {
    Object.assign(form, options.defaultForm())
  }

  const tableIndexMethod = (index: number) => {
    const paged = queryParams as Partial<CrudQuery>
    const pageNum = paged.pageNum ?? 1
    const pageSize = paged.pageSize ?? 20
    return (pageNum - 1) * pageSize + index + 1
  }

  /** 分页列表重置到首页；树形列表（无 pageNum）不动 */
  const resetPageNum = () => {
    const paged = queryParams as Partial<CrudQuery>
    if ('pageNum' in paged) paged.pageNum = 1
  }

  const handleSearch = () => {
    resetPageNum()
    if (!options.clientSideFiltering) void options.reload()
  }

  const handleReset = () => {
    resetQuery()
    handleSearch()
  }

  const removeFilterChip = (key: string) => {
    if (options.clearChipField) {
      options.clearChipField(key, queryParams)
    } else {
      const defaults = options.defaultQuery() as Record<string, unknown>
      ;(queryParams as Record<string, unknown>)[key] = defaults[key]
    }
    handleSearch()
  }

  const openCreate = (overrides?: Partial<TForm>) => {
    isCreate.value = true
    dialogTitle.value = options.createTitle ?? '新增'
    editingId.value = undefined
    resetForm()
    if (overrides) Object.assign(form, overrides)
    dialogVisible.value = true
  }

  const openEdit = (id: TRow['id'], values: Partial<TForm>) => {
    isCreate.value = false
    dialogTitle.value = options.editTitle ?? '编辑'
    editingId.value = id
    resetForm()
    Object.assign(form, values)
    dialogVisible.value = true
  }

  const handleDialogClose = () => {
    resetForm()
    editingId.value = undefined
  }

  const handleDelete = async (row: TRow) => {
    if (!options.deleteApi) return
    const label = options.rowLabel?.(row) ?? String(row.id)
    const text = options.deleteConfirmText?.(row) ?? `确定要删除「${label}」吗？此操作不可恢复！`
    try {
      await ElMessageBox.confirm(text, '警告', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        draggable: true,
      })
      await options.deleteApi(row.id)
      options.onDeleted?.(row)
      await options.reload()
    } catch (error) {
      if (!isMessageBoxCancel(error)) {
        console.error('删除失败:', error)
      }
    }
  }

  const handleBatchDelete = async (rows: TRow[], labelOf?: (row: TRow) => string) => {
    if (!options.deleteApi || !rows.length) return
    const labeler = labelOf ?? options.rowLabel ?? ((row: TRow) => String(row.id))
    const labels = rows.map(labeler).join('、')
    try {
      await ElMessageBox.confirm(
        `确定要删除以下 ${rows.length} 项吗？此操作不可恢复！\n\n${labels}`,
        '警告',
        {
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
          type: 'warning',
          draggable: true,
        },
      )
      await Promise.all(rows.map((row) => options.deleteApi?.(row.id)))
      await options.reload()
    } catch (error) {
      if (!isMessageBoxCancel(error)) {
        console.error('批量删除失败:', error)
      }
    }
  }

  return {
    loading,
    submitLoading,
    tableRef,
    queryParams,
    form,
    dialogVisible,
    dialogTitle,
    isCreate,
    editingId,
    filterChips,
    activeFilterCount,
    resetQuery,
    resetForm,
    tableIndexMethod,
    handleSearch,
    handleReset,
    removeFilterChip,
    openCreate,
    openEdit,
    handleDialogClose,
    handleDelete,
    handleBatchDelete,
  }
}
