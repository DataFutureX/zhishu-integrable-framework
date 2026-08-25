import {
  mockAnnouncements,
  mockMenuTree,
  mockOperationLogs,
  mockRoleMenuIds,
  mockRoles,
  mockSystemConfig,
  mockUnits,
  mockUsers,
} from './data'
import { resetMcpDemoState } from './mcp'
import { resetOpenAppDemoState } from './openApp'
import { deepClone } from './utils'

const idCounter = { value: 100000 }

export const mockState = {
  idCounter,
  systemConfig: deepClone(mockSystemConfig),
  users: deepClone(mockUsers),
  roles: deepClone(mockRoles),
  roleMenuIds: deepClone(mockRoleMenuIds) as Record<string, (number | string)[]>,
  menus: deepClone(mockMenuTree),
  units: deepClone(mockUnits),
  announcements: deepClone(mockAnnouncements),
  operationLogs: deepClone(mockOperationLogs),
}

export function resetMockState() {
  resetMcpDemoState()
  resetOpenAppDemoState()
  Object.assign(mockState, {
    idCounter: { value: 100000 },
    systemConfig: deepClone(mockSystemConfig),
    users: deepClone(mockUsers),
    roles: deepClone(mockRoles),
    roleMenuIds: deepClone(mockRoleMenuIds),
    menus: deepClone(mockMenuTree),
    units: deepClone(mockUnits),
    announcements: deepClone(mockAnnouncements),
    operationLogs: deepClone(mockOperationLogs),
  })
}
