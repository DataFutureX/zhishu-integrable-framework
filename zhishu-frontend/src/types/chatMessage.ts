import type { ChatMessageBase } from '@/composables/useChatSession'
import type {
  AgentTraceEvent,
  AlarmSummary,
  StationCompareResult,
  TrendAnalysisResult,
} from '@/types/aiChat'

/**
 * Agent 会话消息（AIChat 页面与其消息卡片组件共用）。
 * 文档问答（DocumentQA）消息更简单，沿用该文件内的局部 Message 类型。
 */
export interface AgentChatMessage extends ChatMessageBase {
  /** 结构化输出（多站对比 / 趋势分析 / 告警摘要） */
  structured?: StationCompareResult | TrendAnalysisResult | AlarmSummary | null
  /** Agent 执行轨迹 */
  traces?: AgentTraceEvent[] | null
  /** 欢迎语示例提问（可点击填入输入框） */
  examples?: string[]
}
