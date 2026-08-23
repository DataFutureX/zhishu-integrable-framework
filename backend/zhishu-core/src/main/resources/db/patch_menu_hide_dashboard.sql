-- 隐藏工作台「仪表盘」，AI 简报作为第一个子菜单
UPDATE sys_menu
SET redirect = '/home/briefings',
    update_time = CURRENT_TIMESTAMP
WHERE id = 1;

UPDATE sys_menu
SET visible = 0,
    sort = 9,
    update_time = CURRENT_TIMESTAMP
WHERE id = 11;

UPDATE sys_menu
SET parent_id = 1,
    path = '/home/briefings',
    sort = 1,
    visible = 1,
    update_time = CURRENT_TIMESTAMP
WHERE id = 107;

UPDATE sys_menu
SET parent_id = 1,
    path = '/home/chat',
    sort = 2,
    update_time = CURRENT_TIMESTAMP
WHERE id = 103;
