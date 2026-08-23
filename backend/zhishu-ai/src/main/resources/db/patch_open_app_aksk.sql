-- 开放 API 应用增加 AK/SK 鉴权字段（幂等）
-- AK/SK 签名 Token 鉴权：调用方用 SK 签名生成 Token，传递 AK，知枢端验签放行

ALTER TABLE open_app ADD COLUMN IF NOT EXISTS access_key VARCHAR(64);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS secret_key_enc TEXT;
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS aksk_generated_at TIMESTAMP;
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_open_app_access_key ON open_app (access_key);

COMMENT ON COLUMN open_app.access_key IS 'Access Key，以 zsak_ 前缀，全局唯一';
COMMENT ON COLUMN open_app.secret_key_enc IS 'Secret Key 密文（AES-GCM），明文仅在生成时返回一次';
COMMENT ON COLUMN open_app.aksk_generated_at IS 'AK/SK 最近一次生成时间';
COMMENT ON COLUMN open_app.last_used_at IS '最近一次调用时间';
