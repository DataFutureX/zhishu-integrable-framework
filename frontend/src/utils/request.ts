import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { isDemoMode } from '@/config/demo'
import { showErrorMessage } from '@/utils/uiMessage'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 扩展请求配置：静默失败（不弹全局错误、401 不强制登出） */
export interface AppAxiosRequestConfig extends AxiosRequestConfig {
  skipErrorMessage?: boolean
}

const shouldSkipErrorMessage = (config?: AxiosRequestConfig) =>
  Boolean((config as AppAxiosRequestConfig | undefined)?.skipErrorMessage)

const preprocessLargeIntegers = (text: string): unknown => {
  try {
    const processed = text.replace(/:\s*(-?\d{16,})/g, (_match, numberStr: string) => {
      const num = Number(numberStr)
      if (num > Number.MAX_SAFE_INTEGER || num < Number.MIN_SAFE_INTEGER) {
        return `:"${numberStr}"`
      }
      return _match
    })
    return JSON.parse(processed)
  } catch (error) {
    console.error('JSON解析失败:', error)
    try {
      return JSON.parse(text)
    } catch {
      return null
    }
  }
}

const service: AxiosInstance = axios.create({
  baseURL: isDemoMode
    ? '/api/v1'
    : import.meta.env.DEV
      ? '/api/v1'
      : import.meta.env.VITE_API_BASE_URL + '/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
  transformResponse: [
    (data) => {
      if (typeof data === 'object' && data !== null) {
        return data
      }
      if (typeof data === 'string') {
        return preprocessLargeIntegers(data)
      }
      return data
    },
  ],
})

if (isDemoMode) {
  // 惰性加载 mock adapter，打断 request ↔ mock 的静态循环依赖，并保证首请求即可走 mock
  let mockAdapterPromise: Promise<typeof import('@/mock/adapter').mockAdapter> | null = null
  service.defaults.adapter = async (config) => {
    mockAdapterPromise ??= import('@/mock/adapter').then((m) => m.mockAdapter)
    const adapter = await mockAdapterPromise
    return adapter(config)
  }
}

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 直接读 localStorage，避免 request → useUserStore → api → request 循环依赖
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  },
)

const handleUnauthorized = (message?: string) => {
  showErrorMessage(message || '登录已失效，请重新登录')
  void import('@/utils/logout').then(({ logoutAndRedirect }) => {
    void logoutAndRedirect(undefined, { silent: true, notifyServer: false })
  })
}

service.interceptors.response.use(
  // 拦截器解包业务 data；Axios 类型要求返回 AxiosResponse，此处用断言放宽
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    const silent = shouldSkipErrorMessage(response.config)

    if (res.code !== 200) {
      if (res.code === 401) {
        if (!silent) handleUnauthorized(res.message)
        return Promise.reject(new Error(res.message || '未授权'))
      }

      if (!silent) showErrorMessage(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res.data as never
  },
  (error) => {
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
  },
)

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
