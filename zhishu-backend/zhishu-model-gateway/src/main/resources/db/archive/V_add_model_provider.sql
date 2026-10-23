-- ============================================================
-- 供应商表：ai_model_provider（仅连接与凭据）
-- 模型名与生成参数一律住在 ai_gateway_model（模型行）；旧版本列由
-- db/patch_drop_model_legacy.sql 清理。
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_model_provider (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    provider_key    VARCHAR(64)  NOT NULL UNIQUE,
    base_url        VARCHAR(512) NOT NULL,
    api_key_enc     TEXT,
    api_key_masked  VARCHAR(128),
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    sort_order      INT          NOT NULL DEFAULT 0,
    remark          VARCHAR(500),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_model_provider                 IS '模型网关·供应商（仅连接与凭据）';
COMMENT ON COLUMN ai_model_provider.name            IS '展示名称（如"通义千问"、"DeepSeek"）';
COMMENT ON COLUMN ai_model_provider.provider_key    IS '程序标识（如 dashscope / deepseek / openai）';
COMMENT ON COLUMN ai_model_provider.base_url        IS 'OpenAI 兼容 Base URL';
COMMENT ON COLUMN ai_model_provider.api_key_enc     IS 'AES-GCM 加密 API Key';
COMMENT ON COLUMN ai_model_provider.api_key_masked  IS 'API Key 脱敏展示';
COMMENT ON COLUMN ai_model_provider.is_default      IS '是否为默认供应商（供回退定位）';
COMMENT ON COLUMN ai_model_provider.status          IS 'ENABLED / DISABLED';
