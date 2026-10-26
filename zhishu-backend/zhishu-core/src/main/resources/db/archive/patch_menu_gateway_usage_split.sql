-- =============================================================================
-- 模型网关：原「调用用量」拆分为「调用台账 / 调用用量」
--   1) 1203 就地改造为「调用台账」：/gateway/ledger，展示逐条调用台账
--   2) 新增 1205「调用用量」：承接原 /gateway/usage，展示按模型/用户/天统计
--   3) 1204 接入说明 sort 顺延为 6
--   4) 权限行 120301（gateway:usage:query）保持挂在 1203 下，两页共用
-- 幂等可重复执行；已有库发版前必须先跑本脚本，否则侧栏缺失新页面
-- =============================================================================

-- 1) 台账（改造）+ 用量（新增）
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(1203, 12, '调用台账', '/gateway/ledger', 'GatewayUsageLedger', NULL, 'Tickets',
                'MENU', 1, 1, 4, 'views/gateway/GatewayUsageLedger.vue', '{"title":"调用台账"}', 1),
(1205, 12, '调用用量', '/gateway/usage',  'GatewayUsage',       NULL, 'DataLine',
                'MENU', 1, 1, 5, 'views/gateway/GatewayUsageStats.vue',  '{"title":"调用用量"}', 1)
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
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 2) 接入说明排在两个用量页之后
UPDATE sys_menu SET sort = 6, update_time = CURRENT_TIMESTAMP WHERE id = 1204;

-- 3) 权限行：两个页面共用 gateway:usage:query（已存在则只校正挂载点与文案）
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(120301, 1203, '用量查询', NULL, 'gateway:usage:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type,
    sort = EXCLUDED.sort,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 4) ADMIN 授权（目录 / 台账 / 接入说明 / 用量 / 权限行）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id
FROM sys_menu m
WHERE m.id IN (12, 1203, 1204, 1205, 120301)
ON CONFLICT DO NOTHING;
