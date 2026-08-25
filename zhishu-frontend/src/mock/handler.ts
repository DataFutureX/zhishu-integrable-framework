import { DEMO_TOKEN } from '@/config/demo'
import { mockPermissions, mockSystemHealth, mockSystemStatus } from './data'
import {
  createDemoOpenApp,
  deleteDemoOpenApp,
  generateDemoAkSk,
  getDemoOpenApp,
  listDemoOpenApps,
  regenerateDemoSk,
  updateDemoOpenApp,
  updateDemoOpenAppScopes,
  updateDemoOpenAppStatus,
} from './openApp'
import { mockState } from './state'
import type { MenuVO } from '@/types/menu'
import type { UnitVO } from '@/types/unit'
import {
  createDemoCaptchaImages,
  deepClone,
  filterByTimeRange,
  nextId,
  nowStr,
  paginate,
} from './utils'

const NOT_HANDLED = Symbol('NOT_HANDLED')

export class MockNotFoundError extends Error {
  constructor(method: string, path: string) {
    super(`演示模式未实现接口: ${method} ${path}`)
    this.name = 'MockNotFoundError'
  }
}

interface MockRequest {
  method: string
  url: string
  params?: Record<string, unknown>
  data?: unknown
}

type Handler = (
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) => unknown | typeof NOT_HANDLED

function normalizePath(url: string): string {
  let path = url.split('?')[0] || '/'
  // axios baseURL 为 /api/v1，adapter 拿到的可能是相对路径或完整路径
  path = path.replace(/^\/api\/v1/, '')
  return path.startsWith('/') ? path : `/${path}`
}

function parseBody(data: unknown): Record<string, unknown> {
  if (!data) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as Record<string, unknown>
    } catch {
      return {}
    }
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    return { file: true }
  }
  return data as Record<string, unknown>
}

function num(value: unknown, fallback = 1): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function str(value: unknown): string {
  return value == null ? '' : String(value).trim()
}

function includesIgnoreCase(source: unknown, keyword: string): boolean {
  if (!keyword) return true
  return String(source ?? '')
    .toLowerCase()
    .includes(keyword.toLowerCase())
}

function okVoid() {
  return undefined
}

function flattenMenus(menus: MenuVO[]): MenuVO[] {
  const result: MenuVO[] = []
  const walk = (items: MenuVO[]) => {
    items.forEach((item) => {
      result.push(item)
      if (item.children?.length) walk(item.children)
    })
  }
  walk(menus)
  return result
}

function flattenUnits(units: UnitVO[]): UnitVO[] {
  const result: UnitVO[] = []
  const walk = (items: UnitVO[]) => {
    items.forEach((item) => {
      const { children, ...rest } = item
      result.push(rest as UnitVO)
      if (children?.length) walk(children)
    })
  }
  walk(units)
  return result
}

function findUnitInTree(units: UnitVO[], id: string): UnitVO | undefined {
  for (const unit of units) {
    if (String(unit.id) === id) return unit
    if (unit.children?.length) {
      const found = findUnitInTree(unit.children, id)
      if (found) return found
    }
  }
  return undefined
}

function removeUnitFromTree(units: UnitVO[], id: string): boolean {
  const idx = units.findIndex((u) => String(u.id) === id)
  if (idx >= 0) {
    units.splice(idx, 1)
    return true
  }
  for (const unit of units) {
    if (unit.children?.length && removeUnitFromTree(unit.children, id)) return true
  }
  return false
}

function findMenuInTree(menus: MenuVO[], id: string): MenuVO | undefined {
  for (const menu of menus) {
    if (String(menu.id) === id) return menu
    if (menu.children?.length) {
      const found = findMenuInTree(menu.children, id)
      if (found) return found
    }
  }
  return undefined
}

function removeMenuFromTree(menus: MenuVO[], id: string): boolean {
  const idx = menus.findIndex((m) => String(m.id) === id)
  if (idx >= 0) {
    menus.splice(idx, 1)
    return true
  }
  for (const menu of menus) {
    if (menu.children?.length && removeMenuFromTree(menu.children, id)) return true
  }
  return false
}

