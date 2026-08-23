-- =============================================================================
-- 万象监测平台 · AI 助手 — PostgreSQL 完整初始化脚本（最终态）
-- 适用：PostgreSQL 14+ / 库名 wanxiang_monitor（可与业务表同库）
-- 用法：psql -U postgres -d wanxiang_monitor -f init_postgresql.sql
-- 说明：
--   1. 已合并本目录历史上全部 migration_* 的最终表结构与种子数据
--   2. 新库只需执行本脚本；旧库升级请参考 archive/ 中的增量脚本
--   3. 需已启用 vector 扩展（本脚本会 CREATE EXTENSION IF NOT EXISTS）
--   4. vector_store / SPRING_AI_CHAT_MEMORY 由 Spring AI 启动时自动建表
-- 生成日期：2026-08-12
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;


-- ---------------------------------------------------------------------------
-- 1. 知识库分类
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS knowledges_category (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(500),
    sort_order   INT          NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledges_category_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_knowledges_category_status ON knowledges_category (status, sort_order);

COMMENT ON TABLE knowledges_category IS '知识库分类：每个分类对应一类知识库';
COMMENT ON COLUMN knowledges_category.code IS '唯一编码';
COMMENT ON COLUMN knowledges_category.name IS '知识库名称';

INSERT INTO knowledges_category (code, name, description, sort_order, status)
VALUES
    ('general', '通用知识库', '默认知识库，未单独归类的文档可放入此处', 0, 'ENABLED'),
    ('standard', '技术规范', '标准、规程、规范类文档', 10, 'ENABLED'),
    ('ops', '运维手册', '运维操作、故障处理类文档', 20, 'ENABLED')
ON CONFLICT (code) DO NOTHING;


-- ---------------------------------------------------------------------------
-- 2. 知识文档元数据（原 documents）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS knowledges (
    id          BIGSERIAL PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL,
    file_type   VARCHAR(50)  NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_size   BIGINT,
    content     TEXT,
    category_id BIGINT,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed   BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_knowledges_file_name ON knowledges (file_name);
CREATE INDEX IF NOT EXISTS idx_knowledges_processed ON knowledges (processed);
CREATE INDEX IF NOT EXISTS idx_knowledges_upload_time ON knowledges (upload_time DESC);
CREATE INDEX IF NOT EXISTS idx_knowledges_category ON knowledges (category_id);

COMMENT ON TABLE knowledges IS '知识文档元数据：上传文件信息与解析文本';
COMMENT ON COLUMN knowledges.id IS '知识文档 ID';
COMMENT ON COLUMN knowledges.file_name IS '文件名';
COMMENT ON COLUMN knowledges.file_type IS '类型（pdf / docx / doc 等）';
COMMENT ON COLUMN knowledges.file_path IS '存储路径';
COMMENT ON COLUMN knowledges.file_size IS '大小（字节）';
COMMENT ON COLUMN knowledges.content IS '解析后的文本';
COMMENT ON COLUMN knowledges.category_id IS '所属知识库分类 ID';
COMMENT ON COLUMN knowledges.upload_time IS '上传时间';
COMMENT ON COLUMN knowledges.update_time IS '最后更新时间';
COMMENT ON COLUMN knowledges.processed IS '是否已完成向量化';

UPDATE knowledges d
SET category_id = c.id
FROM knowledges_category c
WHERE d.category_id IS NULL
  AND c.code = 'general';


-- ---------------------------------------------------------------------------
-- 3. 问答历史
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS qa_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    scene           VARCHAR(32) NOT NULL,
    question        TEXT        NOT NULL,
    answer          TEXT        NOT NULL,
    model           VARCHAR(64),
    document_id     BIGINT,
    conversation_id VARCHAR(64),
    agent_id        BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_qa_history_user_scene_time
    ON qa_history (user_id, scene, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_qa_history_conversation
    ON qa_history (conversation_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_agent
    ON qa_history (agent_id);

COMMENT ON TABLE qa_history IS 'AI 问答历史';
COMMENT ON COLUMN qa_history.user_id IS '用户 ID（前端 X-User-Id）';
COMMENT ON COLUMN qa_history.scene IS '场景：CHAT=智能问答，DOCUMENT_QA=知识问答';
COMMENT ON COLUMN qa_history.question IS '用户提问';
COMMENT ON COLUMN qa_history.answer IS 'AI 回答';
COMMENT ON COLUMN qa_history.model IS '模型标识';
COMMENT ON COLUMN qa_history.document_id IS '知识问答指定文档 ID（可空）';
COMMENT ON COLUMN qa_history.conversation_id IS '多轮会话 ID';
COMMENT ON COLUMN qa_history.agent_id IS '关联智能体 ID';
COMMENT ON COLUMN qa_history.create_time IS '创建时间';


-- ---------------------------------------------------------------------------
-- 4. Agent 会话元数据
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS chat_session (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    scene           VARCHAR(32)  NOT NULL DEFAULT 'CHAT',
    title           VARCHAR(128) NOT NULL DEFAULT '新会话',
    agent_id        BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_session_conversation UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_scene_time
    ON chat_session (user_id, scene, update_time DESC);

COMMENT ON TABLE chat_session IS 'AI Agent 会话（标题/智能体元数据）';
COMMENT ON COLUMN chat_session.conversation_id IS '多轮会话 ID（与 qa_history / Memory 一致）';
COMMENT ON COLUMN chat_session.title IS '会话标题（可编辑）';


-- ---------------------------------------------------------------------------
-- 5. AI 模型运行时配置（单例 id=1）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ai_model_config (
    id                  BIGINT PRIMARY KEY DEFAULT 1,
    chat_model          VARCHAR(64)  NOT NULL DEFAULT 'qwen-plus',
    embedding_model     VARCHAR(64)  NOT NULL DEFAULT 'qwen3.7-text-embedding',
    temperature         NUMERIC(4,2) NOT NULL DEFAULT 0.70,
    max_tokens          INTEGER      NOT NULL DEFAULT 2000,
    top_p               NUMERIC(4,2) NOT NULL DEFAULT 0.90,
    enable_rag_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    memory_window_size  INTEGER      NOT NULL DEFAULT 20,
    base_url            VARCHAR(512),
    api_key_masked      VARCHAR(128),
    api_key_enc         TEXT,
    remark              VARCHAR(500),
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_model_config_singleton CHECK (id = 1)
);

INSERT INTO ai_model_config (id, chat_model, embedding_model, temperature, max_tokens, top_p, enable_rag_default, memory_window_size, remark)
VALUES (1, 'qwen-plus', 'qwen3.7-text-embedding', 0.70, 2000, 0.90, FALSE, 20, '默认通义兼容配置')
ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE ai_model_config IS 'AI 助手模型运行时配置（单例）';
COMMENT ON COLUMN ai_model_config.chat_model IS '对话模型名';
COMMENT ON COLUMN ai_model_config.embedding_model IS '向量模型名（改后需重新向量化文档）';


-- ---------------------------------------------------------------------------
-- 6. 智能体定义
-- ---------------------------------------------------------------------------

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
    document_ids     TEXT,
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
COMMENT ON COLUMN ai_agent.workflow_type IS 'REACT | SEQUENTIAL | ROUTING | GRAPH';
COMMENT ON COLUMN ai_agent.workflow_config IS 'Graph JSON v1 或模板扩展';
COMMENT ON COLUMN ai_agent.document_ids IS '绑定知识库文档 ID JSON 数组，空=全部';

INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'monitor_default',
    '水利监测智能体',
    '万象监测默认智能体：遥测查询、多站对比、在线状态、工程与告警趋势，支持可选知识库增强',
    $prompt$你是「数智未来AI助手」，服务万象监测平台，擅长数据分析与智能问答。

你可以使用工具查询：
1. 遥测站最新/历史监测要素
2. 多站最新要素对比 compareStations
3. 遥测站在线状态与列表
4. 工程（项目）列表
5. 近期阈值告警与告警趋势 queryRecentAlerts / queryAlertTrends

使用规则：
- 「最新/当前/实时」→ queryStationLatestElements
- 「历史/趋势/某段时间」→ queryStationHistoryElements（时间 yyyy-MM-dd HH:mm:ss）
- 「对比/并排/几个站」→ compareStations（站号逗号分隔，最多 8 站）
- 「在线/离线/站点列表/在线状态概览」→ getTerminalOnlineOverview 或 listTerminals / queryTerminalOnlineStatus
- 「工程/项目」→ listProjects
- 「告警列表」→ queryRecentAlerts；「告警趋势/最近几天告警」→ queryAlertTrends
- 询问「全部遥测站在线状态」时必须调用 getTerminalOnlineOverview，用返回的 total/onlineCount/items 生成表格，禁止编造空表
- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）
- 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围
- 优先用工具取真实数据，再用中文简洁专业地总结
- 不确定时明确说明$prompt$,
    NULL, NULL, NULL,
    '["STATION_LATEST","STATION_HISTORY","STATION_COMPARE","ONLINE","PROJECT","ALERT","RAG"]',
    'REACT', TRUE, 'ENABLED', TRUE, TRUE, 'system'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'inspection_agent',
    '巡检智能体',
    '数字巡检（在线/遥测/告警/趋势/对比）+ 业务巡检只读（计划/任务/异常/摘要）；顺序工作流：澄清 → 工具 → 润色',
    $prompt$你是「万象巡检智能体」，服务水利监测平台的数字巡检与业务巡检问答（只读，不创建/修改任务）。

你可以使用工具：
【数字巡检】
1. 工程列表 listProjects
2. 在线概览/列表/单站 getTerminalOnlineOverview、listTerminals、queryTerminalOnlineStatus
3. 遥测最新/历史/多站对比 queryStationLatestElements、queryStationHistoryElements、compareStations
4. 近期阈值告警与趋势 queryRecentAlerts、queryAlertTrends
【业务巡检（只读）】
5. 巡检计划 listInspectionPlans、getInspectionPlan
6. 巡检任务 listInspectionTasks、getInspectionTaskDetail
7. 巡检异常 listOpenInspectionIssues
8. 巡检摘要 getInspectionSummary（计划/任务/未关闭异常计数）

使用规则：
- 「帮我巡检 / 数字巡检 / 巡检报告」：先调 listProjects 获取工程列表，确认工程或范围；再 getInspectionSummary 看业务巡检概况；再 getTerminalOnlineOverview 或按工程 listTerminals；对离线站与告警站补充最新值/告警/趋势；输出结构化巡检报告
- 「巡检整体情况 / 进度摘要」→ getInspectionSummary（projectId 必填，先从 listProjects 结果中取）
- 「巡检计划 / 启用计划」→ listInspectionPlans（projectId 必填，先从 listProjects 结果中取；可选 status=ENABLED 过滤）
- 「巡检任务 / 进行中任务 / 任务进度」→ listInspectionTasks（projectId 必填）；详情与检查点完成率 → getInspectionTaskDetail
- 「巡检异常 / 未关闭异常」→ listOpenInspectionIssues（projectId 必填）
- 调用巡检类工具（listInspectionPlans / getInspectionSummary / listInspectionTasks / listOpenInspectionIssues）前，必须先调 listProjects 拿到 projectId 再传入；禁止传 keyword 等不存在的参数
- 用户未指定工程时，先询问或使用 listProjects 返回的第一个工程；禁止省略 projectId
- 「在线/离线」→ getTerminalOnlineOverview 或 listTerminals；禁止编造站点表
- 「告警列表」→ queryRecentAlerts；「告警趋势」→ queryAlertTrends
- 「多站对比」→ compareStations
- 「最新/历史数据」→ 对应遥测工具（时间 yyyy-MM-dd HH:mm:ss）
- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）
- 相对时间按系统注入时间换算；优先工具取真数；不确定时明确说明
- 不要声称已创建任务、已打卡或已关闭异常（本智能体只读）

数字巡检报告建议结构（Markdown）：
1. 巡检范围与时间
2. 业务巡检摘要（计划/任务/未关闭异常，若有）
3. 在线概览（总数/在线/离线）
4. 异常清单（离线站、告警、数据异常、告警趋势要点）
5. 建议动作（人工复核，不代执行写操作）$prompt$,
    NULL, NULL, NULL,
    '["STATION_LATEST","STATION_HISTORY","STATION_COMPARE","ONLINE","PROJECT","ALERT","INSPECTION_PLAN","INSPECTION_TASK","INSPECTION_ISSUE","INSPECTION_SUMMARY"]',
    'SEQUENTIAL', TRUE, 'ENABLED', TRUE, FALSE, 'system'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    capabilities = EXCLUDED.capabilities,
    workflow_type = EXCLUDED.workflow_type,
    enable_memory = EXCLUDED.enable_memory,
    status = EXCLUDED.status,
    is_builtin = TRUE,
    is_default = FALSE,
    update_time = CURRENT_TIMESTAMP
WHERE ai_agent.is_builtin = TRUE;


INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'nl2sql_agent',
    '数据分析智能体',
    '自然语言转只读 SQL：工程/站点/要素/最新与历史遥测（分区父表）/告警；按用户工程权限过滤；SQL 对用户可见',
    $prompt$你是「万象数据分析智能体」，通过 NL2SQL 查询监测业务库（只读）。

必须遵守：
1. 生成 SQL 前先调用 describeBizSchema（可指定表名）
2. 仅使用白名单表：t_project、t_terminal、t_element_config、t_timed_report_latest、t_timed_report、t_terminal_alert
3. 历史遥测查父表 t_timed_report，必须带 observe_time 时间范围；不要写子分区表名
4. 测点用 element_code（水位 z、降雨 pn05、流量 q 等）；不确定时先查 t_element_config
5. 只生成单条 SELECT/WITH；系统会按当前用户工程权限自动过滤
6. 调用 executeReadonlySql 后：用 markdown/rows 回答，并展示 executedSql（可对用户可见）
7. SQL 失败时根据 error 修正后最多再试 1 次；仍失败则说明原因
8. 不要编造数据；无权限或无数据时如实说明

回答结构建议：
- 简要结论
- 数据表（优先使用返回的 markdown）
- 所用 SQL（executedSql）
$prompt$,
    NULL, NULL, NULL,
    '["NL2SQL"]',
    'REACT', TRUE, 'ENABLED', TRUE, FALSE, 'system'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    capabilities = EXCLUDED.capabilities,
    workflow_type = EXCLUDED.workflow_type,
    enable_memory = EXCLUDED.enable_memory,
    status = EXCLUDED.status,
    is_builtin = TRUE,
    is_default = FALSE,
    update_time = CURRENT_TIMESTAMP
WHERE ai_agent.is_builtin = TRUE;


INSERT INTO ai_agent (
    code, name, description, system_prompt, model, temperature, max_tokens,
    capabilities, workflow_type, enable_memory, status, is_builtin, is_default, created_by
) VALUES (
    'kg_agent',
    '知识图谱智能体',
    '基于业务拓扑图谱的 GraphRAG：实体搜索、邻居展开、最短路径、工程拓扑摘要、告警影响面（只读）',
    $prompt$你是「万象知识图谱智能体」，只通过知识图谱工具回答工程 / 终端 / 告警 / 巡检 / 区域 / 负责人之间的关系与拓扑问题。

可用工具：
1. searchGraphEntities — 按名称/编码搜索实体，得到 label 与 bizId
2. getGraphNeighbors — 展开指定实体邻居子图
3. findGraphPath — 两实体最短路径
4. getProjectTopology — 工程拓扑摘要（站点/告警/计划/任务/未关闭异常计数）
5. getAlertImpact — 告警影响面（关联终端、工程、同站其它告警、巡检异常等）

使用规则：
- 用户提到工程名、站名、告警、巡检异常时，先 searchGraphEntities 定位 label/bizId，再查邻居或路径
- 「某工程整体关联 / 拓扑概况」→ getProjectTopology
- 「A 和 B 什么关系 / 如何连通」→ findGraphPath
- 「某告警影响范围 / 波及哪些站」→ getAlertImpact
- 「展开邻居 / 周边实体」→ getGraphNeighbors
- 回答时引用工具返回的 label、bizId、name 与关系链，禁止编造未返回的节点或边
- 图谱不可用或无权时如实说明；本智能体不做遥测取数、不写 SQL、不修改业务数据

回答结构建议（Markdown）：
1. 简要结论
2. 关系链或影响面要点（节点与边）
3. 如需进一步排查，给出可继续查询的实体 hint$prompt$,
    NULL, NULL, NULL,
    '["KNOWLEDGE_GRAPH"]',
    'REACT', TRUE, 'ENABLED', TRUE, FALSE, 'system'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    capabilities = EXCLUDED.capabilities,
    workflow_type = EXCLUDED.workflow_type,
    enable_memory = EXCLUDED.enable_memory,
    status = EXCLUDED.status,
    is_builtin = TRUE,
    is_default = FALSE,
    update_time = CURRENT_TIMESTAMP
WHERE ai_agent.is_builtin = TRUE;


-- ---------------------------------------------------------------------------
-- 7. 智能体执行记录（Graph / 试运行 Checkpoint）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ai_agent_run (
    id               BIGSERIAL PRIMARY KEY,
    agent_id         BIGINT       NOT NULL,
    conversation_id  VARCHAR(64),
    status           VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    current_node     VARCHAR(64),
    state_json       TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_run_agent ON ai_agent_run (agent_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_conv ON ai_agent_run (conversation_id);

COMMENT ON TABLE ai_agent_run IS '智能体 Graph/试运行执行记录（Checkpoint）';

-- ---------------------------------------------------------------------------
-- 8. Agent Briefing 简报调度与投递
-- ---------------------------------------------------------------------------

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


-- ---------------------------------------------------------------------------
-- 知识图谱同步水位
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ai_kg_sync_watermark (
    source_table       VARCHAR(64) PRIMARY KEY,
    last_sync_at       TIMESTAMP,
    max_source_time    TIMESTAMP,
    last_status        VARCHAR(32),
    last_message       VARCHAR(500),
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_kg_sync_watermark IS '知识图谱 PG→Neo4j 同步水位';
COMMENT ON COLUMN ai_kg_sync_watermark.source_table IS '源表名，如 t_project';
COMMENT ON COLUMN ai_kg_sync_watermark.max_source_time IS '已同步到的源表最大时间戳';
COMMENT ON COLUMN ai_kg_sync_watermark.last_status IS 'SUCCESS | FAILED | SKIPPED';

-- ---------------------------------------------------------------------------
-- MCP 双平面：对外 Client / 接入上游 / 调用审计
-- ---------------------------------------------------------------------------

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

CREATE INDEX IF NOT EXISTS idx_ai_mcp_client_status ON ai_mcp_client (status);

COMMENT ON TABLE ai_mcp_client IS '他方调用本平台 MCP 的 Client';
COMMENT ON COLUMN ai_mcp_client.secret_hash IS 'API Key SHA-256 十六进制，明文仅创建/轮换时返回一次';
COMMENT ON COLUMN ai_mcp_client.bound_user_id IS '绑定 sys_user.id，复用工程 ACL';
COMMENT ON COLUMN ai_mcp_client.capabilities IS 'JSON 数组，对齐 AgentCapability；空=默认只读 20 Tool';

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

COMMENT ON TABLE ai_mcp_upstream IS '本平台接入的他方 MCP Server';
COMMENT ON COLUMN ai_mcp_upstream.protocol IS 'STREAMABLE_HTTP | SSE';
COMMENT ON COLUMN ai_mcp_upstream.auth_header_enc IS 'Authorization 头，可选加密';

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

COMMENT ON TABLE ai_mcp_upstream_tool IS '上游 tools/list 缓存与白名单';

CREATE TABLE IF NOT EXISTS ai_agent_mcp_upstream (
    id               BIGSERIAL PRIMARY KEY,
    agent_id         BIGINT       NOT NULL,
    upstream_id      BIGINT       NOT NULL,
    allowed_tools    TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_agent_mcp_upstream UNIQUE (agent_id, upstream_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_mcp_upstream_agent ON ai_agent_mcp_upstream (agent_id);

COMMENT ON TABLE ai_agent_mcp_upstream IS '智能体绑定的上游 MCP';

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

COMMENT ON TABLE ai_mcp_call_log IS 'MCP 调用审计 OUT=对外提供 IN=接入他方';
COMMENT ON COLUMN ai_mcp_call_log.direction IS 'OUT | IN';
