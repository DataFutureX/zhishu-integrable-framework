-- 信息架构调整：工作台 / 智能中心 / 账号管理 / 系统设置
-- 个人中心保留路由，侧栏由前端排除；普通用户仅开放使用侧 AI 能力

-- 一级目录命名与排序
UPDATE sys_menu SET title = '工作台', sort = 1, update_time = CURRENT_TIMESTAMP WHERE id = 1;
UPDATE sys_menu SET title = '智能中心', path = '/ai', redirect = '/ai/qa', sort = 2, update_time = CURRENT_TIMESTAMP WHERE id = 10;
UPDATE sys_menu SET title = '账号管理', sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 5;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 6;
UPDATE sys_menu SET sort = 9, update_time = CURRENT_TIMESTAMP WHERE id = 7;

-- 知识检索（DocumentQA）
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status)
VALUES (109, 10, '知识检索', '/ai/qa', 'AIDocumentQA', NULL, 'Search', 'MENU', 1, 1, 1, 'views/ai/DocumentQA.vue', '{"title":"知识检索"}', 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    route_name = EXCLUDED.route_name,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    sort = EXCLUDED.sort,
    component = EXCLUDED.component,
    meta = EXCLUDED.meta,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 工作台：AI 简报为首个子菜单，仪表盘隐藏
UPDATE sys_menu SET redirect = '/home/briefings', update_time = CURRENT_TIMESTAMP WHERE id = 1;
UPDATE sys_menu SET visible = 0, sort = 9, update_time = CURRENT_TIMESTAMP WHERE id = 11;
UPDATE sys_menu
SET parent_id = 1,
    path = '/home/briefings',
    sort = 1,
    visible = 1,
    update_time = CURRENT_TIMESTAMP
WHERE id = 107;

-- 智能中心：使用侧在前，运营侧在后
UPDATE sys_menu SET sort = 2, update_time = CURRENT_TIMESTAMP WHERE id = 106;
UPDATE sys_menu SET sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 101;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 102;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 104;
UPDATE sys_menu SET sort = 6, update_time = CURRENT_TIMESTAMP WHERE id = 105;
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 108;

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status)
VALUES (10901, 109, '知识检索查询', NULL, 'ai:qa:query', NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    sort = EXCLUDED.sort;

-- ADMIN 补授权
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id FROM sys_menu m WHERE m.id IN (109)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 100000 + m.id, 1, m.id FROM sys_menu m WHERE m.id IN (10901)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;

-- 普通用户：工作台 + 智能中心使用侧 + 个人中心
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(2103, 2, 103),
(2010, 2, 10),
(2109, 2, 109),
(2107, 2, 107),
(2106, 2, 106),
(210901, 2, 10901)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
