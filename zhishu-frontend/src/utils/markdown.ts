import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

/**
 * 统一的 Markdown 渲染入口（对话 / 知识问答 / 演示面板共用）。
 *
 * 安全约束：正文可能来自模型输出、RAG 文档片段与 MCP 工具返回，属于不可信内容。
 * 即使开启 `html` 也必须在 `v-html` 前经 DOMPurify 白名单过滤，
 * 否则 `<img onerror=...>` 可窃取 localStorage 中的 token。
 */

/** 允许保留的排版类标签：不包含 script / iframe / style / form / input / object */
const ALLOWED_TAGS = [
  'a',
  'b',
  'blockquote',
  'br',
  'caption',
  'code',
  'col',
  'colgroup',
  'dd',
  'del',
  'details',
  'div',
  'dl',
  'dt',
  'em',
  'figcaption',
  'figure',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'i',
  'img',
  'ins',
  'kbd',
  'li',
  'mark',
  'ol',
  'p',
  'pre',
  's',
  'samp',
  'small',
  'span',
  'strong',
  'sub',
  'summary',
  'sup',
  'table',
  'tbody',
  'td',
  'tfoot',
  'th',
  'thead',
  'tr',
  'u',
  'ul',
  'var',
]

const ALLOWED_ATTR = [
  'align',
  'alt',
  'aria-hidden',
  'aria-label',
  'class',
  'colspan',
  'height',
  'href',
  'rel',
  'rowspan',
  'src',
  'start',
  'target',
  'title',
  'width',
]

/** 外链统一加 rel，避免 window.opener 泄漏与反向标签劫持 */
const EXTERNAL_LINK_PATTERN = /^https?:\/\//i

let hookRegistered = false

function registerSanitizeHook() {
  if (hookRegistered) return
  hookRegistered = true

  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.nodeName !== 'A') return
    const element = node as Element
    const href = element.getAttribute('href') || ''
    if (!EXTERNAL_LINK_PATTERN.test(href)) return
    element.setAttribute('target', '_blank')
    element.setAttribute('rel', 'noopener noreferrer')
  })
}

function sanitize(html: string): string {
  registerSanitizeHook()
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 模型常直接贴 base64 图片，保留 img 的 data URI
    ADD_DATA_URI_TAGS: ['img'],
    FORBID_TAGS: [
      'style',
      'script',
      'iframe',
      'object',
      'embed',
      'form',
      'input',
      'button',
      'link',
      'meta',
    ],
    FORBID_ATTR: ['style', 'srcset', 'onerror', 'onload', 'onclick'],
    KEEP_CONTENT: true,
  })
}

/** 渲染结果按内容缓存：流式逐字刷新时避免整段重复解析 */
const CACHE_LIMIT = 300
const renderCache = new Map<string, string>()

function memoizedRender(prefix: string, content: string, render: () => string): string {
  const key = `${prefix}\u0000${content}`
  const cached = renderCache.get(key)
  if (cached !== undefined) return cached

  const html = render()
  if (renderCache.size >= CACHE_LIMIT) {
    const oldest = renderCache.keys().next().value
    if (oldest !== undefined) renderCache.delete(oldest)
  }
  renderCache.set(key, html)
  return html
}

/** 允许基础 HTML（再经 DOMPurify 白名单过滤），用于 Agent 会话等富文本场景 */
const richRenderer = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true,
})

/** 完全禁用 HTML 标签，用于无需富文本的场景 */
const plainRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

/** 渲染富文本 Markdown：返回值已消毒，可安全用于 v-html */
export function renderMarkdown(content: string): string {
  if (!content) return ''
  return memoizedRender('rich', content, () => sanitize(richRenderer.render(content)))
}

/** 渲染纯 Markdown（不解析 HTML 标签，无需再消毒） */
export function renderPlainMarkdown(content: string): string {
  if (!content) return ''
  return memoizedRender('plain', content, () => plainRenderer.render(content))
}

/** 单测 / 排障用：清空渲染缓存 */
export function clearMarkdownCache() {
  renderCache.clear()
}
