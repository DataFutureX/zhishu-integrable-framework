/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEMO_MODE?: string
  readonly VITE_PORT?: string
  readonly VITE_API_BASE_URL?: string
  /** 可选：覆盖默认 Swagger UI 地址 */
  readonly VITE_SWAGGER_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

declare module '*.md?raw' {
  const content: string
  export default content
}
