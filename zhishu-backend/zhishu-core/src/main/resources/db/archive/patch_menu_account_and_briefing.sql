-- 已有库补丁：权限管理 → 账号管理；AI 简报挪到工作台
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_menu_account_and_briefing.sql

UPDATE sys_menu
SET title = '账号管理',
    update_time = CURRENT_TIMESTAMP
WHERE id = 5;

UPDATE sys_menu
SET parent_id = 1,
    path = '/home/briefings',
    sort = 1,
    visible = 1,
    update_time = CURRENT_TIMESTAMP
WHERE id = 107;

UPDATE sys_menu SET sort = 2, update_time = CURRENT_TIMESTAMP WHERE id = 106;
UPDATE sys_menu SET sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 101;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 102;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 104;
UPDATE sys_menu SET sort = 6, update_time = CURRENT_TIMESTAMP WHERE id = 105;
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 108;
