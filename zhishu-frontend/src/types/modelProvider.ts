/** 模型设置（Model Provider）类型定义 */

export interface ModelProviderVO {
  id: number
  name: string
  providerKey: string
  baseUrl: string
  apiKeyMasked?: string | null
  apiKeyConfigured: boolean
  chatModel: string
  embeddingModel?: string | null
  temperature: number
  maxTokens: number
  topP: number
  isDefault: boolean
  status: 'ENABLED' | 'DISABLED' | string
  sortOrder: number
  remark?: string | null
  updateTime?: string | null
}

export interface ModelProviderCreateDTO {
  name: string
  providerKey: string
  baseUrl: string
  apiKey?: string | null
  chatModel: string
  embeddingModel?: string | null
  temperature: number
  maxTokens: number
  topP: number
  remark?: string | null
}

export interface ModelProviderUpdateDTO {
  name: string
  baseUrl: string
  apiKey?: string | null
  chatModel: string
  embeddingModel?: string | null
  temperature: number
  maxTokens: number
  topP: number
  status?: string
  sortOrder?: number
  remark?: string | null
}
