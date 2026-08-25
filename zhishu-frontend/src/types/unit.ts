/** 单位视图对象 */
export interface UnitVO {
  id: number | string
  parentId?: number | string
  parentName?: string
  unitCode: string
  unitName: string
  unitType?: string
  region?: string
  address?: string
  contactPerson?: string
  contactPhone?: string
  sort?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
  children?: UnitVO[]
}

/** 分页查询参数 */
export interface UnitQueryDTO {
  unitCode?: string
  unitName?: string
  unitType?: string
  status?: number
  parentId?: number | string
  pageNum?: number
  pageSize?: number
}

/** 创建单位 */
export interface UnitCreateDTO {
  parentId?: number | string
  unitCode?: string
  unitName: string
  unitType?: string
  region?: string
  address?: string
  contactPerson?: string
  contactPhone?: string
  sort?: number
  status?: number
  remark?: string
}

/** 更新单位 */
export interface UnitUpdateDTO {
  id: number | string
  parentId?: number | string
  unitCode?: string
  unitName?: string
  unitType?: string
  region?: string
  address?: string
  contactPerson?: string
  contactPhone?: string
  sort?: number
  status?: number
  remark?: string
}

export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}

/** 单位状态 */
export enum UnitStatus {
  DISABLED = 0,
  ENABLED = 1,
}
