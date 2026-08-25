/** 公开页冒烟清单 */
export const PUBLIC_PAGES = [
  {
    name: 'portal',
    path: '/portal',
    /** 门户品牌标题 */
    expectSelector: '.portal-hero__brand',
  },
  {
    name: 'docs',
    path: '/docs/quickstart',
    expectSelector: '.docs-page__title',
  },
  {
    name: 'docs-sso',
    path: '/docs/sso',
    expectSelector: '.portal-docs__heading',
  },
  {
    name: 'login',
    path: '/login',
    expectSelector: 'input[placeholder="用户名"]',
  },
] as const

export type AuthPageFixture = {
  name: string
  path: string
  /** 期望可见的标题文案；为空则只校验 selector 可见 */
  title?: string
  titleSelector: string
}

/** 登录后业务页（与根目录截图脚本路径对齐） */
export const AUTH_PAGES: AuthPageFixture[] = [
  {
    name: 'agent-chat',
    path: '/ai/chat',
    title: 'Agent 会话',
    titleSelector: '.session-rail__title',
  },
  {
    name: 'knowledge-qa',
    path: '/ai/qa',
    title: '检索记录',
    titleSelector: '.session-rail__title',
  },
  {
    name: 'user',
    path: '/permission/user',
    title: '用户管理',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'unit',
    path: '/permission/unit',
    title: '单位管理',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'role',
    path: '/permission/role',
    title: '角色管理',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'menu',
    path: '/permission/menu',
    title: '菜单管理',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'system-config',
    path: '/system/config',
    title: '参数配置',
    titleSelector: '.page-header__title',
  },
  {
    name: 'announcement',
    path: '/system/announcement',
    title: '公告管理',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'operation-log',
    path: '/system/operation-log',
    title: '操作日志',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'monitor',
    path: '/monitor/ops',
    title: '运维监控',
    titleSelector: '.monitor-hero__title',
  },
  {
    name: 'open-api',
    path: '/system/open-api',
    title: '开放能力',
    titleSelector: '.page-header__title',
  },
  {
    name: 'api-docs',
    path: '/devtools/api',
    title: '后端接口',
    titleSelector: '.swagger-embed__title',
  },
  {
    name: 'agents',
    path: '/ai/agents',
    title: 'Agents',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'knowledges',
    path: '/ai/knowledges',
    title: '知识库',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'mcp-hub',
    path: '/ai/mcp',
    title: 'MCPs',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'model-config',
    path: '/ai/model-config',
    title: '模型设置',
    titleSelector: '.page-header__title',
  },
  {
    name: 'knowledge-graph',
    path: '/ai/knowledge-graph',
    title: '知识图谱',
    titleSelector: '.page-hero__title',
  },
  {
    name: 'profile',
    path: '/profile/info',
    title: '个人信息',
    titleSelector: '.profile-card .el-card__header',
  },
  {
    name: 'change-password',
    path: '/profile/password',
    title: '修改密码',
    titleSelector: '.el-card__header',
  },
]
