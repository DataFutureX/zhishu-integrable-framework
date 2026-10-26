/// <reference types="vite/client" />

/**
 * 构建期常量（由 vite.config.ts 的 define 注入）。
 * 必须声明为 `false` 字面量类型，Rollup/esbuild 才能把 `if (isDemoMode)` 分支静态剔除，
 * 从而让 src/mock/** 不进生产包。
 */
declare const __DEMO_MODE__: boolean

interface ImportMetaEnv {
  readonly VITE_DEMO_MODE?: string
  readonly VITE_PORT?: string
  readonly VITE_API_BASE_URL?: string
  /** 可选：覆盖默认 Swagger UI 地址 */
  readonly VITE_SWAGGER_URL?: string
  /** 可选：AI 流式（SSE）首包超时毫秒数，缺省 5 分钟，0 表示不限制 */
  readonly VITE_AI_SSE_FIRST_BYTE_TIMEOUT_MS?: string
  /** 可选：AI 流式（SSE）空闲超时毫秒数，缺省 5 分钟，0 表示不限制 */
  readonly VITE_AI_SSE_IDLE_TIMEOUT_MS?: string
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

/** Element Plus 的按需样式入口（仅副作用导入，无类型声明文件） */
declare module 'element-plus/es/components/*/style/css'
