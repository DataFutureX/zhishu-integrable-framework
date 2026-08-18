import { describe, expect, it } from 'vitest'
import type { MenuVO } from '@/types/menu'
import {
  filterVisibleMenus,
  findFirstMenuPath,
  menusToRoutes,
  resolveViewComponent,
} from '@/router/dynamicRoutes'

function menu(partial: Partial<MenuVO> & Pick<MenuVO, 'id' | 'title' | 'menuType'>): MenuVO {
  return {
    parentId: 0,
    path: partial.path,
    routeName: partial.routeName,
    component: partial.component,
    visible: 1,
    status: 1,
    sort: 0,
    requiresAuth: 1,
    ...partial,
  }
}

describe('resolveViewComponent', () => {
  it('resolves known view path', () => {
    expect(resolveViewComponent('views/dashboard/Dashboard.vue')).toBeTypeOf('function')
    expect(resolveViewComponent('views/error/Forbidden.vue')).toBeTypeOf('function')
  })

  it('returns undefined for missing component', () => {
    expect(resolveViewComponent('views/not-exists/Foo.vue')).toBeUndefined()
    expect(resolveViewComponent(undefined)).toBeUndefined()
  })
})

describe('filterVisibleMenus / reserved paths', () => {
  it('hides BUTTON / PAGE / invisible / reserved paths', () => {
    const menus: MenuVO[] = [
      menu({
        id: 1,
        title: '系统',
        menuType: 'DIRECTORY',
        path: '/permission',
        children: [
          menu({
            id: 2,
            title: '用户',
            menuType: 'MENU',
            path: '/permission/user',
            component: 'views/user/UserList.vue',
            children: [
              menu({ id: 3, title: '查询', menuType: 'BUTTON', routeName: 'system:user:query' }),
            ],
          }),
          menu({ id: 4, title: '隐藏页', menuType: 'PAGE', path: '/permission/hidden' }),
          menu({ id: 5, title: '不可见', menuType: 'MENU', path: '/permission/x', visible: 0 }),
        ],
      }),
      menu({ id: 6, title: '登录冲突', menuType: 'MENU', path: '/login', component: 'views/login/Login.vue' }),
      menu({ id: 7, title: '403冲突', menuType: 'MENU', path: '/403', component: 'views/error/Forbidden.vue' }),
    ]

    const visible = filterVisibleMenus(menus)
    expect(visible).toHaveLength(1)
    expect(visible[0].children).toHaveLength(1)
    expect(visible[0].children?.[0].title).toBe('用户')
    expect(visible[0].children?.[0].children?.length ?? 0).toBe(0)
  })
})

describe('menusToRoutes', () => {
  it('skips reserved paths and reserved route names', () => {
    const routes = menusToRoutes([
      menu({
        id: 1,
        title: '登录',
        menuType: 'MENU',
        path: '/login',
        routeName: 'Login',
        component: 'views/login/Login.vue',
      }),
      menu({
        id: 2,
        title: '用户',
        menuType: 'MENU',
        path: '/permission/user',
        routeName: 'PermissionUser',
        component: 'views/user/UserList.vue',
        children: [
          menu({ id: 21, title: '查询', menuType: 'BUTTON', routeName: 'system:user:query' }),
          menu({ id: 22, title: '新增', menuType: 'BUTTON', routeName: 'system:user:add' }),
        ],
      }),
    ])

    expect(routes).toHaveLength(1)
    expect(routes[0].path).toBe('permission/user')
    expect(routes[0].meta?.permissions).toEqual(['system:user:query', 'system:user:add'])
  })

  it('findFirstMenuPath skips reserved and prefers redirect', () => {
    expect(
      findFirstMenuPath([
        menu({ id: 1, title: '登录', menuType: 'MENU', path: '/login', component: 'views/login/Login.vue' }),
        menu({
          id: 2,
          title: '系统',
          menuType: 'DIRECTORY',
          path: '/permission',
          redirect: '/permission/user',
        }),
      ]),
    ).toBe('/permission/user')
  })
})
