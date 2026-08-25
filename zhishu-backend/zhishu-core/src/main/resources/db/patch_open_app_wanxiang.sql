-- 已有库补丁：注册万象开放应用（开发凭证）
-- 明文：zsopen_dev_wanxiang_2026_local （仅本地开发）
-- psql -d zhishu_integrable_framework -f patch_open_app_wanxiang.sql

INSERT INTO open_app (code, name, status, allowed_scopes, remark, created_by)
VALUES (
    'wanxiang-monitor',
    '万象监测平台',
    'ENABLED',
    '["chat","knowledges","kg"]',
    '万象 BFF 代调知枢开放 API',
    'system'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO open_app_credential (app_id, key_prefix, secret_hash, status, created_by)
SELECT a.id,
       'zsopen_dev_wan',
       '834c7fbd53c42e4ff5973a6e33da06dda740734d90faa722d7765c7a3b6c944a',
       'ENABLED',
       'system'
FROM open_app a
WHERE a.code = 'wanxiang-monitor'
ON CONFLICT (key_prefix) DO NOTHING;

UPDATE open_app
SET allowed_scopes = '["chat","knowledges","kg"]',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'wanxiang-monitor';
