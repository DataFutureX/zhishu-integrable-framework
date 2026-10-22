-- =============================================================================
-- 模型网关（zhishu-model-gateway）建表脚本
-- 适用：PostgreSQL 14+；由 GatewaySchemaInitializer 幂等执行
-- 说明：
--   1. 目录 ai_gateway_model 为对外模型唯一来源，provider_id 指向 ai_model_provider
--   2. 额度 ai_gateway_quota 按 SUBJECT（USER / ROLE / DEFAULT）归账
--   3. 台账 ai_gateway_usage 同时承担「预留凭据」与「审计计量」
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 对外模型目录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_gateway_model (
    id                       BIGSERIAL PRIMARY KEY,
    model_id                 VARCHAR(64)  NOT NULL,
    display_name             VARCHAR(64)  NOT NULL,
    provider_id              BIGINT       NOT NULL,
    upstream_model           VARCHAR(128) NOT NULL,
    model_type               VARCHAR(16)  NOT NULL DEFAULT 'CHAT',
    context_window           INT,
    max_output_tokens        INT,
    input_modalities         VARCHAR(64)  NOT NULL DEFAULT 'text',
    system_prompt_update     VARCHAR(16)  NOT NULL DEFAULT 'in-history',
    reasoning_efforts        VARCHAR(64),
    default_reasoning_effort VARCHAR(16),
    client_requirements      TEXT,
    extra_params             TEXT,
    temperature              NUMERIC(3,2),
    top_p                    NUMERIC(3,2),
    max_tokens               INT,
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    sort_order               INT          NOT NULL DEFAULT 0,
    remark                   VARCHAR(500),
    create_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_gateway_model_model_id UNIQUE (model_id)
);

-- 默认模型按类型各自唯一：CHAT 与 EMBEDDING 各最多一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_chat
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'CHAT';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_embedding
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'EMBEDDING';

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_status
    ON ai_gateway_model (status, sort_order);

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_type
    ON ai_gateway_model (model_type, status, sort_order);

COMMENT ON TABLE  ai_gateway_model                          IS '模型网关：对外模型目录';
COMMENT ON COLUMN ai_gateway_model.model_id                 IS '对外模型 id（客户端 model 字段取值）';
COMMENT ON COLUMN ai_gateway_model.display_name             IS '展示名';
COMMENT ON COLUMN ai_gateway_model.provider_id              IS '上游供应商（ai_model_provider.id）';
COMMENT ON COLUMN ai_gateway_model.upstream_model           IS '该供应商的真实模型名';
COMMENT ON COLUMN ai_gateway_model.model_type               IS 'CHAT / EMBEDDING；EMBEDDING 不经网关推理面';
COMMENT ON COLUMN ai_gateway_model.context_window           IS '上下文窗口（tokens），空=未知';
COMMENT ON COLUMN ai_gateway_model.max_output_tokens        IS '单次输出上限，空=未知';
COMMENT ON COLUMN ai_gateway_model.input_modalities         IS '输入模态，逗号分隔：text,image';
COMMENT ON COLUMN ai_gateway_model.system_prompt_update     IS 'system 提示词更新策略：in-history / leading-only';
COMMENT ON COLUMN ai_gateway_model.reasoning_efforts        IS 'reasoning 档位，逗号分隔：low,medium,high';
COMMENT ON COLUMN ai_gateway_model.default_reasoning_effort IS '默认 reasoning 档位';
COMMENT ON COLUMN ai_gateway_model.client_requirements      IS '客户端版本门禁：{"LetsWork":{"min_version":"0.2.1"}}';
COMMENT ON COLUMN ai_gateway_model.extra_params             IS '透传/覆盖到上游请求体的专有参数';
COMMENT ON COLUMN ai_gateway_model.temperature              IS '默认温度（请求未显式传时补齐）';
COMMENT ON COLUMN ai_gateway_model.top_p                    IS '默认 Top P';
COMMENT ON COLUMN ai_gateway_model.max_tokens               IS '默认最大输出 token';
COMMENT ON COLUMN ai_gateway_model.is_default               IS '是否网关默认模型（全局至多一条）';

