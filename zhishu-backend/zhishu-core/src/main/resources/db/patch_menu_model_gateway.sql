-- =============================================================================
-- 模型网关：菜单 IA 改造
--   1) 新增一级目录「模型网关」，归集模型相关二级菜单
--   2) 「模型设置」从「智能中心」迁入
--   3) 新增「网关模型 / 额度策略 / 调用用量 / 接入说明」四个页面与按钮权限
-- 幂等可重复执行
-- =============================================================================

-- 1) 一级目录 + 二级页面
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(12,   0,  '模型网关', '/gateway',        'ModelGateway',  '/gateway/models', 'Connection',
                'DIRECTORY', 1, 1, 3, NULL, NULL, 1),
(1201, 12, '模型设置', '/gateway/models', 'GatewayModels', NULL, 'Grid',
                'MENU', 1, 1, 2, 'views/gateway/GatewayModelList.vue',  '{"title":"模型设置"}', 1),
(1202, 12, '额度策略', '/gateway/quotas', 'GatewayQuotas', NULL, 'Histogram',
                'MENU', 1, 1, 3, 'views/gateway/GatewayQuotaPolicy.vue', '{"title":"额度策略"}', 1),
(1203, 12, '调用用量', '/gateway/usage',  'GatewayUsage',  NULL, 'DataLine',
                'MENU', 1, 1, 4, 'views/gateway/GatewayUsageList.vue',  '{"title":"调用用量"}', 1),
(1204, 12, '接入说明', '/gateway/guide',  'GatewayGuide',  NULL, 'Document',
                'MENU', 1, 1, 5, 'views/gateway/GatewayGuide.vue',      '{"title":"接入说明"}', 1)
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

-- 2) 「模型设置」就地改造为「供应商管理」（保留 id 与已有授权），模型目录页更名为「模型设置」
UPDATE sys_menu
SET title = '供应商管理',
    parent_id = 12,
    path = '/gateway/providers',
    component = 'views/gateway/GatewayProviderList.vue',
    sort = 1,
    update_time = CURRENT_TIMESTAMP
WHERE id = 105;

UPDATE sys_menu
SET title = '模型设置',
    path = '/gateway/models',
    component = 'views/gateway/GatewayModelList.vue',
    sort = 2,
    update_time = CURRENT_TIMESTAMP
WHERE id = 1201;

-- 3) 智能中心剩余子项重排（105 已移出）
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 110;
UPDATE sys_menu SET sort = 8, update_time = CURRENT_TIMESTAMP WHERE id = 108;

-- 4) 一级目录排序顺延：模型网关=3，账号管理=4，系统设置=5
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 5;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 6;

-- 5) 按钮权限
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(120101, 1201, '网关模型查询', NULL, 'gateway:model:list',   NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(120102, 1201, '网关模型维护', NULL, 'gateway:model:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(120201, 1202, '额度查询',     NULL, 'gateway:quota:list',   NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(120202, 1202, '额度维护',     NULL, 'gateway:quota:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(120301, 1203, '用量查询',     NULL, 'gateway:usage:query',  NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    title = EXCLUDED.title,
    route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type,
    sort = EXCLUDED.sort,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 6) ADMIN 授权（含迁入的 105）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id
FROM sys_menu m
WHERE m.id IN (12, 105, 1201, 1202, 1203, 1204, 120101, 120102, 120201, 120202, 120301)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
