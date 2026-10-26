import { describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import { useCrudList } from '@/composables/useCrudList'

interface Row {
  id: string | number
  username: string
  status?: number
}

interface Query {
  pageNum: number
  pageSize: number
  username?: string
  status?: number
}

interface Form {
  id?: string | number
  username: string
  status: number
}

const setup = (overrides: Partial<Parameters<typeof useCrudList<Row, Form, Query>>[0]> = {}) => {
  const reload = vi.fn()
  const deleteApi = vi.fn().mockResolvedValue(undefined)
  const list = useCrudList<Row, Form, Query>({
    defaultQuery: () => ({ pageNum: 1, pageSize: 20, username: '', status: undefined }),
    defaultForm: () => ({ username: '', status: 1 }),
    createTitle: '新增',
    editTitle: '编辑',
    reload,
    deleteApi,
    rowLabel: (row) => row.username,
    chipBuilder: (query) => {
      const chips = []
      if (query.username?.trim())
        chips.push({ key: 'username', label: `用户名：${query.username}` })
      if (query.status !== undefined) chips.push({ key: 'status', label: `状态：${query.status}` })
      return chips
    },
    ...overrides,
  })
  return { list, reload, deleteApi }
}

describe('useCrudList', () => {
  it('分页序号按页码与每页条数换算', () => {
    const { list } = setup()
    expect(list.tableIndexMethod(0)).toBe(1)
    list.queryParams.pageNum = 3
    list.queryParams.pageSize = 10
    expect(list.tableIndexMethod(0)).toBe(21)
  })

  it('查询时把页码重置为 1 并触发刷新', () => {
    const { list, reload } = setup()
    list.queryParams.pageNum = 5
    list.handleSearch()
    expect(list.queryParams.pageNum).toBe(1)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('重置查询条件回到默认值', () => {
    const { list } = setup()
    list.queryParams.username = 'admin'
    list.queryParams.status = 0
    list.handleReset()
    expect(list.queryParams.username).toBe('')
    expect(list.queryParams.status).toBeUndefined()
  })

  it('筛选 chip 随查询条件变化，计数同步', () => {
    const { list } = setup()
    expect(list.activeFilterCount.value).toBe(0)
    list.queryParams.username = 'admin'
    list.queryParams.status = 1
    expect(list.filterChips.value).toHaveLength(2)
    expect(list.activeFilterCount.value).toBe(2)
  })

  it('移除 chip 时按默认值清空对应字段', () => {
    const { list } = setup()
    list.queryParams.username = 'admin'
    list.removeFilterChip('username')
    expect(list.queryParams.username).toBe('')
  })

  it('打开新增弹窗会重置表单并写入覆盖值', () => {
    const { list } = setup()
    list.queryParams.username = 'dirty'
    list.openCreate({ username: '预设' })
    expect(list.dialogVisible.value).toBe(true)
    expect(list.isCreate.value).toBe(true)
    expect(list.dialogTitle.value).toBe('新增')
    expect(list.form.username).toBe('预设')
  })

  it('打开编辑弹窗写入表单并标记为编辑态', () => {
    const { list } = setup()
    list.openEdit(9, { id: 9, username: 'editor', status: 0 })
    expect(list.isCreate.value).toBe(false)
    expect(list.dialogTitle.value).toBe('编辑')
    expect(list.editingId.value).toBe(9)
    expect(list.form.username).toBe('editor')
  })

  it('关闭弹窗后清理表单与编辑态', () => {
    const { list } = setup()
    list.openEdit(3, { id: 3, username: 'x', status: 1 })
    list.handleDialogClose()
    expect(list.form.username).toBe('')
    expect(list.editingId.value).toBeUndefined()
  })

  it('确认删除后调用接口并刷新列表', async () => {
    const { list, deleteApi, reload } = setup()
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)

    await list.handleDelete({ id: 7, username: 'to-delete' })

    expect(deleteApi).toHaveBeenCalledWith(7)
    expect(reload).toHaveBeenCalledTimes(1)
    confirmSpy.mockRestore()
  })

  it('用户取消删除时不调用接口也不报错', async () => {
    const { list, deleteApi } = setup()
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel' as never)

    await expect(list.handleDelete({ id: 1, username: 'a' })).resolves.toBeUndefined()
    expect(deleteApi).not.toHaveBeenCalled()

    confirmSpy.mockRestore()
  })
})
