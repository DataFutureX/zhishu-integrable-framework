-- 已有库补丁：知枢智能中心菜单并授权给 ADMIN
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_ai_menus.sql

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(10, 0, '智能中心', '/ai', 'AI', '/ai/qa', 'MagicStick', 'DIRECTORY', 1, 1, 2, NULL, NULL, 1),
(109, 10, '知识检索', '/ai/qa', 'AIDocumentQA', NULL, 'Search', 'MENU', 1, 1, 1, 'views/ai/DocumentQA.vue', '{"title":"知识检索"}', 1),
(106, 10, '知识图谱', '/ai/knowledge-graph', 'AIKnowledgeGraph', NULL, 'Connection', 'MENU', 1, 1, 2, 'views/ai/KnowledgeGraph.vue', '{"title":"知识图谱"}', 1),
(101, 10, 'Agents', '/ai/agents', 'AIAgentManage', NULL, 'Cpu', 'MENU', 1, 1, 3, 'views/ai/AgentManage.vue', '{"title":"Agents 管理"}', 1),
(102, 10, '知识库', '/ai/knowledges', 'AIDocumentManage', NULL, 'FolderOpened', 'MENU', 1, 1, 4, 'views/ai/DocumentManage.vue', '{"title":"知识库"}', 1),
(104, 10, 'MCP Hub', '/ai/mcp', 'AIMcpHub', NULL, 'Link', 'MENU', 1, 1, 5, 'views/ai/McpHub.vue', '{"title":"MCP Hub"}', 1),
(105, 10, '模型设置', '/ai/model-config', 'AIModelConfig', NULL, 'SetUp', 'MENU', 1, 1, 6, 'views/ai/ModelConfig.vue', '{"title":"模型设置"}', 1),
(108, 10, '工作流编排', '/ai/agents/:id/graph', 'AIAgentGraphEditor', NULL, NULL, 'PAGE', 0, 1, 7, 'views/ai/AgentGraphEditor.vue', NULL, 1)
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

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(107, 1, 'AI 简报', '/home/briefings', 'AIBriefings', NULL, 'Bell', 'MENU', 1, 1, 1, 'views/ai/BriefingManage.vue', '{"title":"AI 简报"}', 1),
(103, 1, 'Agent 会话', '/home/chat', 'AIChat', NULL, 'ChatLineRound', 'MENU', 1, 1, 2, 'views/ai/AIChat.vue', '{"title":"Agent 会话"}', 1)
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

UPDATE sys_menu SET title = '工作台', sort = 1, update_time = CURRENT_TIMESTAMP WHERE id = 1;
UPDATE sys_menu SET sort = 9, update_time = CURRENT_TIMESTAMP WHERE id = 7;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id
FROM sys_menu m
WHERE m.id IN (10, 101, 102, 103, 104, 105, 106, 107, 108, 109)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status) VALUES
(10101, 101, '智能体查询', NULL, 'ai:agent:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10102, 101, '智能体新增', NULL, 'ai:agent:add', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(10103, 101, '智能体修改', NULL, 'ai:agent:edit', NULL, NULL, 'BUTTON', 0, 1, 3, NULL, NULL, 1),
(10104, 101, '智能体删除', NULL, 'ai:agent:remove', NULL, NULL, 'BUTTON', 0, 1, 4, NULL, NULL, 1),
(10105, 101, '工作流编排', NULL, 'ai:agent:graph', NULL, NULL, 'BUTTON', 0, 1, 5, NULL, NULL, 1),
(10401, 104, 'MCP 编辑', NULL, 'ai:mcp:edit', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10601, 106, '知识图谱同步', NULL, 'ai:kg:sync', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(10901, 109, '知识检索查询', NULL, 'ai:qa:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    requires_auth = EXCLUDED.requires_auth,
    sort = EXCLUDED.sort,
    status = EXCLUDED.status;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 100000 + m.id, 1, m.id
FROM sys_menu m
WHERE m.id IN (10101, 10102, 10103, 10104, 10105, 10401, 10601, 10901)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
