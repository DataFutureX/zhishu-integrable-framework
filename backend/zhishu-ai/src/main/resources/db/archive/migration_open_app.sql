-- 开放 API 应用凭证（幂等；权威脚本见 zhishu-core/.../db/init_ai.sql）

CREATE TABLE IF NOT EXISTS open_app (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    allowed_scopes   TEXT         NOT NULL DEFAULT '[]',
    remark           VARCHAR(500),
    created_by       VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_open_app_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_open_app_status ON open_app (status);

CREATE TABLE IF NOT EXISTS open_app_credential (
    id               BIGSERIAL PRIMARY KEY,
    app_id           BIGINT       NOT NULL,
    key_prefix       VARCHAR(32)  NOT NULL,
    secret_hash      VARCHAR(128) NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    expires_at       TIMESTAMP,
    last_used_at     TIMESTAMP,
    created_by       VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_open_app_credential_prefix UNIQUE (key_prefix)
);

CREATE INDEX IF NOT EXISTS idx_open_app_credential_app ON open_app_credential (app_id, status);

INSERT INTO open_app (code, name, status, allowed_scopes, remark, created_by)
VALUES (
    'wanxiang-monitor',
    '万象监测平台',
    'ENABLED',
    '["chat","knowledges","kg"]',
    '万象 BFF 代调知枢开放 API',
    'system'
)
ON CONFLICT (code) DO NOTHING;
