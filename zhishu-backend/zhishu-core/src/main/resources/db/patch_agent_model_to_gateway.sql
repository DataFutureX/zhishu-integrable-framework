-- =============================================================================
-- 模型网关：把「模型设置 + Agent 绑定模型」迁移为网关模型目录
--   1) 每个启用的 provider 生成一条目录行（gw-<provider>-<model>）
--   2) Agent 覆盖了非默认模型名的组合补建目录行
--   3) 回填 ai_agent.model 为网关 model_id
--   4) 弃用 ai_agent.model_provider_id（保留列不删）
--   5) 建服务账号 + 缺省额度兜底
-- 幂等可重复执行
-- =============================================================================

-- 1) 每个启用的 provider 生成一条目录行
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model,
    context_window, max_output_tokens, input_modalities, system_prompt_update,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT
    'gw-' || lower(regexp_replace(p.provider_key, '[^a-zA-Z0-9]+', '-', 'g'))
          || '-' || lower(regexp_replace(p.chat_model,  '[^a-zA-Z0-9.]+', '-', 'g')),
    p.name || ' · ' || p.chat_model,
    p.id, p.chat_model,
    32768, 4096, 'text', 'in-history',
    p.is_default, 'ENABLED', p.sort_order, '由模型设置迁移生成',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.status = 'ENABLED'
ON CONFLICT (model_id) DO NOTHING;

-- 2) Agent 覆盖了非默认模型名的组合，补建目录行
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model,
    context_window, max_output_tokens, input_modalities, system_prompt_update,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT DISTINCT
    'gw-' || lower(regexp_replace(p.provider_key, '[^a-zA-Z0-9]+', '-', 'g'))
          || '-' || lower(regexp_replace(a.model,        '[^a-zA-Z0-9.]+', '-', 'g')),
    p.name || ' · ' || a.model,
    p.id, a.model,
    32768, 4096, 'text', 'in-history',
    FALSE, 'ENABLED', p.sort_order, '由 Agent 绑定迁移生成',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_agent a
JOIN ai_model_provider p ON p.id = a.model_provider_id
WHERE a.model IS NOT NULL
  AND btrim(a.model) <> ''
  AND a.model <> p.chat_model
  AND p.status = 'ENABLED'
ON CONFLICT (model_id) DO NOTHING;

-- 3) 回填 ai_agent.model 为网关 model_id（必须在 1、2 之后执行）
UPDATE ai_agent a
SET model = m.model_id,
    update_time = CURRENT_TIMESTAMP
FROM ai_model_provider p
JOIN ai_gateway_model m ON m.provider_id = p.id
WHERE a.model_provider_id = p.id
  AND m.upstream_model = COALESCE(NULLIF(btrim(a.model), ''), p.chat_model);

-- 4) 弃用 provider 绑定（保留列，不再参与路由）
UPDATE ai_agent
SET model_provider_id = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE model_provider_id IS NOT NULL;

-- 5) 服务账号（内部无用户上下文的调用主体，密码置为不可用字面量）
INSERT INTO sys_user (id, username, real_name, password, role, status, create_time, update_time)
VALUES (9000000000000000001, 'gateway-service', '模型网关服务账号', 'disabled', 'SERVICE', 1,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- 6) 缺省额度兜底
INSERT INTO ai_gateway_quota (subject_type, subject_key, period_kind, token_limit, used_tokens,
                              period_start, period_end, status, remark)
VALUES ('DEFAULT', '*', 'MONTHLY', 5000000, 0,
        date_trunc('month', CURRENT_TIMESTAMP),
        date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '1 month',
        'ENABLED', '缺省月额度')
ON CONFLICT (subject_type, subject_key) DO NOTHING;
