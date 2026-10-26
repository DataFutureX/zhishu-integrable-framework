import { Cpu, Guide, RefreshRight, Share, Sort } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import type { WorkflowTemplateVO } from '@/types/aiAgent'

/**
 * 工作流模板的展示元数据（色调 / 图标 / 提示文案）。
 * 智能体表单弹窗与列表共用，避免样式类名与图标散落在视图里。
 */

const TONE_MAP: Record<string, string> = {
  REACT: 'react',
  SEQUENTIAL: 'sequential',
  ROUTING: 'routing',
  GRAPH: 'graph',
}

const ICON_MAP: Record<string, Component> = {
  REACT: RefreshRight,
  SEQUENTIAL: Sort,
  ROUTING: Guide,
  GRAPH: Share,
}

const HINT_MAP: Record<string, string> = {
  REACT: '单智能体推理-行动循环：模型边思考边按需调用 Tools，适合通用问答与工具调用。',
  SEQUENTIAL: '固定三步：先澄清意图，再调用工具执行，最后润色答案。适合巡检、报告等结构化任务。',
  ROUTING:
    '入口先判断问题类型，再路由到「数据查询」或「知识问答」子智能体。适合问题差异较大的场景。',
  GRAPH:
    '按可视化节点与边编排执行（START / LLM / TOOL_AGENT / ROUTER / END）。保存后可进入工作流编排画布。',
}

/** 卡片色调修饰符（对应 `.workflow-pick--{tone}`） */
export function workflowTone(code: string): string {
  return TONE_MAP[code] || 'react'
}

/** 卡片图标 */
export function workflowIcon(code: string): Component {
  return ICON_MAP[code] || Cpu
}

/** 卡片悬浮提示 */
export function workflowHint(template: WorkflowTemplateVO): string {
  return HINT_MAP[template.code] || template.description
}
