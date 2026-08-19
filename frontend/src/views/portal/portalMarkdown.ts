import MarkdownIt from 'markdown-it'

import {
  PORTAL_DOC_FILE_TO_ID,
  portalDocPath,
  type PortalDocId,
} from '@/utils/portalDocRoutes'

const REPO_BLOB = 'https://github.com/DataFutureX/yunqi-application-platform/blob/master/'
const REPO_TREE = 'https://github.com/DataFutureX/yunqi-application-platform/tree/master/'

function fileBasename(href: string): string {
  const path = href.split('?')[0]?.split('#')[0] ?? href
  const parts = path.replace(/\\/g, '/').split('/')
  const raw = parts[parts.length - 1] ?? path
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
}

function githubSlug(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\p{M}\s-]/gu, '')
    .replace(/\s+/g, '-')
}

export interface PortalDocTocItem {
  id: string
  text: string
  level: 2 | 3 | 4
}

interface PortalMarkdownEnv {
  slugCounts: Map<string, number>
  toc: PortalDocTocItem[]
}

function headingText(
  tokens: { type: string; children?: Array<{ content: string }> | null }[],
  idx: number,
): string {
  const inline = tokens[idx + 1]
  if (!inline || inline.type !== 'inline') return ''
  return (inline.children ?? []).map((child) => child.content).join('')
}

function splitTableRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '')
  const cells: string[] = []
  let current = ''
  let escaped = false
  for (const ch of trimmed) {
    if (escaped) {
      current += ch
      escaped = false
      continue
    }
    if (ch === '\\') {
      escaped = true
      continue
    }
    if (ch === '|') {
      cells.push(current.trim())
      current = ''
      continue
    }
    current += ch
  }
  cells.push(current.trim())
  return cells
}

function isSeparatorRow(line: string): boolean {
  const cells = splitTableRow(line)
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell.replace(/\s/g, '')))
}

function looksLikeTableRow(line: string): boolean {
  const trimmed = line.trim()
  return trimmed.includes('|') && !trimmed.startsWith('```')
}

function markdownItGfmTable(md: MarkdownIt): void {
  md.block.ruler.before('fence', 'gfm_table', (state, startLine, endLine, silent) => {
    const getLine = (line: number) =>
      state.src.slice(state.bMarks[line] + state.tShift[line], state.eMarks[line])

    const headerLine = getLine(startLine)
    if (!looksLikeTableRow(headerLine)) return false
    if (startLine + 1 >= endLine) return false
    const sepLine = getLine(startLine + 1)
    if (!isSeparatorRow(sepLine)) return false

    const header = splitTableRow(headerLine)
    if (header.length === 0) return false

    let nextLine = startLine + 2
    const body: string[][] = []
    while (nextLine < endLine) {
      const line = getLine(nextLine)
      if (!line.trim()) break
      if (!looksLikeTableRow(line) || isSeparatorRow(line)) break
      body.push(splitTableRow(line))
      nextLine += 1
    }

    if (silent) return true

    const tableOpen = state.push('table_open', 'table', 1)
    tableOpen.map = [startLine, nextLine]

    state.push('thead_open', 'thead', 1)
    state.push('tr_open', 'tr', 1)
    for (const cell of header) {
      state.push('th_open', 'th', 1)
      const inline = state.push('inline', '', 0)
      inline.content = cell
      inline.children = []
      state.push('th_close', 'th', -1)
    }
    state.push('tr_close', 'tr', -1)
    state.push('thead_close', 'thead', -1)

    if (body.length > 0) {
      state.push('tbody_open', 'tbody', 1)
      for (const row of body) {
        state.push('tr_open', 'tr', 1)
        for (let i = 0; i < header.length; i += 1) {
          state.push('td_open', 'td', 1)
          const inline = state.push('inline', '', 0)
          inline.content = row[i] ?? ''
          inline.children = []
          state.push('td_close', 'td', -1)
        }
        state.push('tr_close', 'tr', -1)
      }
      state.push('tbody_close', 'tbody', -1)
    }

    state.push('table_close', 'table', -1)
    state.line = nextLine
    return true
  })
}

function rewriteHref(href: string): string {
  let trimmed = href.trim()
  if (!trimmed) return trimmed
  try {
    trimmed = decodeURIComponent(trimmed)
  } catch {
    // keep original when the href is not a valid URI sequence
  }

  if (trimmed.startsWith('#')) return trimmed
  if (trimmed.startsWith('/docs')) return trimmed

  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('mailto:')) return trimmed

  const basename = fileBasename(trimmed).toLowerCase()
  const docId = PORTAL_DOC_FILE_TO_ID[basename]
  if (docId) return portalDocPath(docId)

  const normalized = trimmed.replace(/\\/g, '/').replace(/^\.\//, '')
  if (normalized.includes('sdk/yunqi-sso-partner-sdk')) {
    return `${REPO_TREE}sdk/yunqi-sso-partner-sdk`
  }
  if (
    normalized.startsWith('../') ||
    normalized.startsWith('backend/') ||
    normalized.startsWith('frontend/') ||
    normalized.startsWith('docs/') ||
    normalized.startsWith('sdk/')
  ) {
    const repoPath = normalized.replace(/^(\.\.\/)+/, '')
    if (repoPath.includes('.')) return `${REPO_BLOB}${repoPath}`
    return `${REPO_TREE}${repoPath}`
  }

  return trimmed
}

