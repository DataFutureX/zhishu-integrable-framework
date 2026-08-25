/** 门户文档 id，对应路径 `/docs/<id>` */
export const PORTAL_DOC_IDS = [
  'quickstart',
  'sso',
  'wanxiang',
  'sso-sdk',
  'openapi-sdk',
] as const
export type PortalDocId = (typeof PORTAL_DOC_IDS)[number]
export const PORTAL_DOCS_BASE = '/docs'

export function portalDocPath(id: PortalDocId): string {
  return `${PORTAL_DOCS_BASE}/${id}`
}

/** md 文件名 → 文档 id（路径解析与 Markdown 链接改写共用） */
export const PORTAL_DOC_FILE_TO_ID: Record<string, PortalDocId> = {
  'readme.md': 'quickstart',
  '单点登录对接说明.md': 'sso',
  '万象接入联调实现步骤.md': 'wanxiang',
  '他方sso接入sdk使用说明.md': 'sso-sdk',
  '知枢openapi接入sdk使用说明.md': 'openapi-sdk',
}

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

export function isPortalDocId(value: string): value is PortalDocId {
  return (PORTAL_DOC_IDS as readonly string[]).includes(value)
}

function parseDocHash(hash: string): PortalDocId | null {
  const h = hash.replace(/^#/, '')
  if (!h) return null
  if (h === 'quickstart' || h === 'docs') return 'quickstart'
  if (h.startsWith('docs/')) {
    const id = h.slice('docs/'.length).split('/')[0] ?? ''
    return isPortalDocId(id) ? id : 'quickstart'
  }
  return isPortalDocId(h) ? h : null
}

/** 兼容路径 `/docs/sso` 与旧锚点 `#docs/sso` */
export function parsePortalDocRef(ref: string): PortalDocId | null {
  const raw = decodeURIComponent(ref.trim())
  if (!raw) return null

  const withoutOrigin = raw.replace(/^https?:\/\/[^/]+/i, '')
  const [pathPart, hashPart] = withoutOrigin.split('#')
  const path = (pathPart ?? '').split('?')[0] ?? ''

  const pathMatch = path.match(/\/docs(?:\/([^/]*))?$/)
  if (pathMatch && !/\.md$/i.test(path)) {
    const id = pathMatch[1]
    if (!id) return hashPart ? (parseDocHash(hashPart) ?? 'quickstart') : 'quickstart'
    return isPortalDocId(id) ? id : 'quickstart'
  }

  if (hashPart) return parseDocHash(hashPart)

  const basename = fileBasename(withoutOrigin || raw).toLowerCase()
  if (basename.endsWith('.md') && PORTAL_DOC_FILE_TO_ID[basename]) {
    return PORTAL_DOC_FILE_TO_ID[basename]
  }

  if (!path.startsWith('/')) return parseDocHash(raw)
  return null
}
