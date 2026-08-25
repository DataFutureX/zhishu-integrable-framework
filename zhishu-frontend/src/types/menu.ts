export type MenuType = 'DIRECTORY' | 'MENU' | 'PAGE' | 'BUTTON'

export interface MenuVO {
  id: number | string
  parentId: number | string
  title: string
  path?: string
  routeName?: string
  redirect?: string
  icon?: string
  menuType: MenuType | string
  visible?: number
  requiresAuth?: number
  sort?: number
  component?: string
  meta?: string
  status?: number
  createTime?: string
  updateTime?: string
  children?: MenuVO[]
}

export interface MenuCreateDTO {
  id?: number | string
  parentId: number | string
  title: string
  path?: string
  routeName?: string
  redirect?: string
  icon?: string
  menuType: MenuType | string
  visible?: number
  requiresAuth?: number
  sort?: number
  component?: string
  meta?: string
  status?: number
}

export interface MenuUpdateDTO {
  id: number | string
  parentId?: number | string
  title: string
  path?: string
  routeName?: string
  redirect?: string
  icon?: string
  menuType?: MenuType | string
  visible?: number
  requiresAuth?: number
  sort?: number
  component?: string
  meta?: string
  status?: number
}
