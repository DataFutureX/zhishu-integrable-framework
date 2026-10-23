-- 已有库补丁：禁用工作台 / 仪表盘；Agent 会话迁入智能中心
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_menu_disable_home_move_chat.sql

UPDATE sys_menu
SET visible = 0,
    status = 0,
    redirect = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE sys_menu
SET visible = 0,
    status = 0,
    update_time = CURRENT_TIMESTAMP
WHERE id IN (11, 1101);

UPDATE sys_menu
SET parent_id = 10,
    path = '/ai/chat',
    sort = 1,
    visible = 1,
    status = 1,
    update_time = CURRENT_TIMESTAMP
WHERE id = 103;

UPDATE sys_menu
SET redirect = '/ai/chat',
    update_time = CURRENT_TIMESTAMP
WHERE id = 10;

UPDATE sys_menu SET sort = 2, update_time = CURRENT_TIMESTAMP WHERE id = 109;
UPDATE sys_menu SET sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 106;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 101;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 102;
UPDATE sys_menu SET sort = 6, update_time = CURRENT_TIMESTAMP WHERE id = 104;
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 105;
UPDATE sys_menu SET sort = 8, update_time = CURRENT_TIMESTAMP WHERE id = 108;

DELETE FROM sys_role_menu WHERE role_id = 2 AND menu_id IN (1, 11, 107, 1101);
