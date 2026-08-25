/** 开放应用 VO */
export interface OpenAppVO {
  id: number
  code: string
  name: string
  status: string
  /** 允许的调用范围，JSON 数组字符串 */
  allowedScopes: string
  remark: string
  /** Access Key（明文） */
  accessKey: string | null
  /** AK/SK 最近生成时间 */
  akskGeneratedAt: string | null
  /** 最近一次调用时间 */
  lastUsedAt: string | null
  createTime: string
}

/** 生成 AK/SK 结果 */
export interface GenerateAkSkResult {
  accessKey: string
  secretKey: string
}
