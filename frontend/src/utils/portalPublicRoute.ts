/** 门户与文档公开页：首屏不加载 Element Plus */
export function isPortalPublicPath(path: string): boolean {
  return path === '/portal' || path.startsWith('/docs')
}
