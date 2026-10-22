-- =============================================================================
-- 模型网关 v2：供应商与模型设置拆分
--   1) ai_gateway_model 增加 model_type 与生成参数
--   2) 默认模型改为「按类型各自唯一」
--   3) 供应商旧列（chat_model/embedding_model/参数）下沉到模型行
--   4) 旧列保留一版（DEPRECATED），仅放开 NOT NULL，便于回滚
-- 幂等可重复执行。前置检查：is_default = TRUE 的供应商只能有一条。
-- =============================================================================

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

-- 3) 供应商旧列放开 NOT NULL（新供应商不再要求对话模型与生成参数）
ALTER TABLE ai_model_provider ALTER COLUMN chat_model  DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN temperature DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN max_tokens  DROP NOT NULL;
ALTER TABLE ai_model_provider ALTER COLUMN top_p       DROP NOT NULL;

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
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model, model_type,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT 'gw-' || lower(regexp_replace(p.provider_key,    '[^a-zA-Z0-9]+', '-', 'g'))
             || '-' || lower(regexp_replace(p.embedding_model, '[^a-zA-Z0-9.]+', '-', 'g'))
             || '-emb',
       p.name || ' · ' || p.embedding_model || '（向量）',
       p.id, p.embedding_model, 'EMBEDDING',
       p.is_default, 'ENABLED', p.sort_order, '由供应商 embedding_model 迁移生成',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.embedding_model IS NOT NULL
  AND btrim(p.embedding_model) <> ''
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
