-- 简报 Webhook 投递状态（已有库补丁）
-- psql -d zhishu_integrable_framework -v ON_ERROR_STOP=1 -f patch_webhook_delivery.sql

ALTER TABLE ai_briefing_delivery
    ADD COLUMN IF NOT EXISTS webhook_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE ai_briefing_delivery
    ADD COLUMN IF NOT EXISTS webhook_error TEXT;

ALTER TABLE ai_briefing_delivery
    ADD COLUMN IF NOT EXISTS webhook_sent_at TIMESTAMP;

COMMENT ON COLUMN ai_briefing_delivery.webhook_status IS 'NONE | PENDING | SENT | SKIPPED | FAILED';