function allMenuIds(): (number | string)[] {
  return flattenMenus(mockState.menus).map((m) => m.id)
}

function getRoleMenuIds(roleId: string | number): (number | string)[] {
  const key = String(roleId)
  if (key === '1') return allMenuIds()
  const stored = mockState.roleMenuIds[key]
  if (stored?.length) return stored
  return []
}

function setRoleMenuIds(roleId: string | number, menuIds: (number | string)[]) {
  mockState.roleMenuIds[String(roleId)] = menuIds
}

function handleAuth(
  method: string,
  path: string,
  _params: Record<string, unknown>,
  _body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/auth/captcha') {
    const slideX = 120 + Math.floor(Math.random() * 100)
    const sliderY = 40 + Math.floor(Math.random() * 40)
    return createDemoCaptchaImages(slideX, sliderY)
  }
  if (method === 'POST' && path === '/auth/captcha/verify') {
    return { captchaToken: `demo-captcha-token-${Date.now()}` }
  }
  if (method === 'GET' && path === '/auth/public-key') {
    return {
      keyId: 'demo',
      // 演示用 RSA 公钥（仅加密；登录校验由 mock 放行）
      publicKey: `-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDW3qr/TqTLfncdQRh4buUtVseU
yGIeTRfWB66FHfO0UPpjWjBqrOio+lhIt8H1FzftVGcG96+cEBJ88GCAIrzq8PMc
82CrO2pa7VW8q7wIBxQm+Gmc0YogQvrVPn4RVPU4eheKss6+Q01sbiqE/eqjKE7u
ZX2DyIQ6ggb67B0cNwIDAQAB
-----END PUBLIC KEY-----`,
      algorithm: 'RSA',
      expireSeconds: 3600,
    }
  }
  if (method === 'POST' && path === '/auth/login') {
    return { token: DEMO_TOKEN, expiration: Date.now() + 86400000 }
  }
  if (method === 'POST' && path === '/auth/sso/exchange') {
    const redirect =
      typeof _body.redirect === 'string' && _body.redirect.startsWith('/') && !_body.redirect.startsWith('//')
        ? _body.redirect
        : '/ai/chat'
    return {
      token: DEMO_TOKEN,
      expiration: Date.now() + 86400000,
      redirect,
    }
  }
  if (method === 'POST' && path === '/auth/logout') {
    return okVoid()
  }
  return NOT_HANDLED
}

