export interface RoleVO {
  id: number | string
  roleCode: string
  roleName: string
  description?: string
  status?: number
  sort?: number
  menuIds?: (number | string)[]
  createTime?: string
  updateTime?: string
}

export interface RoleQueryDTO {
  roleCode?: string
  roleName?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface RoleCreateDTO {
  roleCode: string
  roleName: string
  description?: string
  status?: number
  sort?: number
}

export interface RoleUpdateDTO {
  id: number | string
  roleName: string
  description?: string
  status?: number
  sort?: number
}

export interface RoleMenuAssignDTO {
  menuIds: (number | string)[]
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}
