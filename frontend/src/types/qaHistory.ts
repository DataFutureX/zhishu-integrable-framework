export type QaHistoryScene = 'CHAT' | 'DOCUMENT_QA'

export interface QaHistoryVO {
  id: string
  scene: QaHistoryScene | string
  question: string
  answer: string
  model?: string | null
  documentId?: string
  conversationId?: string
  createTime?: string
}