function handleUser(
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/users/me') {
    return mockState.users[0]
  }
  if (method === 'PUT' && path === '/users/me') {
    Object.assign(mockState.users[0], body, { updateTime: nowStr() })
    return mockState.users[0]
  }
  if (method === 'PUT' && path === '/users/me/password') {
    return okVoid()
  }
  if (method === 'GET' && path === '/users/page') {
    const username = str(params.username)
    const realName = str(params.realName)
    const phone = str(params.phone)
    const role = str(params.role)
    const roleId = params.roleId
    const status = params.status
    return paginate(mockState.users, num(params.pageNum), num(params.pageSize, 10), (u) => {
      if (username && !includesIgnoreCase(u.username, username)) return false
      if (realName && !includesIgnoreCase(u.realName, realName)) return false
      if (phone && !includesIgnoreCase(u.phone, phone)) return false
      if (role && !includesIgnoreCase(u.role, role) && !includesIgnoreCase(u.roleName, role)) return false
      if (roleId != null && roleId !== '' && String(u.roleId) !== String(roleId)) return false
      if (status !== undefined && status !== '' && status !== null && Number(u.status) !== num(status, -1)) {
        return false
      }
      return true
    })
  }
  const byUsername = path.match(/^\/users\/username\/(.+)$/)
  if (method === 'GET' && byUsername) {
    const username = decodeURIComponent(byUsername[1])
    const user = mockState.users.find((u) => u.username === username)
    if (!user) throw new MockNotFoundError(method, path)
    return user
  }
  const userDetail = path.match(/^\/users\/(\d+)$/)
  if (method === 'GET' && userDetail) {
    const user = mockState.users.find((u) => String(u.id) === userDetail[1])
    if (!user) throw new MockNotFoundError(method, path)
    return { ...user, password: '******' }
  }
  if (method === 'POST' && path === '/users') {
    const roleEntity = mockState.roles.find((r) => String(r.id) === String(body.roleId))
    const item = {
      ...body,
      id: nextId(mockState.idCounter),
      role: roleEntity?.roleCode ?? body.role,
      roleName: roleEntity?.roleName,
      status: body.status ?? 1,
      createTime: nowStr(),
      updateTime: nowStr(),
    }
    mockState.users.push(item as (typeof mockState.users)[number])
    return item
  }
  if (method === 'PUT' && path === '/users') {
    const idx = mockState.users.findIndex((u) => String(u.id) === String(body.id))
    if (idx < 0) throw new MockNotFoundError(method, path)
    if (body.roleId != null) {
      const roleEntity = mockState.roles.find((r) => String(r.id) === String(body.roleId))
      if (roleEntity) {
        body.role = roleEntity.roleCode
        body.roleName = roleEntity.roleName
      }
    }
    mockState.users[idx] = { ...mockState.users[idx], ...body, updateTime: nowStr() }
    return mockState.users[idx]
  }
  const delUser = path.match(/^\/users\/(\d+)$/)
  if (method === 'DELETE' && delUser) {
    mockState.users = mockState.users.filter((u) => String(u.id) !== delUser[1])
    return okVoid()
  }
  const userRole = path.match(/^\/users\/(\d+)\/role$/)
  if (method === 'GET' && userRole) {
    const user = mockState.users.find((u) => String(u.id) === userRole[1])
    if (!user) throw new MockNotFoundError(method, path)
    const roleEntity = mockState.roles.find((r) => String(r.id) === String(user.roleId))
    return {
      userId: user.id,
      roleId: user.roleId ?? roleEntity?.id ?? 1,
      roleCode: user.role ?? roleEntity?.roleCode ?? 'ADMIN',
      roleName: user.roleName ?? roleEntity?.roleName ?? '系统管理员',
    }
  }
  if (method === 'PUT' && userRole) {
    const user = mockState.users.find((u) => String(u.id) === userRole[1])
    if (!user) throw new MockNotFoundError(method, path)
    const roleEntity = mockState.roles.find((r) => String(r.id) === String(body.roleId))
    user.roleId = body.roleId as number
    if (roleEntity) {
      user.role = roleEntity.roleCode
      user.roleName = roleEntity.roleName
    }
    user.updateTime = nowStr()
    return okVoid()
  }
  const userStatus = path.match(/^\/users\/(\d+)\/status$/)
  if (method === 'PUT' && userStatus) {
    const user = mockState.users.find((u) => String(u.id) === userStatus[1])
    if (user) {
      user.status = num(body.status)
      user.updateTime = nowStr()
    }
    return okVoid()
  }
  const resetPwd = path.match(/^\/users\/(\d+)\/password\/reset$/)
  if (method === 'PUT' && resetPwd) {
    return okVoid()
  }
  return NOT_HANDLED
}

