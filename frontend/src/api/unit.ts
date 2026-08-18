import { get, post, put, del } from '@/utils/request'
import type {
  UnitVO,
  UnitQueryDTO,
  UnitCreateDTO,
  UnitUpdateDTO,
  PageResult,
} from '@/types/unit'

/** 分页查询单位 GET /api/v1/units/page */
export const getUnitPageApi = (params: UnitQueryDTO) => {
  return get<PageResult<UnitVO>>('/units/page', { params })
}

/** 查询单位树 GET /api/v1/units/tree */
export const getUnitTreeApi = (status?: number) => {
  return get<UnitVO[]>('/units/tree', { params: status !== undefined ? { status } : undefined })
}

/** 查询全部启用单位 GET /api/v1/units/list */
export const getUnitListApi = () => {
  return get<UnitVO[]>('/units/list')
}

/** 查询单位详情 GET /api/v1/units/{id} */
export const getUnitDetailApi = (id: number | string) => {
  return get<UnitVO>(`/units/${id}`)
}

/** 创建单位 POST /api/v1/units */
export const createUnitApi = (data: UnitCreateDTO) => {
  return post<UnitVO>('/units', data)
}

/** 更新单位 PUT /api/v1/units */
export const updateUnitApi = (data: UnitUpdateDTO) => {
  return put<UnitVO>('/units', data)
}

/** 删除单位 DELETE /api/v1/units/{id} */
export const deleteUnitApi = (id: number | string) => {
  return del(`/units/${id}`)
}
