-- =============================================================================
-- 退役 ai_model_config 单例配置表
--   1) sys_config 增加 chat_enable_rag_default（「对话默认开启知识库 RAG」开关）
--   2) 迁移旧值：ai_model_config.enable_rag_default → sys_config
--   3) 将旧表重命名为 ai_model_config_retired（保留一个版本供人工取回旧 Key）
-- 幂等可重复执行。执行前请确认已跑过 patch_provider_model_split.sql
-- （供应商旧列上的模型/参数已下沉为 ai_gateway_model 模型行）。
--
-- 说明：旧表的 api_key_enc 不在应用侧再读，且本脚本不拷贝到供应商表（避免把 Key 写错供应商），
--       需要时由管理员在「供应商管理」里手工录入 Key，确认无误后手动 DROP：
--       DROP TABLE IF EXISTS ai_model_config_retired;
-- =============================================================================

ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS chat_enable_rag_default BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN sys_config.chat_enable_rag_default IS '对话默认开启知识库 RAG（原 ai_model_config.enable_rag_default）';

-- 仅当旧表仍在时迁移并重命名
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ai_model_config') THEN
        UPDATE sys_config s
        SET chat_enable_rag_default = m.enable_rag_default
        FROM ai_model_config m
        WHERE m.id = 1 AND s.id = 1
          AND m.enable_rag_default IS NOT NULL;

        ALTER TABLE ai_model_config RENAME TO ai_model_config_retired;
        RAISE NOTICE '已迁移 enable_rag_default，并将 ai_model_config 重命名为 ai_model_config_retired';
    ELSE
        RAISE NOTICE 'ai_model_config 不存在，跳过迁移';
    END IF;
END $$;

-- 验证
-- SELECT id, chat_enable_rag_default FROM sys_config WHERE id = 1;
-- SELECT count(*) FROM information_schema.tables WHERE table_name = 'ai_model_config';  -- 应为 0
-- 确认旧 Key 已取回后：DROP TABLE IF EXISTS ai_model_config_retired;
