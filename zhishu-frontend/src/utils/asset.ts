/** 将相对路径解析为可访问的资源 URL */
export const resolveAssetUrl = (path?: string) => {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const base = import.meta.env.DEV
    ? (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080')
    : (import.meta.env.VITE_API_BASE_URL || '')
  return `${base.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`
}
