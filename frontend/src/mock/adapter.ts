import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { handleMockRequest, MockNotFoundError } from './handler'
import { delay } from './utils'

function buildResponse(
  config: InternalAxiosRequestConfig,
  code: number,
  message: string,
  data: unknown,
  status = 200,
): AxiosResponse {
  return {
    data: { code, message, data },
    status,
    statusText: status === 200 ? 'OK' : 'Error',
    headers: {},
    config,
  }
}

export const mockAdapter: AxiosAdapter = async (config) => {
  await delay(80 + Math.random() * 120)

  const method = (config.method || 'get').toUpperCase()
  const url = config.url || ''

  try {
    const data = handleMockRequest({
      method,
      url,
      params: config.params as Record<string, unknown> | undefined,
      data: config.data,
    })
    return buildResponse(config, 200, 'success', data)
  } catch (error) {
    if (error instanceof MockNotFoundError) {
      console.warn('[Demo]', error.message)
      return buildResponse(config, 404, error.message, null, 404)
    }
    const message = error instanceof Error ? error.message : '演示模式请求失败'
    return buildResponse(config, 500, message, null, 500)
  }
}