function handleMenu(
  method: string,
  path: string,
  _params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/menus/current-user') {
    return deepClone(mockState.menus)
  }
  if (method === 'GET' && path === '/menus/current-user/permissions') {
    return mockPermissions
  }
  if (method === 'GET' && path === '/menus/tree') {
    return deepClone(mockState.menus)
  }
  const roleMenu = path.match(/^\/menus\/role\/(.+)$/)
  if (method === 'GET' && roleMenu) {
    const roleCode = decodeURIComponent(roleMenu[1])
    const roleEntity = mockState.roles.find((r) => r.roleCode === roleCode)
    if (!roleEntity) return deepClone(mockState.menus)
    const allowed = new Set(getRoleMenuIds(roleEntity.id).map(String))
    if (String(roleEntity.id) === '1' || allowed.size === 0) return deepClone(mockState.menus)
    const filterTree = (items: MenuVO[]): MenuVO[] =>
      items
        .filter((item) => allowed.has(String(item.id)))
        .map((item) => ({
          ...item,
          children: item.children ? filterTree(item.children) : undefined,
        }))
    return filterTree(deepClone(mockState.menus))
  }
  const menuDetail = path.match(/^\/menus\/(\d+)$/)
  if (method === 'GET' && menuDetail) {
    return findMenuInTree(mockState.menus, menuDetail[1])
  }
  if (method === 'POST' && path === '/menus') {
    const item = {
      ...(body as Partial<MenuVO>),
      id: nextId(mockState.idCounter),
      parentId: (body.parentId as MenuVO['parentId']) ?? 0,
      title: String(body.title ?? '新菜单'),
      menuType: String(body.menuType ?? 'MENU'),
      createTime: nowStr(),
      updateTime: nowStr(),
      children: [],
    } as MenuVO
    const parentId = String(item.parentId ?? 0)
    if (parentId === '0') {
      mockState.menus.push(item)
    } else {
      const parent = findMenuInTree(mockState.menus, parentId)
      if (parent) {
        parent.children = parent.children || []
        parent.children.push(item)
      } else {
        mockState.menus.push(item)
      }
    }
    return item
  }
  if (method === 'PUT' && path === '/menus') {
    const target = findMenuInTree(mockState.menus, String(body.id))
    if (!target) throw new MockNotFoundError(method, path)
    Object.assign(target, body, { updateTime: nowStr(), children: target.children })
    return target
  }
  const delMenu = path.match(/^\/menus\/(\d+)$/)
  if (method === 'DELETE' && delMenu) {
    removeMenuFromTree(mockState.menus, delMenu[1])
    return okVoid()
  }
  return NOT_HANDLED
}

function handleAnnouncement(
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  const published = () => mockState.announcements.filter((a) => a.status === 1)

  if (method === 'GET' && path === '/announcements/page') {
    const title = str(params.title)
    const priority = params.priority
    const status = params.status
    return paginate(mockState.announcements, num(params.pageNum), num(params.pageSize, 10), (a) => {
      if (title && !includesIgnoreCase(a.title, title)) return false
      if (priority !== undefined && priority !== '' && priority !== null && Number(a.priority) !== num(priority, -1)) {
        return false
      }
      if (status !== undefined && status !== '' && status !== null && Number(a.status) !== num(status, -1)) {
        return false
      }
      return true
    })
  }
  if (method === 'GET' && path === '/announcements/published/page') {
    const title = str(params.title)
    const unreadOnly = params.unreadOnly === true || params.unreadOnly === 'true'
    return paginate(published(), num(params.pageNum), num(params.pageSize, 10), (a) => {
      if (title && !includesIgnoreCase(a.title, title)) return false
      if (unreadOnly && a.read) return false
      return true
    })
  }
  if (method === 'GET' && path === '/announcements/recent') {
    const limit = num(params.limit, 10)
    return published().slice(0, limit)
  }
  if (method === 'GET' && path === '/announcements/unread-count') {
    return published().filter((a) => !a.read).length
  }
  const annDetail = path.match(/^\/announcements\/(\d+)$/)
  if (method === 'GET' && annDetail) {
    return mockState.announcements.find((a) => String(a.id) === annDetail[1])
  }
  if (method === 'POST' && path === '/announcements') {
    const publishImmediately = body.publishImmediately === true
    const item = {
      ...body,
      id: nextId(mockState.idCounter),
      status: publishImmediately ? 1 : 0,
      publishTime: publishImmediately ? nowStr() : undefined,
      publisherId: 1,
      publisherName: '演示管理员',
      read: false,
      createTime: nowStr(),
      updateTime: nowStr(),
    }
    mockState.announcements.unshift(item as (typeof mockState.announcements)[number])
    return item
  }
  if (method === 'PUT' && path === '/announcements') {
    const idx = mockState.announcements.findIndex((a) => String(a.id) === String(body.id))
    if (idx < 0) throw new MockNotFoundError(method, path)
    mockState.announcements[idx] = {
      ...mockState.announcements[idx],
      ...body,
      updateTime: nowStr(),
    }
    return mockState.announcements[idx]
  }
  if (method === 'DELETE' && annDetail) {
    mockState.announcements = mockState.announcements.filter((a) => String(a.id) !== annDetail[1])
    return okVoid()
  }
  const publish = path.match(/^\/announcements\/(\d+)\/publish$/)
  if (method === 'PUT' && publish) {
    const item = mockState.announcements.find((a) => String(a.id) === publish[1])
    if (item) {
      item.status = 1
      item.publishTime = nowStr()
      item.updateTime = nowStr()
    }
    return item
  }
  const revoke = path.match(/^\/announcements\/(\d+)\/revoke$/)
  if (method === 'PUT' && revoke) {
    const item = mockState.announcements.find((a) => String(a.id) === revoke[1])
    if (item) {
      item.status = 2
      item.updateTime = nowStr()
    }
    return item
  }
  const readAnn = path.match(/^\/announcements\/(\d+)\/read$/)
  if (method === 'PUT' && readAnn) {
    const item = mockState.announcements.find((a) => String(a.id) === readAnn[1])
    if (item) item.read = true
    return okVoid()
  }
  if (method === 'PUT' && path === '/announcements/read-all') {
    const before = published().filter((a) => !a.read).length
    mockState.announcements.forEach((a) => {
      if (a.status === 1) a.read = true
    })
    return before
  }
  return NOT_HANDLED
}

