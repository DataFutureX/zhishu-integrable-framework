-- =============================================================================
-- 业务管理面按钮权限码补齐（ai:agent:* / ai:document:* / ai:category:edit /
-- ai:mcp:edit / system:openapi:edit）
--
-- 背景：静态扫描发现 AgentController(11)、McpHubController(9)、
--       OpenAppAdminController(7)、DocumentController(4)、KnowledgesCategoryController(3)
--       共 34 个写端点没有任何 @PreAuthorize；而 PermissionConstants 里早已声明的
--       ai:agent:add / ai:mcp:edit 等常量在 sys_menu 中根本没有对应 BUTTON 行
--       —— 意味着「加注解」与「建权限码」必须同批落地，否则非 ADMIN 全部 403。
--
-- 语义：按钮的可见性跟随父菜单 —— 已拥有父菜单的角色自动获得其下新按钮，
--       因此本次授权不会扩大任何人的既有权限边界。
--       （ADMIN 角色由 PermissionServiceImpl 直接取全部 BUTTON，无需授权行）
--
-- 幂等：按 route_name 判重；授权按 (role_id, menu_id) 判重。可重复执行。
-- 执行：psql -f db/patch_menu_biz_permissions.sql   或 postgres MCP execute_raw_query
-- =============================================================================

-- 1) 插入按钮权限码（menu_type=BUTTON、visible=0、requires_auth=1）
INSERT INTO sys_menu (id, parent_id, title, route_name, menu_type, visible, requires_auth, sort, status, create_time, update_time)
SELECT v.id, v.parent_id, v.title, v.route_name, 'BUTTON', 0, 1, v.sort, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    -- Agents（父菜单 101）
    (10111, 101, '智能体查询',   'ai:agent:query',      1),
    (10112, 101, '智能体新增',   'ai:agent:add',        2),
    (10113, 101, '智能体修改',   'ai:agent:edit',       3),
    (10114, 101, '智能体删除',   'ai:agent:remove',     4),
    (10115, 101, '工作流编辑',   'ai:agent:graph',      5),
    -- 知识库（父菜单 102）
    (10211, 102, '文档上传',     'ai:document:add',     1),
    (10212, 102, '文档编辑',     'ai:document:edit',    2),
    (10213, 102, '文档删除',     'ai:document:remove',  3),
    (10214, 102, '分类维护',     'ai:category:edit',    4),
    -- MCP Hub（父菜单 104）
    (10411, 104, 'MCP 配置维护', 'ai:mcp:edit',         1),
    -- 开放能力（父菜单 610）
    (61011, 610, '开放应用维护', 'system:openapi:edit', 1)
) AS v(id, parent_id, title, route_name, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.menu_type = 'BUTTON' AND m.route_name = v.route_name
)
AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = v.id);

-- 2) 授权：已拥有父菜单的角色自动获得其下这批按钮
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT base.max_id + row_number() OVER (ORDER BY cand.role_id, cand.menu_id), cand.role_id, cand.menu_id
FROM (
    SELECT DISTINCT rm.role_id, m.id AS menu_id
    FROM sys_menu m
    JOIN sys_role_menu rm ON rm.menu_id = m.parent_id
    WHERE m.menu_type = 'BUTTON'
      AND m.route_name IN ('ai:agent:query', 'ai:agent:add', 'ai:agent:edit', 'ai:agent:remove', 'ai:agent:graph',
                           'ai:document:add', 'ai:document:edit', 'ai:document:remove', 'ai:category:edit',
                           'ai:mcp:edit', 'system:openapi:edit')
      AND NOT EXISTS (
          SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
      )
) cand
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_role_menu) base;

-- 3) 验证：应看到 11 个按钮 + 每角色对应的授权行
SELECT m.id, m.parent_id, m.route_name, m.title,
       (SELECT count(*) FROM sys_role_menu rm WHERE rm.menu_id = m.id) AS granted_roles
FROM sys_menu m
WHERE m.menu_type = 'BUTTON'
  AND m.route_name LIKE ANY (ARRAY['ai:agent:%', 'ai:document:%', 'ai:category:%', 'ai:mcp:%', 'system:openapi:%'])
ORDER BY m.parent_id, m.id;

-- 4) 对照：代码里的常量与库中的权限码是否有缺漏
--    （PermissionConstants 新增常量后，必须在此脚本或同类补丁里出现对应 BUTTON 行）
SELECT v.route_name AS code_in_script,
       (m.id IS NOT NULL) AS exists_in_db
FROM (VALUES
    ('ai:agent:query'), ('ai:agent:add'), ('ai:agent:edit'), ('ai:agent:remove'), ('ai:agent:graph'),
    ('ai:document:add'), ('ai:document:edit'), ('ai:document:remove'), ('ai:category:edit'),
    ('ai:mcp:edit'), ('system:openapi:edit')
) v(route_name)
LEFT JOIN sys_menu m ON m.menu_type = 'BUTTON' AND m.route_name = v.route_name
WHERE m.id IS NULL;
