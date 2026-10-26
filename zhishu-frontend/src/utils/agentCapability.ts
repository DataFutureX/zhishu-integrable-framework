import type { CapabilityVO, ToolInfoVO } from '@/types/aiAgent'

/**
 * 能力绑定的工具清单：优先后端下发的 tools（含 @Tool 描述），
 * 其次回退到 toolNames（无描述）。
 * 列表页统计工具数量与表单弹窗展示 tool chip 共用，避免两处实现漂移。
 */
export function resolveCapabilityTools(cap: CapabilityVO): ToolInfoVO[] {
  if (cap.tools?.length) return cap.tools
  return (cap.toolNames || []).map((name) => ({ name, description: '' }))
}
