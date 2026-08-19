-- 已有库补丁：写入知枢 AI 管理菜单并授权给 ADMIN 角色
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_ai_menus.sql

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(10, 0, 'AI 管理', '/ai', 'AI', '/ai/agents', 'MagicStick', 'DIRECTORY', 1, 1, 6, NULL, NULL, 1),
(101, 10, 'Agents', '/ai/agents', 'AIAgentManage', NULL, 'Cpu', 'MENU', 1, 1, 1, 'views/ai/AgentManage.vue', '{"title":"Agents 管理"}', 1),
(102, 10, '知识库', '/ai/knowledges', 'AIDocumentManage', NULL, 'FolderOpened', 'MENU', 1, 1, 2, 'views/ai/DocumentManage.vue', '{"title":"知识库"}', 1),
(103, 10, 'Agent 会话', '/ai/chat', 'AIChat', NULL, 'ChatLineRound', 'MENU', 1, 1, 3, 'views/ai/AIChat.vue', '{"title":"Agent 会话"}', 1),
(104, 10, 'MCP Hub', '/ai/mcp', 'AIMcpHub', NULL, 'Link', 'MENU', 1, 1, 4, 'views/ai/McpHub.vue', '{"title":"MCP Hub"}', 1),
(105, 10, '模型设置', '/ai/model-config', 'AIModelConfig', NULL, 'SetUp', 'MENU', 1, 1, 5, 'views/ai/ModelConfig.vue', '{"title":"模型设置"}', 1),
(106, 10, '知识图谱', '/ai/knowledge-graph', 'AIKnowledgeGraph', NULL, 'Connection', 'MENU', 1, 1, 6, 'views/ai/KnowledgeGraph.vue', '{"title":"知识图谱"}', 1),
(107, 10, 'AI 简报', '/ai/briefings', 'AIBriefings', NULL, 'Bell', 'MENU', 1, 1, 7, 'views/ai/BriefingManage.vue', '{"title":"AI 简报"}', 1),
(108, 10, '工作流编排', '/ai/agents/:id/graph', 'AIAgentGraphEditor', NULL, NULL, 'PAGE', 0, 1, 8, 'views/ai/AgentGraphEditor.vue', NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    route_name = EXCLUDED.route_name,
    redirect = EXCLUDED.redirect,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    requires_auth = EXCLUDED.requires_auth,
    sort = EXCLUDED.sort,
    component = EXCLUDED.component,
    meta = EXCLUDED.meta,
    status = EXCLUDED.status;

UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 7;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id
FROM sys_menu m
WHERE m.id IN (10, 101, 102, 103, 104, 105, 106, 107, 108)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
