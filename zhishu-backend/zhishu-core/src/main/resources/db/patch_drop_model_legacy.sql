-- =============================================================================
-- 清理模型相关的废弃表与废弃列（网关接管后的收尾）
--   1) ai_model_provider：删除已下沉到 ai_gateway_model 的 5 个历史列
--   2) ai_agent：删除废弃的 model_provider_id 列
--   3) 删除迁移期的备份/退役表
--      ai_model_provider_legacy_backup、ai_model_config(_retired)
-- 幂等可重复执行；前置：已跑过 patch_provider_model_split.sql（历史值已下沉）
-- 与 patch_retire_ai_model_config.sql（RAG 默认开关已迁至 sys_config）。
--
-- ⚠ 不可回滚操作：执行前请确认
--   - 已核对 ai_gateway_model 的模型行与默认行齐全（SELECT provider_id, model_type, count(*) FROM ai_gateway_model GROUP BY 1,2;）
--   - 已从 ai_model_config_retired 取回需要的旧 API Key（在「供应商管理」重新录入）
-- =============================================================================

ALTER TABLE ai_model_provider DROP COLUMN IF EXISTS chat_model;
ALTER TABLE ai_model_provider DROP COLUMN IF EXISTS embedding_model;
ALTER TABLE ai_model_provider DROP COLUMN IF EXISTS temperature;
ALTER TABLE ai_model_provider DROP COLUMN IF EXISTS max_tokens;
ALTER TABLE ai_model_provider DROP COLUMN IF EXISTS top_p;

ALTER TABLE ai_agent DROP COLUMN IF EXISTS model_provider_id;

DROP TABLE IF EXISTS ai_model_provider_legacy_backup;
DROP TABLE IF EXISTS ai_model_config;
DROP TABLE IF EXISTS ai_model_config_retired;

-- 验证
-- SELECT column_name FROM information_schema.columns
--  WHERE table_name IN ('ai_model_provider', 'ai_agent')
--    AND column_name IN ('chat_model', 'embedding_model', 'temperature', 'max_tokens', 'top_p', 'model_provider_id');  -- 应为 0 行
-- SELECT table_name FROM information_schema.tables
--  WHERE table_name LIKE 'ai_model_config%' OR table_name = 'ai_model_provider_legacy_backup';  -- 应为 0 行
