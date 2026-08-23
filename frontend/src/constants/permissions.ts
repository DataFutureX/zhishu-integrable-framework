export const PERMISSIONS = {
  SYSTEM_USER_QUERY: 'system:user:query',
  SYSTEM_USER_ADD: 'system:user:add',
  SYSTEM_USER_EDIT: 'system:user:edit',
  SYSTEM_USER_REMOVE: 'system:user:remove',
  SYSTEM_USER_ASSIGN_ROLE: 'system:user:assignRole',

  SYSTEM_ROLE_QUERY: 'system:role:query',
  SYSTEM_ROLE_ADD: 'system:role:add',
  SYSTEM_ROLE_EDIT: 'system:role:edit',
  SYSTEM_ROLE_REMOVE: 'system:role:remove',
  SYSTEM_ROLE_ASSIGN_MENU: 'system:role:assignMenu',

  SYSTEM_MENU_QUERY: 'system:menu:query',
  SYSTEM_MENU_ADD: 'system:menu:add',
  SYSTEM_MENU_EDIT: 'system:menu:edit',
  SYSTEM_MENU_REMOVE: 'system:menu:remove',

  SYSTEM_UNIT_QUERY: 'system:unit:query',
  SYSTEM_UNIT_ADD: 'system:unit:add',
  SYSTEM_UNIT_EDIT: 'system:unit:edit',
  SYSTEM_UNIT_REMOVE: 'system:unit:remove',

  SYSTEM_CONFIG_QUERY: 'system:config:query',
  SYSTEM_CONFIG_EDIT: 'system:config:edit',

  SYSTEM_MONITOR_QUERY: 'system:monitor:query',
  SYSTEM_OPERLOG_QUERY: 'system:operlog:query',

  SYSTEM_ANNOUNCEMENT_QUERY: 'system:announcement:query',
  SYSTEM_ANNOUNCEMENT_ADD: 'system:announcement:add',
  SYSTEM_ANNOUNCEMENT_EDIT: 'system:announcement:edit',
  SYSTEM_ANNOUNCEMENT_REMOVE: 'system:announcement:remove',
  SYSTEM_ANNOUNCEMENT_PUBLISH: 'system:announcement:publish',

  DEVTOOLS_API_QUERY: 'devtools:api:query',

  AI_AGENT_QUERY: 'ai:agent:query',
  AI_AGENT_ADD: 'ai:agent:add',
  AI_AGENT_EDIT: 'ai:agent:edit',
  AI_AGENT_REMOVE: 'ai:agent:remove',
  AI_AGENT_GRAPH: 'ai:agent:graph',
  AI_MCP_EDIT: 'ai:mcp:edit',
  AI_KG_SYNC: 'ai:kg:sync',
  AI_QA_QUERY: 'ai:qa:query',
} as const

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS]