function handleSystem(
  method: string,
  path: string,
  _params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/system-config') {
    return { ...mockState.systemConfig }
  }
  if (method === 'PUT' && path === '/system-config') {
    Object.assign(mockState.systemConfig, body, { updateTime: nowStr() })
    return mockState.systemConfig
  }
  if (method === 'POST' && path === '/system-config/icon') {
    const iconUrl = '/logo.svg'
    mockState.systemConfig.systemIcon = iconUrl
    mockState.systemConfig.updateTime = nowStr()
    return iconUrl
  }
  if (method === 'GET' && path === '/system/status') {
    return {
      ...mockSystemStatus,
      timestamp: nowStr(),
      business: { userTotal: mockState.users.length },
    }
  }
  if (method === 'GET' && path === '/system/health') {
    return { ...mockSystemHealth, timestamp: nowStr() }
  }
  return NOT_HANDLED
}

function handleUnit(
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/units/page') {
    const flat = flattenUnits(mockState.units)
    const unitCode = str(params.unitCode)
    const unitName = str(params.unitName)
    const unitType = str(params.unitType)
    const parentId = params.parentId
    const status = params.status
    return paginate(flat, num(params.pageNum), num(params.pageSize, 10), (u) => {
      if (unitCode && !includesIgnoreCase(u.unitCode, unitCode)) return false
      if (unitName && !includesIgnoreCase(u.unitName, unitName)) return false
      if (unitType && !includesIgnoreCase(u.unitType, unitType)) return false
      if (parentId != null && parentId !== '' && String(u.parentId) !== String(parentId)) return false
      if (status !== undefined && status !== '' && status !== null && Number(u.status) !== num(status, -1)) {
        return false
      }
      return true
    })
  }
  if (method === 'GET' && path === '/units/tree') {
    const status = params.status
    if (status === undefined || status === '' || status === null) {
      return deepClone(mockState.units)
    }
    const statusNum = num(status, -1)
    const filterTree = (items: UnitVO[]): UnitVO[] =>
      items
        .filter((item) => Number(item.status) === statusNum)
        .map((item) => ({
          ...item,
          children: item.children ? filterTree(item.children) : undefined,
        }))
    return filterTree(deepClone(mockState.units))
  }
  if (method === 'GET' && path === '/units/list') {
    return flattenUnits(mockState.units).filter((u) => u.status === 1)
  }
  const unitDetail = path.match(/^\/units\/(\d+)$/)
  if (method === 'GET' && unitDetail) {
    return findUnitInTree(mockState.units, unitDetail[1])
  }
  if (method === 'POST' && path === '/units') {
    const parentId = (body.parentId as UnitVO['parentId']) ?? 0
    const parent =
      parentId && String(parentId) !== '0'
        ? findUnitInTree(mockState.units, String(parentId))
        : undefined
    const item: UnitVO = {
      ...(body as Partial<UnitVO>),
      id: nextId(mockState.idCounter),
      parentId,
      parentName: parent?.unitName,
      unitCode: String(body.unitCode ?? `UNIT-${Date.now()}`),
      unitName: String(body.unitName ?? '新单位'),
      status: body.status != null ? Number(body.status) : 1,
      createTime: nowStr(),
      updateTime: nowStr(),
      children: [],
    }
    if (parent) {
      parent.children = parent.children || []
      parent.children.push(item)
    } else {
      mockState.units.push(item)
    }
    return item
  }
  if (method === 'PUT' && path === '/units') {
    const target = findUnitInTree(mockState.units, String(body.id))
    if (!target) throw new MockNotFoundError(method, path)
    Object.assign(target, body, { updateTime: nowStr(), children: target.children })
    return target
  }
  if (method === 'DELETE' && unitDetail) {
    removeUnitFromTree(mockState.units, unitDetail[1])
    return okVoid()
  }
  return NOT_HANDLED
}

