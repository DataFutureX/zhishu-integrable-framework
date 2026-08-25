-- 智能体定义表 + qa_history.agent_id（已有库增量）
-- psql -U postgres -d wanxiang_monitor -f migration_ai_agent.sql

CREATE TABLE IF NOT EXISTS ai_agent (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(500),
    system_prompt    TEXT         NOT NULL,
    model            VARCHAR(64),
    temperature      NUMERIC(4,2),
    max_tokens       INTEGER,
    capabilities     TEXT         NOT NULL DEFAULT '[]',
    workflow_type    VARCHAR(32)  NOT NULL DEFAULT 'REACT',
    workflow_config  TEXT,
    enable_memory    BOOLEAN      NOT NULL DEFAULT TRUE,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    is_builtin       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(64),
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_agent_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_status ON ai_agent (status);
CREATE INDEX IF NOT EXISTS idx_ai_agent_default ON ai_agent (is_default) WHERE is_default = TRUE;

COMMENT ON TABLE ai_agent IS 'AI 智能体定义（能力勾选 + 工作流模板）';
COMMENT ON COLUMN ai_agent.capabilities IS 'JSON 数组，如 ["STATION_LATEST","ONLINE","RAG"]';
COMMENT ON COLUMN ai_agent.workflow_type IS 'REACT | SEQUENTIAL | ROUTING';
COMMENT ON COLUMN ai_agent.workflow_config IS '模板扩展 JSON，一期可空';

ALTER TABLE qa_history ADD COLUMN IF NOT EXISTS agent_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_qa_history_agent ON qa_history (agent_id);

INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'monitor_default',
    '水利监测智能体',
    '万象监测默认智能体：遥测查询、在线状态、工程与告警，支持可选知识库增强',
    $prompt$你是「数智未来AI助手」，服务万象监测平台，擅长数据分析与智能问答。

你可以使用工具查询：
1. 遥测站最新/历史监测要素
2. 遥测站在线状态与列表
3. 工程（项目）列表
4. 近期阈值告警

使用规则：
- 「最新/当前/实时」→ queryStationLatestElements
- 「历史/趋势/某段时间」→ queryStationHistoryElements（时间 yyyy-MM-dd HH:mm:ss）
- 「在线/离线/站点列表/在线状态概览」→ getTerminalOnlineOverview 或 listTerminals / queryTerminalOnlineStatus
- 「工程/项目」→ listProjects
- 「告警」→ queryRecentAlerts
- 询问「全部遥测站在线状态」时必须调用 getTerminalOnlineOverview，用返回的 total/onlineCount/items 生成表格，禁止编造空表
- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）
- 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围
- 优先用工具取真实数据，再用中文简洁专业地总结
- 不确定时明确说明$prompt$,
    NULL, NULL, NULL,
    '["STATION_LATEST","STATION_HISTORY","ONLINE","PROJECT","ALERT","RAG"]',
    'REACT', TRUE, 'ENABLED', TRUE, TRUE, 'system'
)
ON CONFLICT (code) DO NOTHING;

-- 存量库：默认智能体显示名
UPDATE ai_agent SET name = '水利监测智能体' WHERE code = 'monitor_default' AND name = '监测助手';
