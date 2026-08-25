import type { PageLayoutType } from '@/types/route'

export function parseRouteMetaJson(meta?: string): Record<string, unknown> {
  if (!meta) return {}
  try {
    return JSON.parse(meta) as Record<string, unknown>
  } catch {
    return {}
  }
}

export function resolveMatchedLayoutType(
  matched: { meta: Record<string, unknown> }[],
): PageLayoutType {
  for (let i = matched.length - 1; i >= 0; i--) {
    const meta = matched[i].meta
    if (
      meta.layout === 'default' ||
      meta.layout === 'dashboard' ||
      meta.layout === 'fullscreen'
    ) {
      return meta.layout as PageLayoutType
    }
  }

  return 'default'
}
