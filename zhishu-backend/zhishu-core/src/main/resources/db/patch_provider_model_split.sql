-- =============================================================================
-- 模型网关 v2：供应商与模型设置拆分（**仅手工执行，且只适用于仍持有历史列的库**）
--   1) ai_gateway_model 增加 model_type 与生成参数
--   2) 默认模型改为「按类型各自唯一」
--   3) 供应商旧列（chat_model/embedding_model/参数）下沉到模型行
--   4) 旧列置 NULL（原值快照到 ai_model_provider_legacy_backup）
-- 启动器**不再自动执行本脚本**（结构对齐改用非破坏性的 db/gateway_schema.sql）。
-- 已执行过 db/patch_drop_model_legacy.sql 的库也不需再跑（历史列已不存在）。
-- 前置条件：is_default = TRUE 的供应商至多一条。
-- 幂等可重复执行（在未清理的库上）。
-- ============================================================================

-- 0) 前置检查（应返回 <= 1；否则下方 partial unique 索引会建失败）
-- SELECT count(*) FROM ai_model_provider WHERE is_default = TRUE;

-- 1) 模型表新增字段
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS model_type  VARCHAR(16) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS temperature NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS top_p       NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS max_tokens  INT;

-- 2) 默认模型按类型各自唯一
DROP INDEX IF EXISTS uk_ai_gateway_model_default;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_chat
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'CHAT';
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_embedding
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'EMBEDDING';

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_type
    ON ai_gateway_model (model_type, status, sort_order);

-- 3) 供应商旧列放开 NOT NULL 并去掉默认值（新供应商不再要求模型名与生成参数，
--    否则 MyBatis-Plus 略过 null 字段时会被 DB 默认值重新写回参数）
ALTER TABLE ai_model_provider ALTER COLUMN chat_model  DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN temperature DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN max_tokens  DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN top_p       DROP NOT NULL;

ALTER TABLE ai_model_provider ALTER COLUMN temperature DROP DEFAULT;
ALTER TABLE ai_model_provider ALTER COLUMN max_tokens  DROP DEFAULT;
ALTER TABLE ai_model_provider ALTER COLUMN top_p       DROP DEFAULT;

COMMENT ON COLUMN ai_model_provider.chat_model      IS 'DEPRECATED：已下沉到 ai_gateway_model.upstream_model';
COMMENT ON COLUMN ai_model_provider.embedding_model IS 'DEPRECATED：已迁移到 model_type=EMBEDDING 的模型行';
COMMENT ON COLUMN ai_model_provider.temperature     IS 'DEPRECATED：已下沉到 ai_gateway_model.temperature';
COMMENT ON COLUMN ai_model_provider.max_tokens      IS 'DEPRECATED：已下沉到 ai_gateway_model.max_tokens';
COMMENT ON COLUMN ai_model_provider.top_p           IS 'DEPRECATED：已下沉到 ai_gateway_model.top_p';

-- 4) 把供应商上的生成参数下沉到 CHAT 模型行（不覆盖运维在模型页改过的值）
UPDATE ai_gateway_model m
SET temperature = COALESCE(m.temperature, p.temperature),
    top_p       = COALESCE(m.top_p, p.top_p),
    max_tokens  = COALESCE(m.max_tokens, p.max_tokens)
FROM ai_model_provider p
WHERE m.provider_id = p.id
  AND m.model_type = 'CHAT';

-- 5) 每个供应商的 embedding_model 生成一条 EMBEDDING 模型行
--    去重按「供应商 + 类型 + 上游模型名」（model_id 因 provider_key 改名可能不同，不能只靠 ON CONFLICT）
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model, model_type,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT 'gw-' || lower(regexp_replace(p.provider_key,    '[^a-zA-Z0-9]+', '-', 'g'))
             || '-' || lower(regexp_replace(p.embedding_model, '[^a-zA-Z0-9.]+', '-', 'g'))
             || '-emb',
       p.name || ' · ' || p.embedding_model || '（向量）',
       p.id, p.embedding_model, 'EMBEDDING',
       p.is_default AND NOT EXISTS (
           SELECT 1 FROM ai_gateway_model d
            WHERE d.model_type = 'EMBEDDING' AND d.is_default = TRUE),
       'ENABLED', p.sort_order, '由供应商 embedding_model 迁移生成',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.embedding_model IS NOT NULL
  AND btrim(p.embedding_model) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM ai_gateway_model e
         WHERE e.provider_id = p.id
           AND e.model_type = 'EMBEDDING'
           AND e.upstream_model = p.embedding_model)
ON CONFLICT (model_id) DO NOTHING;

