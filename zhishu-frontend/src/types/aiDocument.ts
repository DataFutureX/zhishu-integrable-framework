/** AI 知识管理 / 知识问答 */

export interface KnowledgesCategoryVO {
  id: string
  code: string
  name: string
  description?: string
  sortOrder?: number
  status: string
  documentCount?: number
  createTime?: string
  updateTime?: string
}

/** @deprecated 使用 KnowledgesCategoryVO */
export type DocumentCategoryVO = KnowledgesCategoryVO

export interface KnowledgesCategoryCreateDTO {
  code: string
  name: string
  description?: string
  sortOrder?: number
}

/** @deprecated 使用 KnowledgesCategoryCreateDTO */
export type DocumentCategoryCreateDTO = KnowledgesCategoryCreateDTO

export interface KnowledgesCategoryUpdateDTO {
  name?: string
  description?: string
  sortOrder?: number
  status?: string
}

/** @deprecated 使用 KnowledgesCategoryUpdateDTO */
export type DocumentCategoryUpdateDTO = KnowledgesCategoryUpdateDTO

export interface DocumentVO {
  /** 雪花 ID，后端以字符串返回，避免 JS 精度丢失 */
  id: string
  fileName: string
  fileType: string
  fileSize: number
  uploadTime: string
  processed: boolean
  /** 所属知识库分类 */
  categoryId?: string
  categoryName?: string
  /** 文档解析后的文本内容（详情接口返回） */
  content?: string
}

export interface DocumentUploadParams {
  title: string
  categoryId?: string
  file: File
}

export interface DocumentQueryDTO {
  question: string
  documentId?: string
  /** 知识库分类 ID */
  categoryId?: string
  topK?: number
  /** 多轮文档问答会话 ID */
  conversationId?: string
}
