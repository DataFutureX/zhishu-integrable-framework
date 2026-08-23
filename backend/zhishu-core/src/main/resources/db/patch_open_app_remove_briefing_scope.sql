-- 已有库补丁：简报改为知枢内部模块，开放应用去掉 briefings scope
-- 外部简报请调用 POST /open/v1/chat（chat 权限）

UPDATE open_app
SET allowed_scopes = COALESCE((
        SELECT jsonb_agg(elem)::text
        FROM jsonb_array_elements_text(allowed_scopes::jsonb) AS elem
        WHERE elem <> 'briefings'
    ), '[]'),
    update_time = CURRENT_TIMESTAMP
WHERE allowed_scopes LIKE '%briefings%';