-- 6) 若还没有 EMBEDDING 默认行，把第一条可用向量模型置为默认
UPDATE ai_gateway_model
SET is_default = TRUE,
    update_time = CURRENT_TIMESTAMP
WHERE id = (
        SELECT id FROM ai_gateway_model
         WHERE model_type = 'EMBEDDING' AND status = 'ENABLED'
         ORDER BY sort_order, id
         LIMIT 1)
  AND NOT EXISTS (
        SELECT 1 FROM ai_gateway_model
         WHERE model_type = 'EMBEDDING' AND is_default = TRUE);

-- 7) 若某供应商还没有任何 CHAT 模型行，用旧 chat_model 与生成参数补一条（首次安装也能直接可用）
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model, model_type,
    temperature, top_p, max_tokens, is_default, status, sort_order, remark, create_time, update_time)
SELECT 'gw-' || lower(regexp_replace(p.provider_key, '[^a-zA-Z0-9]+', '-', 'g'))
             || '-' || lower(regexp_replace(p.chat_model,    '[^a-zA-Z0-9.]+', '-', 'g')),
       p.name || ' · ' || p.chat_model,
       p.id, p.chat_model, 'CHAT',
       p.temperature, p.top_p, p.max_tokens,
       p.is_default AND NOT EXISTS (
           SELECT 1 FROM ai_gateway_model d WHERE d.model_type = 'CHAT' AND d.is_default = TRUE),
       'ENABLED', p.sort_order, '由供应商 chat_model 迁移生成',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.chat_model IS NOT NULL
  AND btrim(p.chat_model) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM ai_gateway_model m
         WHERE m.provider_id = p.id AND m.model_type = 'CHAT')
ON CONFLICT (model_id) DO NOTHING;

-- 8) 若还没有 CHAT 默认行，把第一条可用对话模型置为默认
UPDATE ai_gateway_model
SET is_default = TRUE,
    update_time = CURRENT_TIMESTAMP
WHERE id = (
        SELECT id FROM ai_gateway_model
         WHERE model_type = 'CHAT' AND status = 'ENABLED'
         ORDER BY sort_order, id
         LIMIT 1)
  AND NOT EXISTS (
        SELECT 1 FROM ai_gateway_model
         WHERE model_type = 'CHAT' AND is_default = TRUE);

-- 9) 历史列快照（只存一次，便于整体回滚）
CREATE TABLE IF NOT EXISTS ai_model_provider_legacy_backup (
    id              BIGINT PRIMARY KEY,
    chat_model      VARCHAR(64),
    embedding_model VARCHAR(64),
    temperature     NUMERIC(3,2),
    max_tokens      INT,
    top_p           NUMERIC(3,2),
    backup_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ai_model_provider_legacy_backup
    (id, chat_model, embedding_model, temperature, max_tokens, top_p)
SELECT id, chat_model, embedding_model, temperature, max_tokens, top_p
FROM ai_model_provider
WHERE chat_model IS NOT NULL OR embedding_model IS NOT NULL
   OR temperature IS NOT NULL OR max_tokens IS NOT NULL OR top_p IS NOT NULL
ON CONFLICT (id) DO NOTHING;

-- 10) 清空供应商上的模型字段：供应商表不再保留任何模型/参数数据
--     （已迁移完成；被删除的模型不会因重跑而复活，因为旧列已 NULL）
UPDATE ai_model_provider
SET chat_model      = NULL,
    embedding_model = NULL,
    temperature     = NULL,
    max_tokens      = NULL,
    top_p           = NULL
WHERE chat_model IS NOT NULL OR embedding_model IS NOT NULL
   OR temperature IS NOT NULL OR max_tokens IS NOT NULL OR top_p IS NOT NULL;

-- 验证：每个供应商的模型行与默认行
-- SELECT provider_id, model_type, count(*), bool_or(is_default) FROM ai_gateway_model GROUP BY 1, 2 ORDER BY 1, 2;
-- SELECT count(*) FROM ai_model_provider WHERE chat_model IS NOT NULL OR embedding_model IS NOT NULL;  -- 应为 0
-- 查重（同一供应商+类型+上游模型名只应一行）：
-- SELECT provider_id, model_type, upstream_model, count(*) FROM ai_gateway_model
--  GROUP BY 1, 2, 3 HAVING count(*) > 1;
-- 确无重复后，可手工建唯一约束彻底防重（服务层已同步拦截）：
-- CREATE UNIQUE INDEX uk_ai_gateway_model_provider_upstream
--     ON ai_gateway_model (provider_id, model_type, upstream_model);
