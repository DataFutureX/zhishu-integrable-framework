import type { Component } from 'vue'
import {
  Bell,
  ChatDotRound,
  Connection,
  Cpu,
  Document,
  FolderOpened,
  Link,
  Monitor,
  OfficeBuilding,
  Search,
  Tools,
  User,
  UserFilled,
} from '@element-plus/icons-vue'

export type DashboardQuickActionAccent =
  | 'primary'
  | 'success'
  | 'warning'
  | 'info'
  | 'danger'

export interface DashboardQuickActionDef {
  key: string
  label: string
  desc: string
  path: string
  icon: Component
  accent: DashboardQuickActionAccent
}

/** 仪表盘快捷操作（对齐工作台 / 智能中心 / 账号管理 / 系统设置） */
export const DASHBOARD_QUICK_ACTIONS: DashboardQuickActionDef[] = [
  {
    key: 'chat',
    label: 'Agent 会话',
    desc: '与智能体对话协作',
    path: '/ai/chat',
    icon: ChatDotRound,
    accent: 'primary',
  },
  {
    key: 'qa',
    label: '知识检索',
    desc: '基于知识库问答检索',
    path: '/ai/qa',
    icon: Search,
    accent: 'success',
  },
  {
    key: 'agents',
    label: 'Agents',
    desc: '智能体人设与工作流',
    path: '/ai/agents',
    icon: Cpu,
    accent: 'warning',
  },
  {
    key: 'knowledges',
    label: '知识库',
    desc: '文档入库、分类与 RAG',
    path: '/ai/knowledges',
    icon: FolderOpened,
    accent: 'success',
  },
  {
    key: 'mcp',
    label: 'MCP Hub',
    desc: '接入上游与对外 Client',
    path: '/ai/mcp',
    icon: Link,
    accent: 'primary',
  },
  {
    key: 'knowledge-graph',
    label: '知识图谱',
    desc: '实体关系可视化探索',
    path: '/ai/knowledge-graph',
    icon: Connection,
    accent: 'info',
  },
  {
    key: 'users',
    label: '用户管理',
    desc: '账号维护与角色分配',
    path: '/permission/user',
    icon: User,
    accent: 'primary',
  },
  {
    key: 'units',
    label: '单位管理',
    desc: '组织机构树维护',
    path: '/permission/unit',
    icon: OfficeBuilding,
    accent: 'warning',
  },
  {
    key: 'roles',
    label: '角色管理',
    desc: '角色与菜单权限',
    path: '/permission/role',
    icon: UserFilled,
    accent: 'success',
  },
  {
    key: 'open-api',
    label: '开放能力',
    desc: '开放应用 AK/SK 与接口目录',
    path: '/system/open-api',
    icon: Document,
    accent: 'info',
  },
  {
    key: 'announcement',
    label: '公告管理',
    desc: '发布系统通知公告',
    path: '/system/announcement',
    icon: Bell,
    accent: 'danger',
  },
  {
    key: 'monitor',
    label: '运维监控',
    desc: '服务与资源运行状态',
    path: '/monitor/ops',
    icon: Monitor,
    accent: 'primary',
  },
  {
    key: 'settings',
    label: '参数配置',
    desc: '品牌名称与登录策略',
    path: '/system/config',
    icon: Tools,
    accent: 'warning',
  },
]
