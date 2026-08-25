/**
 * 纯函数权限匹配，便于单测与指令 / Store 复用。
 */
export function normalizePermissionCodes(code?: string | string[] | null): string[] {
  if (!code) return []
  return (Array.isArray(code) ? code : [code]).map(String).filter(Boolean)
}

export function matchPermissions(
  owned: string[] | null | undefined,
  required: string | string[] | null | undefined,
  options?: { requireAll?: boolean; isAdmin?: boolean },
): boolean {
  if (options?.isAdmin) return true

  const need = normalizePermissionCodes(required)
  if (!need.length) return true

  const have = new Set((owned || []).map(String))
  if (options?.requireAll) {
    return need.every((code) => have.has(code))
  }
  return need.some((code) => have.has(code))
}

/** 从路由匹配链收集权限码（去重） */
export function collectRoutePermissions(
  matched: Array<{ meta?: { permissions?: string | string[] } }>,
): string[] {
  const codes: string[] = []
  matched.forEach((record) => {
    codes.push(...normalizePermissionCodes(record.meta?.permissions))
  })
  return [...new Set(codes)]
}
