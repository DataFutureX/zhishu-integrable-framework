import { describe, expect, it } from 'vitest'
import {
  prepareQuickStartMarkdown,
  renderPortalMarkdown,
  renderPortalMarkdownWithToc,
} from './portalMarkdown'

describe('portalMarkdown', () => {
  it('renders GFM tables and rewrites SSO markdown links', () => {
    const html = renderPortalMarkdown(`
| 算法 | 说明 |
|------|------|
| RS256 | RSA |
| SM2 | 国密 |

详见 [单点登录对接说明.md](./单点登录对接说明.md)、[万象联调](./万象接入联调实现步骤.md)、[SDK](../sdk/yunqi-sso-partner-sdk) 与 [OpenAPI](./知枢OpenAPI接入SDK使用说明.md)。
`)
    expect(html).toContain('<table>')
    expect(html).toContain('RS256')
    expect(html).toContain('href="/docs/sso"')
    expect(html).toContain('href="/docs/wanxiang"')
    expect(html).toContain('sdk/yunqi-sso-partner-sdk')
    expect(html).toContain('href="/docs/openapi-sdk"')
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

  it('strips leading H1 from CRLF quick start markdown', () => {
    const source = '![logo](./logo.svg)\r\n\r\n# 知枢可集成框架 · 快速开始\r\n\r\n正文\r\n'
    const prepared = prepareQuickStartMarkdown(source)
    expect(prepared).not.toContain('![logo]')
    expect(prepared).not.toContain('# 知枢可集成框架')
    expect(prepared).toContain('正文')
  })

  it('extracts heading toc with stable ids', () => {
    const { html, toc } = renderPortalMarkdownWithToc(`
## 两种体验路径

正文

### 演示模式

说明

## 常见问题
`)
    expect(html).toContain('id="两种体验路径"')
    expect(html).toContain('id="演示模式"')
    expect(toc).toEqual([
      { id: '两种体验路径', text: '两种体验路径', level: 2 },
      { id: '演示模式', text: '演示模式', level: 3 },
      { id: '常见问题', text: '常见问题', level: 2 },
    ])
  })
})
