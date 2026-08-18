import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    /** 访问该路由所需权限码（任一即可；管理员放行） */
    permissions?: string | string[]
    menuId?: number | string
    layout?: import('./route').PageLayoutType
    fullBleed?: boolean
    hideTabBar?: boolean
    hideBreadcrumb?: boolean
    hideSecondaryAside?: boolean
    mode?: string
  }
}
