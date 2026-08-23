/** 清理本地登录态（不通知服务端） */
export function clearStoredSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('tokenExpiration')
  sessionStorage.removeItem('userInfo')
  sessionStorage.removeItem('permissions')
}

/** 读取未过期的 token；过期则自动清理 */
export function getValidToken(): string | null {
  const token = localStorage.getItem('token')
  if (!token) return null

  const expiration = Number(localStorage.getItem('tokenExpiration') || 0)
  if (expiration > 0 && Date.now() >= expiration) {
    clearStoredSession()
    return null
  }

  return token
}

/** 无需登录即可访问的前端路由 */
export function isPublicAppPath(path: string): boolean {
  return (
    path === '/login' ||
    path.startsWith('/portal') ||
    path.startsWith('/docs') ||
    path.startsWith('/sso/')
  )
}
