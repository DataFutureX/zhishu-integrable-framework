import type { Component } from 'vue'
import {
  Bell,
  Document,
  Lock,
  Monitor,
  Notebook,
  OfficeBuilding,
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

/** 仪表盘快捷操作（对齐当前菜单域：permission / monitor / system） */
export const DASHBOARD_QUICK_ACTIONS: DashboardQuickActionDef[] = [
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
    key: 'menus',
    label: '菜单管理',
    desc: '导航结构与按钮权限',
    path: '/permission/menu',
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
    label: '系统监控',
    desc: '服务与资源运行状态',
    path: '/monitor/ops',
    icon: Monitor,
    accent: 'primary',
  },
  {
    key: 'operation-log',
    label: '操作日志',
    desc: '审计关键操作留痕',
    path: '/system/operation-log',
    icon: Notebook,
    accent: 'info',
  },
  {
    key: 'settings',
    label: '系统设置',
    desc: '品牌名称与登录策略',
    path: '/system/config',
    icon: Tools,
    accent: 'warning',
  },
  {
    key: 'password',
    label: '修改密码',
    desc: '个人账号安全设置',
    path: '/profile/password',
    icon: Lock,
    accent: 'danger',
  },
]
