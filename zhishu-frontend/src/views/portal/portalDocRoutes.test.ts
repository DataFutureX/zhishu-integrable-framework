import { describe, expect, it } from 'vitest'
import { parsePortalDocRef, portalDocPath } from '@/utils/portalDocRoutes'

describe('portalDocRoutes', () => {
  it('parses docs path and legacy hash', () => {
    expect(parsePortalDocRef('/docs')).toBe('quickstart')
    expect(parsePortalDocRef('/docs/sso')).toBe('sso')
    expect(parsePortalDocRef('/docs/wanxiang')).toBe('wanxiang')
    expect(parsePortalDocRef('/docs/sso-sdk')).toBe('sso-sdk')
    expect(parsePortalDocRef('/docs/openapi-sdk')).toBe('openapi-sdk')
    expect(parsePortalDocRef('#docs')).toBe('quickstart')
    expect(parsePortalDocRef('#docs/sso')).toBe('sso')
    expect(parsePortalDocRef('#quickstart')).toBe('quickstart')
    expect(parsePortalDocRef('#features')).toBeNull()
  })

  it('parses relative markdown doc links', () => {
    expect(parsePortalDocRef('./单点登录对接说明.md')).toBe('sso')
    expect(parsePortalDocRef('../docs/万象接入联调实现步骤.md')).toBe('wanxiang')
    expect(parsePortalDocRef('./他方SSO接入SDK使用说明.md')).toBe('sso-sdk')
    expect(parsePortalDocRef('./知枢OpenAPI接入SDK使用说明.md')).toBe('openapi-sdk')
    expect(parsePortalDocRef('./README.md')).toBe('quickstart')
  })

  it('builds docs path', () => {
    expect(portalDocPath('sso')).toBe('/docs/sso')
  })
})
