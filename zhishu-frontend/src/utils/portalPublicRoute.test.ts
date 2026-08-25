import { describe, expect, it } from 'vitest'
import { isPortalPublicPath } from './portalPublicRoute'

describe('portalPublicRoute', () => {
  it('detects portal public paths', () => {
    expect(isPortalPublicPath('/portal')).toBe(true)
    expect(isPortalPublicPath('/docs')).toBe(true)
    expect(isPortalPublicPath('/docs/sso')).toBe(true)
    expect(isPortalPublicPath('/login')).toBe(false)
    expect(isPortalPublicPath('/')).toBe(false)
  })
})
