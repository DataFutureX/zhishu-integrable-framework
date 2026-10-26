import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { isDemoMode } from '@/config/demo'
import type { ApiResponse } from '@/types/common'
import { getValidToken, isPublicAppPath } from '@/utils/session'
import { showErrorMessage } from '@/utils/uiMessage'

export type { ApiResponse }

/** 扩展请求配置：静默失败（不弹全局错误、401 不强制登出） */
export interface AppAxiosRequestConfig extends AxiosRequestConfig {
  skipErrorMessage?: boolean
}

/**
 * 业务失败（HTTP 200 但业务 code !== 200）。
 * 拦截器已弹过提示，全局 errorHandler 据此跳过，避免同一次失败弹两次窗。
 */
export class HttpError extends Error {
  readonly code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'HttpError'
    this.code = code
  }
}

const shouldSkipErrorMessage = (config?: AxiosRequestConfig) =>
  Boolean((config as AppAxiosRequestConfig | undefined)?.skipErrorMessage)

/** 统一 baseURL：演示 / 开发模式走同源代理，生产由 VITE_API_BASE_URL 拼出 /v1 */
function resolveApiBaseUrl(): string {
  if (isDemoMode || import.meta.env.DEV) return '/api/v1'
  const base = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return `${base}/v1`
}

const transformResponseList = [
  (data: unknown) => {
    // 雪花 ID 由服务端序列化为字符串（见后端 JacksonConfig），此处依赖 axios 默认 JSON 解析即可
    if (typeof data !== 'string') return data
    try {
      return JSON.parse(data)
    } catch {
      return data
    }
  },
]

/** 演示模式：惰性加载 mock adapter，打断 request ↔ mock 的静态循环依赖，并保证首请求即可走 mock */
function attachDemoMockAdapter(instance: AxiosInstance) {
  let mockAdapterPromise: Promise<typeof import('@/mock/adapter').mockAdapter> | null = null
  instance.defaults.adapter = async (config) => {
    mockAdapterPromise ??= import('@/mock/adapter').then((m) => m.mockAdapter)
    const adapter = await mockAdapterPromise
    return adapter(config)
  }
}

const onRequest = (config: InternalAxiosRequestConfig) => {
  // 直接读 localStorage，避免 request → useUserStore → api → request 循环依赖
  const token = getValidToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

const onRequestError = (error: unknown) => {
  console.error('Request error:', error)
  return Promise.reject(error)
}

const handleUnauthorized = (message?: string) => {
  if (isPublicAppPath(window.location.pathname)) return

  showErrorMessage(message || '登录已失效，请重新登录')
  void import('@/utils/logout').then(({ logoutAndRedirect }) => {
    void logoutAndRedirect(undefined, { silent: true, notifyServer: false })
  })
}

const onResponse = (
  // 拦截器解包业务 data；Axios 类型要求返回 AxiosResponse，此处用断言放宽
  response: AxiosResponse<ApiResponse>,
) => {
  const res = response.data
  const silent = shouldSkipErrorMessage(response.config)

  if (res.code !== 200) {
    if (res.code === 401) {
      if (!silent) handleUnauthorized(res.message)
      return Promise.reject(new HttpError(res.message || '未授权', res.code))
    }

    if (!silent) showErrorMessage(res.message || '请求失败')
    return Promise.reject(new HttpError(res.message || '请求失败', res.code))
  }

  return res.data as never
}

const onResponseError = (error: AxiosError<{ message?: string }>) => {
  console.error('Response error:', error)
  const silent = shouldSkipErrorMessage(error.config)

  if (error.response?.status === 401) {
    const backendMessage = error.response?.data?.message
    if (!silent) handleUnauthorized(backendMessage)
    return Promise.reject(error)
  }

  if (silent) {
    return Promise.reject(error)
  }

  let message = '网络错误'
  if (error.response) {
    const backendMessage = error.response.data?.message
    switch (error.response.status) {
      case 400:
        message = backendMessage || '请求参数错误'
        break
      case 403:
        message = backendMessage || '拒绝访问'
        break
      case 404:
        message = '请求地址不存在'
        break
      case 429:
        message = backendMessage || '请求过于频繁，请稍后重试'
        break
      case 500:
        message = backendMessage || '服务器错误'
        break
      case 503:
        message = '服务不可用'
        break
      default:
        message = backendMessage || `连接错误${error.response.status}`
    }
  } else {
    message = '网络连接异常'
  }

  showErrorMessage(message)
  return Promise.reject(error)
}

export interface HttpClientOptions {
  /** 默认超时（毫秒），默认 15000；AI 等长耗时接口可传更大值 */
  timeout?: number
}

/**
 * 创建与主站共享同一套拦截器的 axios 实例。
 * 鉴权头、业务解包、401 登出、错误提示文案均在此统一定义，
 * 避免各业务线自建实例后行为发散（历史上 AI 实例 401 不登出、错误文案被泛化）。
 */
export function createHttpClient(options: HttpClientOptions = {}): AxiosInstance {
  const instance = axios.create({
    baseURL: resolveApiBaseUrl(),
    timeout: options.timeout ?? 15000,
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
    },
    transformResponse: transformResponseList,
  })

  if (isDemoMode) {
    attachDemoMockAdapter(instance)
  }

  instance.interceptors.request.use(onRequest, onRequestError)
  instance.interceptors.response.use(onResponse, onResponseError)
  return instance
}

/** 主站默认客户端（15s 超时） */
export const service: AxiosInstance = createHttpClient()

export const get = <T = unknown>(url: string, config?: AppAxiosRequestConfig): Promise<T> => {
  return service.get(url, config)
}

export const post = <T = unknown>(
  url: string,
  data?: unknown,
  config?: AppAxiosRequestConfig,
): Promise<T> => {
  return service.post(url, data, config)
}

export const put = <T = unknown>(
  url: string,
  data?: unknown,
  config?: AppAxiosRequestConfig,
): Promise<T> => {
  return service.put(url, data, config)
}

export const del = <T = unknown>(url: string, config?: AppAxiosRequestConfig): Promise<T> => {
  return service.delete(url, config)
}

export default service