-- ---------------------------------------------------------------------------
-- 2. 额度主体
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_gateway_quota (
    id           BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL,
    subject_key  VARCHAR(64) NOT NULL,
    period_kind  VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    token_limit  BIGINT,
    used_tokens  BIGINT      NOT NULL DEFAULT 0,
    period_start TIMESTAMP   NOT NULL,
    period_end   TIMESTAMP   NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    remark       VARCHAR(500),
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_gateway_quota_subject UNIQUE (subject_type, subject_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_gateway_quota_type ON ai_gateway_quota (subject_type, status);

COMMENT ON TABLE  ai_gateway_quota              IS '模型网关：token 额度（按主体归账）';
COMMENT ON COLUMN ai_gateway_quota.subject_type IS 'USER / ROLE / DEFAULT';
COMMENT ON COLUMN ai_gateway_quota.subject_key  IS 'USER=username；ROLE=role_code；DEFAULT=*';
COMMENT ON COLUMN ai_gateway_quota.token_limit  IS '周期内 token 上限，NULL=不限量';
COMMENT ON COLUMN ai_gateway_quota.used_tokens  IS '当前周期已用（含预留）';
COMMENT ON COLUMN ai_gateway_quota.period_start IS '当前周期起点';
COMMENT ON COLUMN ai_gateway_quota.period_end   IS '当前周期终点（到达即惰性滚动清零）';

-- ---------------------------------------------------------------------------
-- 3. 调用台账（兼预留凭据）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_gateway_usage (
    id                BIGSERIAL PRIMARY KEY,
    request_id        VARCHAR(64) NOT NULL,
    user_id           VARCHAR(64),
    username          VARCHAR(64),
    channel           VARCHAR(16) NOT NULL DEFAULT 'DESKTOP',
    model_id          VARCHAR(64) NOT NULL,
    provider_id       BIGINT,
    upstream_model    VARCHAR(128),
    stream            BOOLEAN     NOT NULL DEFAULT FALSE,
    status            VARCHAR(16) NOT NULL,
    http_status       INT,
    reserved_tokens   BIGINT      NOT NULL DEFAULT 0,
    prompt_tokens     INT,
    completion_tokens INT,
    total_tokens      INT,
    cached_tokens     INT,
    reasoning_tokens  INT,
    estimated         BOOLEAN     NOT NULL DEFAULT FALSE,
    latency_ms        INT,
    ttft_ms           INT,
    client_name       VARCHAR(32),
    client_version    VARCHAR(32),
    ip                VARCHAR(64),
    user_agent        VARCHAR(255),
    error_code        VARCHAR(64),
    error_message     VARCHAR(500),
    create_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_gateway_usage_request UNIQUE (request_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_user    ON ai_gateway_usage (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_model   ON ai_gateway_usage (model_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_channel ON ai_gateway_usage (channel, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_time    ON ai_gateway_usage (create_time DESC);

COMMENT ON TABLE  ai_gateway_usage                 IS '模型网关：调用台账（预留/结算/审计）';
COMMENT ON COLUMN ai_gateway_usage.request_id      IS '单次请求唯一标识（网关生成）';
COMMENT ON COLUMN ai_gateway_usage.channel         IS 'DESKTOP / CONSOLE / AGENT / BRIEFING / OPENAPI / TEST';
COMMENT ON COLUMN ai_gateway_usage.status          IS 'RESERVED / SUCCESS / ERROR / REJECTED_QUOTA / ABORTED / TIMEOUT';
COMMENT ON COLUMN ai_gateway_usage.reserved_tokens IS '请求前预留量';
COMMENT ON COLUMN ai_gateway_usage.estimated       IS 'usage 是否由网关估算（上游未回 usage）';
COMMENT ON COLUMN ai_gateway_usage.http_status     IS '返回给客户端的 HTTP 状态';
