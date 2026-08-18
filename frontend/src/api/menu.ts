import { get, post, put, del } from '@/utils/request'
import type { MenuVO, MenuCreateDTO, MenuUpdateDTO } from '@/types/menu'

/** 查询完整菜单树 GET /api/v1/menus/tree */
export const getMenuTreeApi = () => {
  return get<MenuVO[]>('/menus/tree')
}

/** 按角色查询菜单树 GET /api/v1/menus/role/{roleCode} */
export const getMenuTreeByRoleApi = (roleCode: string) => {
  return get<MenuVO[]>(`/menus/role/${roleCode}`)
}

/** 查询当前用户菜单树 GET /api/v1/menus/current-user */
export const getCurrentUserMenusApi = () => {
  return get<MenuVO[]>('/menus/current-user')
}

/** 查询当前用户权限值 GET /api/v1/menus/current-user/permissions */
export const getCurrentUserPermissionsApi = () => {
  return get<string[]>('/menus/current-user/permissions')
}

/** 查询菜单详情 GET /api/v1/menus/{id} */
export const getMenuDetailApi = (id: number | string) => {
  return get<MenuVO>(`/menus/${id}`)
}

/** 创建菜单 POST /api/v1/menus */
export const createMenuApi = (data: MenuCreateDTO) => {
  return post<MenuVO>('/menus', data)
}

/** 更新菜单 PUT /api/v1/menus */
export const updateMenuApi = (data: MenuUpdateDTO) => {
  return put<MenuVO>('/menus', data)
}

/** 删除菜单 DELETE /api/v1/menus/{id} */
export const deleteMenuApi = (id: number | string) => {
  return del(`/menus/${id}`)
}
