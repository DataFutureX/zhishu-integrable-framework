/**
 * 解析 Swagger UI 嵌入地址。
 * 开发环境优先走 Vite 同源代理，避免跨域与 X-Frame-Options 问题。
 * 演示模式无后端，返回空串由页面展示空状态。
 */
export function resolveSwaggerUiUrl(): string {
  if (import.meta.env.VITE_DEMO_MODE === 'true') {
    return ''
  }

  const explicit = import.meta.env.VITE_SWAGGER_URL?.trim()
  if (explicit) {
    return explicit
  }

  if (import.meta.env.DEV) {
    return '/swagger-ui/index.html'
  }

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  if (!apiBase) {
    return '/swagger-ui/index.html'
  }

  try {
    const url = new URL(apiBase)
    return `${url.origin}/swagger-ui/index.html`
  } catch {
    return `${apiBase.replace(/\/api$/, '')}/swagger-ui/index.html`
  }
}