function handleRole(
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/roles/page') {
    const roleCode = str(params.roleCode)
    const roleName = str(params.roleName)
    const status = params.status
    return paginate(mockState.roles, num(params.pageNum), num(params.pageSize, 10), (r) => {
      if (roleCode && !includesIgnoreCase(r.roleCode, roleCode)) return false
      if (roleName && !includesIgnoreCase(r.roleName, roleName)) return false
      if (status !== undefined && status !== '' && status !== null && Number(r.status) !== num(status, -1)) {
        return false
      }
      return true
    })
  }
  if (method === 'GET' && path === '/roles/list') {
    return mockState.roles.filter((r) => r.status === 1)
  }
  const roleDetail = path.match(/^\/roles\/(\d+)$/)
  if (method === 'GET' && roleDetail) {
    const roleEntity = mockState.roles.find((r) => String(r.id) === roleDetail[1])
    if (!roleEntity) throw new MockNotFoundError(method, path)
    return { ...roleEntity, menuIds: getRoleMenuIds(roleEntity.id) }
  }
  if (method === 'POST' && path === '/roles') {
    const item = {
      ...body,
      id: nextId(mockState.idCounter),
      status: body.status ?? 1,
      createTime: nowStr(),
      updateTime: nowStr(),
    }
    mockState.roles.push(item as (typeof mockState.roles)[number])
    setRoleMenuIds(item.id as number, [])
    return item
  }
  if (method === 'PUT' && path === '/roles') {
    const idx = mockState.roles.findIndex((r) => String(r.id) === String(body.id))
    if (idx < 0) throw new MockNotFoundError(method, path)
    mockState.roles[idx] = { ...mockState.roles[idx], ...body, updateTime: nowStr() }
    return mockState.roles[idx]
  }
  if (method === 'DELETE' && roleDetail) {
    mockState.roles = mockState.roles.filter((r) => String(r.id) !== roleDetail[1])
    delete mockState.roleMenuIds[roleDetail[1]]
    return okVoid()
  }
  const roleMenus = path.match(/^\/roles\/(\d+)\/menus$/)
  if (method === 'GET' && roleMenus) {
    return getRoleMenuIds(roleMenus[1])
  }
  if (method === 'PUT' && roleMenus) {
    const menuIds = (body.menuIds as (number | string)[]) || []
    setRoleMenuIds(roleMenus[1], menuIds)
    return okVoid()
  }
  return NOT_HANDLED
}

