import type { MenuVO } from '@/types/menu'

export const normalizeMenuPath = (path?: string) => {
  if (!path) return '/'
  return path.startsWith('/') ? path : `/${path}`
}

/** 是否为个人中心菜单 */
export function isProfileMenu(menu: MenuVO): boolean {
  if (menu.routeName === 'Profile') return true
  return normalizeMenuPath(menu.path) === '/profile'
}

/** 当前路由是否属于个人中心 */
export function isProfileRoute(path: string): boolean {
  return path === '/profile' || path.startsWith('/profile/')
}

/** 从导航菜单中排除个人中心（可按布局模式决定是否展示） */
export function filterNavigationMenus(
  menus: MenuVO[],
  options?: { includeProfile?: boolean },
): MenuVO[] {
  return menus.filter((menu) => {
    if (!options?.includeProfile && isProfileMenu(menu)) return false
    return true
  })
}
