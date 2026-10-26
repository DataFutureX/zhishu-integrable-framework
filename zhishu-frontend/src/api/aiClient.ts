import { createHttpClient } from '@/utils/request'

/**
 * AI 域接口客户端：与主站共享同一套拦截器（鉴权 / 解包 / 401 登出 / 错误提示），
 * 仅把默认超时放宽到 120s —— 模型推理、文档解析、Agent 试跑均远超 15s。
 *
 * 约定：演示分支用构建期常量 `__DEMO_MODE__`，mock 一律 `import('@/mock/**')` 惰性加载，
 * 避免演示数据进生产包。
 */
export const aiService = createHttpClient({ timeout: 120000 })
