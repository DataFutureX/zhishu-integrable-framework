-- Agent Briefing 简报调度与投递（幂等）
-- psql -U postgres -d wanxiang_monitor -f migration_ai_briefing.sql

CREATE TABLE IF NOT EXISTS ai_briefing_schedule (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(128) NOT NULL,
    agent_id              BIGINT,
    prompt_template       TEXT,
    scope_type            VARCHAR(32)  NOT NULL DEFAULT 'USER_PROJECTS',
    schedule_type         VARCHAR(16)  NOT NULL,
    schedule_time         VARCHAR(8),
    schedule_days         VARCHAR(64),
    cron_expr             VARCHAR(128),
    timezone              VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    next_run_at           TIMESTAMP,
    last_run_at           TIMESTAMP,
    notify_bell           BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_email          BOOLEAN      NOT NULL DEFAULT FALSE,
    email_to_mode         VARCHAR(32)  NOT NULL DEFAULT 'USER_PROFILE',
    email_extra_to        TEXT,
    email_subject_template VARCHAR(256),
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by            VARCHAR(64),
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_briefing_schedule_next
    ON ai_briefing_schedule (enabled, next_run_at);

COMMENT ON TABLE ai_briefing_schedule IS 'Agent 简报调度配置';
COMMENT ON COLUMN ai_briefing_schedule.schedule_type IS 'DAILY | WEEKLY | CRON';
COMMENT ON COLUMN ai_briefing_schedule.scope_type IS 'USER_PROJECTS 等投递范围';

CREATE TABLE IF NOT EXISTS ai_briefing_delivery (
    id               BIGSERIAL PRIMARY KEY,
    schedule_id      BIGINT,
    trigger_type     VARCHAR(32),
    trigger_ref      VARCHAR(128),
    user_id          VARCHAR(64)  NOT NULL,
    agent_id         BIGINT,
    run_id           BIGINT,
    title            VARCHAR(256),
    content_md       TEXT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    started_at       TIMESTAMP,
    finished_at      TIMESTAMP,
    read_at          TIMESTAMP,
    bell_notified_at TIMESTAMP,
    email_status     VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    email_to         VARCHAR(512),
    email_error      TEXT,
    email_sent_at    TIMESTAMP,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_briefing_delivery_user_time
    ON ai_briefing_delivery (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_briefing_delivery_status_read
    ON ai_briefing_delivery (status, read_at);
CREATE INDEX IF NOT EXISTS idx_ai_briefing_delivery_schedule
    ON ai_briefing_delivery (schedule_id, create_time DESC);

COMMENT ON TABLE ai_briefing_delivery IS 'Agent 简报投递记录';
COMMENT ON COLUMN ai_briefing_delivery.status IS 'PENDING | RUNNING | SUCCESS | FAILED | SKIPPED';
COMMENT ON COLUMN ai_briefing_delivery.trigger_type IS 'SCHEDULE | RUN_NOW';

-- 种子：每日监测简报（幂等，按名称去重）
INSERT INTO ai_briefing_schedule (
    name, agent_id, prompt_template, scope_type,
    schedule_type, schedule_time, timezone,
    next_run_at,
    notify_bell, notify_email, email_to_mode,
    enabled, created_by
)
SELECT
    '每日监测简报',
    COALESCE(
        (SELECT id FROM ai_agent WHERE code = 'monitor_default' LIMIT 1),
        (SELECT id FROM ai_agent WHERE status = 'ENABLED' ORDER BY id LIMIT 1)
    ),
    $prompt$请生成「每日监测简报」，必须调用工具获取真实数据，禁止编造。

请覆盖：
1. 遥测站在线状态概览（调用 getTerminalOnlineOverview）
2. 近 7 日告警趋势与重点告警（调用 queryRecentAlerts 等）
3. 若能力可用，附巡检摘要（计划/任务/问题概况）

输出 Markdown，建议章节：
# 每日监测简报
## 在线概览
## 告警趋势（近7日）
## 巡检摘要
## 建议关注
$prompt$,
    'USER_PROJECTS',
    'DAILY',
    '08:00',
    'Asia/Shanghai',
    NOW() + INTERVAL '1 day',
    TRUE,
    FALSE,
    'USER_PROFILE',
    TRUE,
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM ai_briefing_schedule WHERE name = '每日监测简报'
);
