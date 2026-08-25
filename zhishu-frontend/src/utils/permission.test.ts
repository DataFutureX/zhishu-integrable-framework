import { describe, expect, it } from 'vitest'
import {
  collectRoutePermissions,
  matchPermissions,
  normalizePermissionCodes,
} from '@/utils/permission'
import { resolveViewComponent } from '@/router/dynamicRouteViews'

describe('normalizePermissionCodes', () => {
  it('normalizes string and array inputs', () => {
    expect(normalizePermissionCodes('a')).toEqual(['a'])
    expect(normalizePermissionCodes(['a', '', 'b'])).toEqual(['a', 'b'])
    expect(normalizePermissionCodes(null)).toEqual([])
  })
})

describe('matchPermissions', () => {
  it('allows empty required codes', () => {
    expect(matchPermissions([], [])).toBe(true)
    expect(matchPermissions(['x'], undefined)).toBe(true)
  })

  it('matches any by default', () => {
    expect(matchPermissions(['a', 'b'], ['b', 'c'])).toBe(true)
    expect(matchPermissions(['a'], ['b', 'c'])).toBe(false)
  })

  it('supports requireAll and admin bypass', () => {
    expect(matchPermissions(['a'], ['a', 'b'], { requireAll: true })).toBe(false)
    expect(matchPermissions(['a', 'b'], ['a', 'b'], { requireAll: true })).toBe(true)
    expect(matchPermissions([], ['secret'], { isAdmin: true })).toBe(true)
  })
})

describe('collectRoutePermissions', () => {
  it('collects unique permission codes from matched records', () => {
    const codes = collectRoutePermissions([
      { meta: { permissions: 'system:user:query' } },
      { meta: { permissions: ['system:user:query', 'system:user:edit'] } },
      { meta: {} },
    ])
    expect(codes).toEqual(['system:user:query', 'system:user:edit'])
  })
})

describe('resolveViewComponent', () => {
  it('resolves known view paths', () => {
    expect(resolveViewComponent('views/user/UserList.vue')).toBeTypeOf('function')
    expect(resolveViewComponent('views/missing/Nope.vue')).toBeUndefined()
  })
})
