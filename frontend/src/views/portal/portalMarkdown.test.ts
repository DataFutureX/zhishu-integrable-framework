import { describe, expect, it } from 'vitest'
import {
  parsePortalDocRef,
  portalDocPath,
  prepareQuickStartMarkdown,
  renderPortalMarkdown,
} from './portalMarkdown'

describe('portalMarkdown', () => {
  it('parses docs path and legacy hash', () => {
    expect(parsePortalDocRef('/docs')).toBe('quickstart')
    expect(parsePortalDocRef('/docs/sso')).toBe('sso')
    expect(parsePortalDocRef('/docs/wanxiang')).toBe('wanxiang')
    expect(parsePortalDocRef('/docs/sso-sdk')).toBe('sso-sdk')
    expect(parsePortalDocRef('#docs')).toBe('quickstart')
    expect(parsePortalDocRef('#docs/sso')).toBe('sso')
    expect(parsePortalDocRef('#quickstart')).toBe('quickstart')
    expect(parsePortalDocRef('#features')).toBeNull()
  })

  it('parses relative markdown doc links', () => {
    expect(parsePortalDocRef('./单点登录对接说明.md')).toBe('sso')
    expect(parsePortalDocRef('../docs/万象接入联调实现步骤.md')).toBe('wanxiang')
    expect(parsePortalDocRef('./他方SSO接入SDK使用说明.md')).toBe('sso-sdk')
    expect(parsePortalDocRef('./README.md')).toBe('quickstart')
  })

  it('builds docs path', () => {
    expect(portalDocPath('sso')).toBe('/docs/sso')
  })

  it('renders GFM tables and rewrites SSO markdown links', () => {
    const html = renderPortalMarkdown(`
| 算法 | 说明 |
|------|------|
| RS256 | RSA |
| SM2 | 国密 |

详见 [单点登录对接说明.md](./单点登录对接说明.md)、[万象联调](./万象接入联调实现步骤.md) 与 [SDK](../sdk/yunqi-sso-partner-sdk)。
`)
    expect(html).toContain('<table>')
    expect(html).toContain('RS256')
    expect(html).toContain('href="/docs/sso"')
    expect(html).toContain('href="/docs/wanxiang"')
    expect(html).toContain('sdk/yunqi-sso-partner-sdk')
  })

  it('strips screenshot chapter from quick start markdown', () => {
    const source = `# 标题

## 目录

- [界面一览](#界面一览)
- [两种体验路径](#两种体验路径)

## 界面一览

![x](./a.png)

## 两种体验路径

正文
`
    const prepared = prepareQuickStartMarkdown(source)
    expect(prepared).not.toContain('## 目录')
    expect(prepared).not.toContain('界面一览')
    expect(prepared).toContain('两种体验路径')
  })
})
