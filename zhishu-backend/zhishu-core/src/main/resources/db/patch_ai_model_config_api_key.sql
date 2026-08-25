-- 模型 API Key 加密入库
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS api_key_enc TEXT;
COMMENT ON COLUMN ai_model_config.api_key_enc IS 'AES-GCM 加密后的模型 API Key';
COMMENT ON COLUMN ai_model_config.api_key_masked IS 'API Key 脱敏展示';
COMMENT ON COLUMN ai_model_config.base_url IS 'OpenAI 兼容接口 Base URL';
