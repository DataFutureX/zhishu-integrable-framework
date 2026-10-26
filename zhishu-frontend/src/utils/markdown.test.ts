import { beforeEach, describe, expect, it } from 'vitest'
import { clearMarkdownCache, renderMarkdown, renderPlainMarkdown } from '@/utils/markdown'

/**
 * 模型输出 / RAG 片段属于不可信内容，这里守住 v-html 前的消毒底线：
 * 标签白名单、事件属性、javascript: 链接、外链 rel。
 */
describe('renderMarkdown', () => {
  beforeEach(() => {
    clearMarkdownCache()
  })

  it('移除 script 标签与内容', () => {
    const html = renderMarkdown('hello <script>window.__xss = 1</script> world')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('__xss')
  })

  it('移除内联事件属性但保留标签', () => {
    const html = renderMarkdown('<img src="x" onerror="window.__xss=1">')
    expect(html).toContain('<img')
    expect(html).not.toContain('onerror')
  })

  it('移除 style 标签与 style 属性', () => {
    const html = renderMarkdown(
      '<span style="position:fixed">fixed</span><style>body{display:none}</style>',
    )
    expect(html).not.toContain('<style')
    expect(html).not.toContain('position:fixed')
  })

  it('外链补 target=_blank 与 rel=noopener', () => {
    const html = renderMarkdown('<a href="https://example.com">link</a>')
    expect(html).toContain('target="_blank"')
    expect(html).toContain('rel="noopener noreferrer"')
  })

  it('保留正常 Markdown 结构', () => {
    const html = renderMarkdown('**加粗** 与 `code`\n\n| a | b |\n| - | - |\n| 1 | 2 |')
    expect(html).toContain('<strong>加粗</strong>')
    expect(html).toContain('<code>code</code>')
    expect(html).toContain('<table>')
  })

  it('相同内容命中缓存，返回一致结果', () => {
    const first = renderMarkdown('重复内容 **cache**')
    const second = renderMarkdown('重复内容 **cache**')
    expect(second).toBe(first)
  })

  it('空内容返回空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderPlainMarkdown('')).toBe('')
  })
})

describe('renderPlainMarkdown', () => {
  it('不解析 HTML 标签，按纯文本转义', () => {
    const html = renderPlainMarkdown('<b>不应加粗</b>')
    expect(html).not.toContain('<b>')
    expect(html).toContain('&lt;b&gt;')
  })
})