function createMarkdownRenderer(): MarkdownIt {
  const md = new MarkdownIt({
    html: false,
    linkify: true,
    typographer: true,
    breaks: false,
  })
  markdownItGfmTable(md)

  const defaultLinkOpen =
    md.renderer.rules.link_open ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

  md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
    const token = tokens[idx]
    const href = token.attrGet('href') ?? ''
    const nextHref = rewriteHref(href)
    token.attrSet('href', nextHref)
    if (/^https?:\/\//i.test(nextHref)) {
      token.attrSet('target', '_blank')
      token.attrSet('rel', 'noopener noreferrer')
    }
    return defaultLinkOpen(tokens, idx, options, env, self)
  }

  const defaultHeadingOpen =
    md.renderer.rules.heading_open ??
    ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

  md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
    const renderEnv = env as PortalMarkdownEnv
    const slugCounts = (renderEnv.slugCounts ??= new Map<string, number>())
    const base = githubSlug(headingText(tokens, idx)) || 'section'
    const seen = slugCounts.get(base) ?? 0
    slugCounts.set(base, seen + 1)
    const id = seen === 0 ? base : `${base}-${seen}`
    tokens[idx].attrSet('id', id)

    const tag = tokens[idx].tag
    const level = Number(tag.slice(1))
    if (level >= 2 && level <= 4) {
      const toc = (renderEnv.toc ??= [])
      toc.push({
        id,
        text: headingText(tokens, idx),
        level: level as PortalDocTocItem['level'],
      })
    }

    return defaultHeadingOpen(tokens, idx, options, env, self)
  }

  md.renderer.rules.image = () => ''
  return md
}

const md = createMarkdownRenderer()

export function preparePortalMarkdown(
  source: string,
  options?: { stripLeadingH1?: boolean },
): string {
  let text = source
    .replace(/!\[[^\]]*\]\([^)]*\)\s*/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  if (options?.stripLeadingH1) {
    text = text.replace(/^#\s+.+\n+/, '')
  }
  return text
}

export function prepareQuickStartMarkdown(source: string): string {
  let text = source
    .replace(/^## 目录[\s\S]*?(?=^## )/m, '')
    .replace(/^## 界面一览[\s\S]*?(?=^## )/m, '')
    .replace(/!\[[^\]]*\]\([^)]*\)\s*/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
  text = text.replace(/^#\s+.+\n+/, '')
  return text
}

export function renderPortalMarkdown(source: string): string {
  return renderPortalMarkdownWithToc(source).html
}

export function renderPortalMarkdownWithToc(source: string): {
  html: string
  toc: PortalDocTocItem[]
} {
  const env: PortalMarkdownEnv = { slugCounts: new Map(), toc: [] }
  const html = md.render(source, env)
  return { html, toc: env.toc }
}

/** 按文档 id 渲染 Markdown 原文为 HTML 与目录 */
export function renderDocContent(
  id: PortalDocId,
  source: string,
): { html: string; toc: PortalDocTocItem[] } {
  const prepared =
    id === 'quickstart' ? prepareQuickStartMarkdown(source) : preparePortalMarkdown(source)
  return renderPortalMarkdownWithToc(prepared)
}

/** @deprecated 使用 renderDocContent */
export function renderDocHtml(id: PortalDocId, source: string): string {
  return renderDocContent(id, source).html
}

const DOC_SOURCE_LOADERS: Record<
  PortalDocId,
  () => Promise<{ default: string }>
> = {
  quickstart: () => import('../../../../README.md?raw'),
  sso: () => import('../../../../docs/单点登录对接说明.md?raw'),
  wanxiang: () => import('../../../../docs/万象接入联调实现步骤.md?raw'),
  'sso-sdk': () => import('../../../../docs/他方SSO接入SDK使用说明.md?raw'),
}

export async function loadPortalDocContent(
  id: PortalDocId,
): Promise<{ html: string; toc: PortalDocTocItem[] }> {
  const { default: source } = await DOC_SOURCE_LOADERS[id]()
  return renderDocContent(id, source)
}

export async function loadPortalDocHtml(id: PortalDocId): Promise<string> {
  const { html } = await loadPortalDocContent(id)
  return html
}
