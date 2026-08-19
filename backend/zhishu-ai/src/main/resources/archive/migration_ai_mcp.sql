-- MCP 双平面表（幂等）

CREATE TABLE IF NOT EXISTS ai_mcp_client (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(128) NOT NULL,
    key_prefix       VARCHAR(32)  NOT NULL,
    secret_hash      VARCHAR(128) NOT NULL,
    bound_user_id    BIGINT       NOT NULL,
    bound_username   VARCHAR(128),
    capabilities     TEXT         NOT NULL DEFAULT '[]',
    rpm_limit        INTEGER      NOT NULL DEFAULT 60,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark           VARCHAR(500),
    last_used_at     TIMESTAMP,
    created_by       VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_mcp_client_prefix UNIQUE (key_prefix)
);

COMMENT ON TABLE ai_mcp_client IS '他方调用本平台 MCP 的 Client';
COMMENT ON COLUMN ai_mcp_client.secret_hash IS 'API Key SHA-256 十六进制，明文仅创建/轮换时返回一次';
COMMENT ON COLUMN ai_mcp_client.bound_user_id IS '绑定 sys_user.id，复用工程 ACL';
COMMENT ON COLUMN ai_mcp_client.capabilities IS 'JSON 数组，对齐 AgentCapability；空=默认只读 20 Tool';

CREATE INDEX IF NOT EXISTS idx_ai_mcp_client_status ON ai_mcp_client (status);

CREATE TABLE IF NOT EXISTS ai_mcp_upstream (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    protocol         VARCHAR(32)  NOT NULL DEFAULT 'STREAMABLE_HTTP',
    base_url         VARCHAR(512) NOT NULL,
    endpoint         VARCHAR(128) NOT NULL DEFAULT '/mcp',
    auth_header_enc  TEXT,
    request_timeout_ms INTEGER    NOT NULL DEFAULT 20000,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    health_status    VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    health_message   VARCHAR(500),
    last_probe_at    TIMESTAMP,
    remark           VARCHAR(500),
    created_by       VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_mcp_upstream_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_ai_mcp_upstream_status ON ai_mcp_upstream (status);

CREATE TABLE IF NOT EXISTS ai_mcp_upstream_tool (
    id               BIGSERIAL PRIMARY KEY,
    upstream_id      BIGINT       NOT NULL,
    original_name    VARCHAR(128) NOT NULL,
    exposed_name     VARCHAR(160) NOT NULL,
    description      TEXT,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_mcp_upstream_tool UNIQUE (upstream_id, original_name)
);

CREATE INDEX IF NOT EXISTS idx_ai_mcp_upstream_tool_up ON ai_mcp_upstream_tool (upstream_id);

CREATE TABLE IF NOT EXISTS ai_agent_mcp_upstream (
    id               BIGSERIAL PRIMARY KEY,
    agent_id         BIGINT       NOT NULL,
    upstream_id      BIGINT       NOT NULL,
    allowed_tools    TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_agent_mcp_upstream UNIQUE (agent_id, upstream_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_mcp_upstream_agent ON ai_agent_mcp_upstream (agent_id);

CREATE TABLE IF NOT EXISTS ai_mcp_call_log (
    id               BIGSERIAL PRIMARY KEY,
    direction        VARCHAR(8)   NOT NULL,
    client_id        BIGINT,
    upstream_id      BIGINT,
    agent_id         BIGINT,
    tool_name        VARCHAR(160) NOT NULL,
    success          BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message    VARCHAR(500),
    duration_ms      INTEGER,
    user_id          VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_mcp_call_log_time ON ai_mcp_call_log (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_call_log_dir ON ai_mcp_call_log (direction, create_time DESC);
