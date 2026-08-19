/** AI 模型运行时配置 */

export interface AiModelConfigVO {
  chatModel: string
  embeddingModel: string
  temperature: number
  maxTokens: number
  topP: number
  enableRagDefault: boolean
  memoryWindowSize: number
  baseUrl?: string | null
  apiKeyMasked?: string | null
  remark?: string | null
  chatModelOptions: string[]
  embeddingModelOptions: string[]
  updateTime?: string | null
}

export interface AiModelConfigUpdateDTO {
  chatModel: string
  embeddingModel: string
  temperature: number
  maxTokens: number
  topP: number
  enableRagDefault: boolean
  memoryWindowSize: number
  remark?: string | null
}
