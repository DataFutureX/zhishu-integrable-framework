-- 已有库补丁：与万象 sys_user 对齐（一期无 JIT，须预开通）
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_align_wanxiang_users.sql

INSERT INTO sys_user (id, username, real_name, password, role, status)
VALUES
    (2072989840212291586, 'user', '演示用户', '$2a$10$Nd6ylX9HCrdR0eIvWqDpiu40ijhG2BCswxRoJNIcnqKhjC2Ymjuo2', 'USER', 0),
    (2084807635623264257, 'u123', 'u123', '$2a$10$8xL1K2a1AJn4pZ/O2waCz.oRlcl6znCP8HNLej4f2MywXlfK6P43.', 'USER', 1)
ON CONFLICT (username) DO UPDATE SET
    real_name = EXCLUDED.real_name,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;
