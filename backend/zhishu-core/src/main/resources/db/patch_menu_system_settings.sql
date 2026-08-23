-- 菜单结构调整：
-- 1) 「系统管理」更名为「系统设置」
-- 2) 「运维监控」「后端接口」归入「系统设置」
-- 3) 移除顶层「系统监控」「开发工具」目录
-- 4) 原「系统设置」子菜单更名为「参数配置」，避免与父级重名

UPDATE sys_menu
SET title = '系统设置',
    update_time = CURRENT_TIMESTAMP
WHERE id = 6;

UPDATE sys_menu
SET title = '参数配置',
    update_time = CURRENT_TIMESTAMP
WHERE id = 65;

UPDATE sys_menu
SET title = '参数配置查询',
    update_time = CURRENT_TIMESTAMP
WHERE id = 6501;

UPDATE sys_menu
SET title = '参数配置修改',
    update_time = CURRENT_TIMESTAMP
WHERE id = 6502;

UPDATE sys_menu
SET parent_id = 6,
    sort = 4,
    update_time = CURRENT_TIMESTAMP
WHERE id = 67;

UPDATE sys_menu
SET parent_id = 6,
    sort = 5,
    update_time = CURRENT_TIMESTAMP
WHERE id = 91;

UPDATE sys_menu
SET sort = 2,
    update_time = CURRENT_TIMESTAMP
WHERE id = 68;

UPDATE sys_menu
SET sort = 3,
    update_time = CURRENT_TIMESTAMP
WHERE id = 69;

UPDATE sys_menu
SET sort = 7,
    update_time = CURRENT_TIMESTAMP
WHERE id = 7 AND parent_id = 0;

DELETE FROM sys_role_menu WHERE menu_id IN (8, 9);

DELETE FROM sys_menu WHERE id IN (8, 9);
