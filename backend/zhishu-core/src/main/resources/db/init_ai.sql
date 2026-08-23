-- =============================================================================
-- 知枢可集成框架 · AI 表 — PostgreSQL 初始化脚本
-- 适用：PostgreSQL 14+ / 库名 zhishu_integrable_framework
-- 用法：
--   psql -U postgres -d zhishu_integrable_framework -f init_ai.sql
-- 说明：
--   1. 从万象 ai-assistant 迁入，不含万象业务表
--   2. 需已启用 vector 扩展
--   3. vector_store / SPRING_AI_CHAT_MEMORY 由 Spring AI 启动时自动建表
-- 关联：[知枢拆分最终方案.md](../../../../../docs/知枢拆分最终方案.md)
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
COMMENT ON COLUMN ai_model_config.api_key_enc IS 'AES-GCM 加密后的模型 API Key';


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
COMMENT ON COLUMN ai_agent.capabilities IS 'JSON 数组，内核如 ["RAG","MCP_TOOLS"]；监测 Tool 来自 MCP 上游';
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
    '["RAG","MCP_TOOLS"]',
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
    '["MCP_TOOLS"]',
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
    '["MCP_TOOLS"]',
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

-- 默认登记万象监测 MCP（同进程 :8080/mcp，API Key 与万象 dev 一致）
INSERT INTO ai_mcp_upstream (
    code, name, protocol, base_url, endpoint, auth_header_enc,
    request_timeout_ms, status, remark, created_by
) VALUES (
    'wanxiang-monitor',
    '万象监测 MCP',
    'STREAMABLE_HTTP',
    'http://127.0.0.1:8080',
    '/mcp',
    'Bearer dev-wanxiang-mcp-key',
    20000,
    'ENABLED',
    '知枢 Agent 查询监测/巡检/NL2SQL 的默认上游',
    'system'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO ai_agent_mcp_upstream (agent_id, upstream_id)
SELECT a.id, u.id
FROM ai_agent a
CROSS JOIN ai_mcp_upstream u
WHERE u.code = 'wanxiang-monitor'
  AND a.code IN ('monitor_default', 'inspection_agent', 'nl2sql_agent')
ON CONFLICT (agent_id, upstream_id) DO NOTHING;


-- ---------------------------------------------------------------------------
-- 开放 API 应用凭证（万象等服务端集成）
-- ---------------------------------------------------------------------------

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

COMMENT ON TABLE open_app IS '开放 API 接入应用（如万象 monitor-platform）';
COMMENT ON COLUMN open_app.allowed_scopes IS 'JSON 数组：chat / knowledges / kg 等（简报为知枢内部模块，不含 briefings）';

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

COMMENT ON TABLE open_app_credential IS '开放 API 应用凭证；secret_hash 为 SHA-256 十六进制';

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

INSERT INTO open_app_credential (app_id, key_prefix, secret_hash, status, created_by)
SELECT a.id,
       'zsopen_dev_wan',
       '834c7fbd53c42e4ff5973a6e33da06dda740734d90faa722d7765c7a3b6c944a',
       'ENABLED',
       'system'
FROM open_app a
WHERE a.code = 'wanxiang-monitor'
ON CONFLICT (key_prefix) DO NOTHING;
