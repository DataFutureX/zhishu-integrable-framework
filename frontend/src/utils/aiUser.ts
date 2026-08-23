/**
 * 解析当前登录用户 ID，供 AI 接口 X-User-Id 使用。
 */
export function resolveAiUserId(): string | undefined {
  try {
    const raw = sessionStorage.getItem('userInfo')
    if (!raw) return undefined
    const info = JSON.parse(raw) as { id?: string | number | null }
    if (info?.id == null || info.id === '') return undefined
    return String(info.id)
  } catch {
    return undefined
  }
}
