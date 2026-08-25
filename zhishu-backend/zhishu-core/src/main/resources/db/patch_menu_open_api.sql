-- 新增「开放能力」菜单（挂在「系统设置」id=6 下）
-- 展示系统对外开放 API 能力与他方接入说明

INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type, visible, requires_auth, sort, component, meta, status)
VALUES (610, 6, '开放能力', '/system/open-api', 'OpenApiCapabilities', NULL, 'Connection', 'MENU', 1, 1, 7, 'views/system/OpenApiCapabilities.vue', '{"title":"开放能力"}', 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    path = EXCLUDED.path,
    route_name = EXCLUDED.route_name,
    icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type,
    visible = EXCLUDED.visible,
    requires_auth = EXCLUDED.requires_auth,
    sort = EXCLUDED.sort,
    component = EXCLUDED.component,
    meta = EXCLUDED.meta,
    status = EXCLUDED.status;

-- 管理员角色自动获得新菜单权限
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id FROM sys_menu m WHERE m.id = 610
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
