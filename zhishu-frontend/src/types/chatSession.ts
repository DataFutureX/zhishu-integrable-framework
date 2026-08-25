import type { QaHistoryScene } from '@/types/qaHistory'

export interface ChatSessionVO {
  conversationId: string
  scene: QaHistoryScene | string
  title?: string
  agentId?: number
  createTime?: string
  updateTime?: string
}

export interface ChatSessionCreateDTO {
  scene?: QaHistoryScene | string
  title?: string
  agentId?: number
}

export interface ChatSessionTitleDTO {
  title: string
}
