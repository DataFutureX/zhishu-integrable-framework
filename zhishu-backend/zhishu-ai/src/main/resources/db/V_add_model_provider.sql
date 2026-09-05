-- ============================================================
-- 多模型设置：新增 ai_model_provider 表，替代原 ai_model_config 单例
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_model_provider (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    provider_key    VARCHAR(64)  NOT NULL UNIQUE,
    base_url        VARCHAR(512) NOT NULL,
    api_key_enc     TEXT,
    api_key_masked  VARCHAR(128),
    chat_model      VARCHAR(64)  NOT NULL,
    embedding_model VARCHAR(64),
    temperature     NUMERIC(3,2) NOT NULL DEFAULT 0.70,
    max_tokens      INT          NOT NULL DEFAULT 2000,
    top_p           NUMERIC(3,2) NOT NULL DEFAULT 0.90,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    sort_order      INT          NOT NULL DEFAULT 0,
    remark          VARCHAR(500),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_model_provider                 IS '模型设置（多供应商）';
COMMENT ON COLUMN ai_model_provider.name            IS '展示名称（如"通义千问"、"DeepSeek"）';
COMMENT ON COLUMN ai_model_provider.provider_key    IS '程序标识（如 dashscope / deepseek / openai）';
COMMENT ON COLUMN ai_model_provider.base_url        IS 'OpenAI 兼容 Base URL';
COMMENT ON COLUMN ai_model_provider.api_key_enc     IS 'AES-GCM 加密 API Key';
COMMENT ON COLUMN ai_model_provider.api_key_masked  IS 'API Key 脱敏展示';
COMMENT ON COLUMN ai_model_provider.chat_model      IS '对话模型名';
COMMENT ON COLUMN ai_model_provider.embedding_model IS '向量模型名（仅默认模型设置有效）';
COMMENT ON COLUMN ai_model_provider.temperature     IS '温度 0~2';
COMMENT ON COLUMN ai_model_provider.max_tokens      IS '最大 Token';
COMMENT ON COLUMN ai_model_provider.top_p           IS 'Top P 0~1';
COMMENT ON COLUMN ai_model_provider.is_default      IS '是否为默认模型设置';
COMMENT ON COLUMN ai_model_provider.status          IS 'ENABLED / DISABLED';

-- Agent 绑定模型设置
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS model_provider_id BIGINT;
COMMENT ON COLUMN ai_agent.model_provider_id IS '绑定的模型设置 ID，空=使用默认';