function handleOperationLog(
  method: string,
  path: string,
  params: Record<string, unknown>,
  _body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/operation-logs/page') {
    const username = str(params.username)
    const moduleName = str(params.module)
    const operation = str(params.operation)
    const status = params.status
    let records = [...mockState.operationLogs]
    records = filterByTimeRange(
      records as Record<string, unknown>[],
      str(params.startTime) || undefined,
      str(params.endTime) || undefined,
      'createTime',
    ) as typeof mockState.operationLogs
    return paginate(records, num(params.pageNum), num(params.pageSize, 10), (log) => {
      if (username && !includesIgnoreCase(log.username, username) && !includesIgnoreCase(log.realName, username)) {
        return false
      }
      if (moduleName && !includesIgnoreCase(log.module, moduleName)) return false
      if (operation && !includesIgnoreCase(log.operation, operation)) return false
      if (status !== undefined && status !== '' && status !== null && Number(log.status) !== num(status, -1)) {
        return false
      }
      return true
    })
  }
  const detail = path.match(/^\/operation-logs\/(\d+)$/)
  if (method === 'GET' && detail) {
    return mockState.operationLogs.find((l) => String(l.id) === detail[1])
  }
  return NOT_HANDLED
}

function handleOpenApp(
  method: string,
  path: string,
  _params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  if (method === 'GET' && path === '/open-apps') {
    return listDemoOpenApps()
  }
  if (method === 'POST' && path === '/open-apps') {
    return createDemoOpenApp(body)
  }
  const byId = path.match(/^\/open-apps\/(\d+)$/)
  if (method === 'GET' && byId) {
    const app = getDemoOpenApp(Number(byId[1]))
    if (!app) throw new MockNotFoundError(method, path)
    return app
  }
  if (method === 'PUT' && byId) {
    const app = updateDemoOpenApp(Number(byId[1]), body)
    if (!app) throw new MockNotFoundError(method, path)
    return app
  }
  if (method === 'DELETE' && byId) {
    if (!deleteDemoOpenApp(Number(byId[1]))) throw new MockNotFoundError(method, path)
    return okVoid()
  }
  const generate = path.match(/^\/open-apps\/(\d+)\/generate-aksk$/)
  if (method === 'POST' && generate) {
    const result = generateDemoAkSk(Number(generate[1]))
    if (!result) throw new MockNotFoundError(method, path)
    return result
  }
  const regenerate = path.match(/^\/open-apps\/(\d+)\/regenerate-sk$/)
  if (method === 'POST' && regenerate) {
    const result = regenerateDemoSk(Number(regenerate[1]))
    if (!result) throw new MockNotFoundError(method, path)
    return result
  }
  const scopes = path.match(/^\/open-apps\/(\d+)\/scopes$/)
  if (method === 'PUT' && scopes) {
    const app = updateDemoOpenAppScopes(idFrom(scopes[1]), (body.scopes as string[]) || [])
    if (!app) throw new MockNotFoundError(method, path)
    return app
  }
  const status = path.match(/^\/open-apps\/(\d+)\/status$/)
  if (method === 'PUT' && status) {
    const app = updateDemoOpenAppStatus(Number(status[1]), String(body.status || 'ENABLED'))
    if (!app) throw new MockNotFoundError(method, path)
    return app
  }
  return NOT_HANDLED
}

function idFrom(value: string) {
  return Number(value)
}

function dispatch(
  method: string,
  path: string,
  params: Record<string, unknown>,
  body: Record<string, unknown>,
) {
  const handlers: Handler[] = [
    handleAuth,
    handleUser,
    handleMenu,
    handleAnnouncement,
    handleSystem,
    handleUnit,
    handleRole,
    handleOperationLog,
    handleOpenApp,
  ]
  for (const handler of handlers) {
    const result = handler(method, path, params, body)
    if (result !== NOT_HANDLED) return result
  }
  throw new MockNotFoundError(method, path)
}

export function handleMockRequest(req: MockRequest): unknown {
  const method = (req.method || 'GET').toUpperCase()
  const path = normalizePath(req.url)
  const params = (req.params || {}) as Record<string, unknown>
  const body = parseBody(req.data)
  return dispatch(method, path, params, body)
}
