-- 将「Agent 会话」从 AI 管理挪到主页下
UPDATE sys_menu
SET parent_id = 1,
    path = '/home/chat',
    sort = 2,
    update_time = CURRENT_TIMESTAMP
WHERE id = 103;

-- AI 管理下剩余菜单重新排序
UPDATE sys_menu SET sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 104;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 105;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 106;
UPDATE sys_menu SET sort = 6, update_time = CURRENT_TIMESTAMP WHERE id = 107;
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 108;
