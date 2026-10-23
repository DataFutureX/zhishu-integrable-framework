-- =============================================================================
-- 模型网关结构对齐（启动器每次启动幂等执行，仅做非破坏性变更）
--   ai_gateway_model 的 v2 列与索引；不读写 ai_model_provider 的历史列。
-- 历史库的数据下沉与清理请手工执行：
--   1) db/patch_provider_model_split.sql   （把供应商历史列下沉为模型行 + 快照）
--   2) db/patch_drop_model_legacy.sql      （删除历史列与备份/退役表，不可回滚）
-- =============================================================================

ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS model_type  VARCHAR(16) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS temperature NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS top_p       NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS max_tokens  INT;

-- 默认模型按类型各自唯一
DROP INDEX IF EXISTS uk_ai_gateway_model_default;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_chat
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'CHAT';
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_embedding
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'EMBEDDING';

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_type
    ON ai_gateway_model (model_type, status, sort_order);
