import { get, post, put, del } from '@/utils/request'
import type {
  RoleVO,
  RoleQueryDTO,
  RoleCreateDTO,
  RoleUpdateDTO,
  RoleMenuAssignDTO,
  PageResult,
} from '@/types/role'

/** 分页查询角色 GET /api/v1/roles/page */
export const getRolePageApi = (params: RoleQueryDTO) => {
  return get<PageResult<RoleVO>>('/roles/page', { params })
}

/** 查询全部启用角色 GET /api/v1/roles/list */
export const getRoleListApi = () => {
  return get<RoleVO[]>('/roles/list')
}

/** 查询角色详情 GET /api/v1/roles/{id} */
export const getRoleDetailApi = (id: number | string) => {
  return get<RoleVO>(`/roles/${id}`)
}

/** 创建角色 POST /api/v1/roles */
export const createRoleApi = (data: RoleCreateDTO) => {
  return post<RoleVO>('/roles', data)
}

/** 更新角色 PUT /api/v1/roles */
export const updateRoleApi = (data: RoleUpdateDTO) => {
  return put<RoleVO>('/roles', data)
}

/** 删除角色 DELETE /api/v1/roles/{id} */
export const deleteRoleApi = (id: number | string) => {
  return del(`/roles/${id}`)
}

/** 查询角色已授权菜单ID GET /api/v1/roles/{id}/menus */
export const getRoleMenuIdsApi = (id: number | string) => {
  return get<(number | string)[]>(`/roles/${id}/menus`)
}

/** 为角色分配菜单 PUT /api/v1/roles/{id}/menus */
export const assignRoleMenusApi = (id: number | string, data: RoleMenuAssignDTO) => {
  return put(`/roles/${id}/menus`, data)
}
