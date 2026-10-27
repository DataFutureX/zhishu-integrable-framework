-- =============================================================================
-- AI 模块数据库脚本（Agent / 会话 / 问答历史 / 知识库 / MCP Hub / 开放应用）
--
-- 来源：以开发库实际结构导出后幂等化，与 cn.datafuturex.zhishu.ai.* 实体一致。
-- 幂等：可重复执行；不写入业务数据（Agent、文档、调用日志等由应用产生）。
-- 执行：psql -f db/init_ai.sql   或由 ChatSession/Document/OpenApp SchemaInitializer 启动期执行
-- =============================================================================

-- ---------- 表结构与字段备注（每表 DDL 后紧跟 COMMENT）----------
CREATE TABLE IF NOT EXISTS public.ai_agent (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(500),
    system_prompt text NOT NULL,
    model character varying(64),
    temperature numeric(4,2),
    max_tokens integer,
    capabilities text DEFAULT '[]'::text NOT NULL,
    workflow_type character varying(32) DEFAULT 'REACT'::character varying NOT NULL,
    workflow_config text,
    document_ids text,
    enable_memory boolean DEFAULT true NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    is_builtin boolean DEFAULT false NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_agent_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_agent_code UNIQUE (code)
);

COMMENT ON TABLE public.ai_agent IS '智能体定义';
COMMENT ON COLUMN public.ai_agent.id IS '主键';
COMMENT ON COLUMN public.ai_agent.code IS '智能体编码（唯一）';
COMMENT ON COLUMN public.ai_agent.name IS '显示名称';
COMMENT ON COLUMN public.ai_agent.description IS '人设简介';
COMMENT ON COLUMN public.ai_agent.system_prompt IS '系统提示词';
COMMENT ON COLUMN public.ai_agent.model IS '绑定的网关模型 id（空=使用默认对话模型）';
COMMENT ON COLUMN public.ai_agent.temperature IS '采样温度';
COMMENT ON COLUMN public.ai_agent.max_tokens IS '单次回复最大 token 数';
COMMENT ON COLUMN public.ai_agent.capabilities IS '能力开关（JSON 数组，如 ["ONLINE","RAG"]）';
COMMENT ON COLUMN public.ai_agent.workflow_type IS '工作流类型：SIMPLE=单轮，GRAPH=图编排';
COMMENT ON COLUMN public.ai_agent.workflow_config IS '图编排定义（Graph JSON，含 nodes/edges）';
COMMENT ON COLUMN public.ai_agent.document_ids IS '绑定的知识库文档 id（JSON 数组，空=全部）';
COMMENT ON COLUMN public.ai_agent.enable_memory IS '是否启用多轮记忆：true=启用';
COMMENT ON COLUMN public.ai_agent.status IS '状态：1=启用，0=停用';
COMMENT ON COLUMN public.ai_agent.is_builtin IS '是否内置智能体：true=内置（不建议删除）';
COMMENT ON COLUMN public.ai_agent.is_default IS '是否默认智能体：true=默认（全局至多一条）';
COMMENT ON COLUMN public.ai_agent.created_by IS '创建人用户 id';
COMMENT ON COLUMN public.ai_agent.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_agent.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_agent_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_agent_id_seq OWNED BY public.ai_agent.id;
CREATE TABLE IF NOT EXISTS public.ai_agent_mcp_upstream (
    id bigint NOT NULL,
    agent_id bigint NOT NULL,
    upstream_id bigint NOT NULL,
    allowed_tools text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_agent_mcp_upstream_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_agent_mcp_upstream UNIQUE (agent_id, upstream_id)
);

COMMENT ON TABLE public.ai_agent_mcp_upstream IS '智能体与 MCP 上游的绑定关系';
COMMENT ON COLUMN public.ai_agent_mcp_upstream.id IS '主键';
COMMENT ON COLUMN public.ai_agent_mcp_upstream.agent_id IS '智能体 id（ai_agent.id）';
COMMENT ON COLUMN public.ai_agent_mcp_upstream.upstream_id IS 'MCP 上游 id（ai_mcp_upstream.id）';
COMMENT ON COLUMN public.ai_agent_mcp_upstream.allowed_tools IS '允许使用的工具名（JSON 数组，空=全部启用工具）';
COMMENT ON COLUMN public.ai_agent_mcp_upstream.create_time IS '创建时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_agent_mcp_upstream_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_agent_mcp_upstream_id_seq OWNED BY public.ai_agent_mcp_upstream.id;
CREATE TABLE IF NOT EXISTS public.ai_agent_run (
    id bigint NOT NULL,
    agent_id bigint NOT NULL,
    conversation_id character varying(64),
    status character varying(32) DEFAULT 'RUNNING'::character varying NOT NULL,
    current_node character varying(64),
    state_json text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    user_message text,
    response_summary text,
    duration_ms bigint,
    error_message character varying(1000),
    model_name character varying(64),
    workflow_type character varying(32),
    user_id character varying(64),
    run_type character varying(16) DEFAULT 'CHAT'::character varying NOT NULL,
    ttft_ms bigint,
    tpot_ms bigint,
    token_count integer,
    CONSTRAINT ai_agent_run_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.ai_agent_run IS '智能体运行记录（监控用）';
COMMENT ON COLUMN public.ai_agent_run.id IS '主键';
COMMENT ON COLUMN public.ai_agent_run.agent_id IS '智能体 id';
COMMENT ON COLUMN public.ai_agent_run.conversation_id IS '多轮会话 id';
COMMENT ON COLUMN public.ai_agent_run.status IS '运行状态：RUNNING/SUCCESS/FAILED/STOPPED';
COMMENT ON COLUMN public.ai_agent_run.current_node IS '图编排当前节点标识';
COMMENT ON COLUMN public.ai_agent_run.state_json IS '运行期状态快照（JSON）';
COMMENT ON COLUMN public.ai_agent_run.create_time IS '开始时间';
COMMENT ON COLUMN public.ai_agent_run.update_time IS '更新时间';
COMMENT ON COLUMN public.ai_agent_run.user_message IS '用户输入';
COMMENT ON COLUMN public.ai_agent_run.response_summary IS '回复摘要（截断）';
COMMENT ON COLUMN public.ai_agent_run.duration_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN public.ai_agent_run.error_message IS '失败原因';
COMMENT ON COLUMN public.ai_agent_run.model_name IS '实际使用的模型 id';
COMMENT ON COLUMN public.ai_agent_run.workflow_type IS '工作流类型快照';
COMMENT ON COLUMN public.ai_agent_run.user_id IS '发起用户 id';
COMMENT ON COLUMN public.ai_agent_run.run_type IS '运行类型：TRIAL=试运行，CHAT=正式调用';
COMMENT ON COLUMN public.ai_agent_run.ttft_ms IS '首 token 延迟（毫秒）';
COMMENT ON COLUMN public.ai_agent_run.tpot_ms IS '每 token 平均耗时（毫秒）';
COMMENT ON COLUMN public.ai_agent_run.token_count IS '累计 token 数';
CREATE SEQUENCE IF NOT EXISTS public.ai_agent_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_agent_run_id_seq OWNED BY public.ai_agent_run.id;
CREATE TABLE IF NOT EXISTS public.ai_kg_sync_watermark (
    source_table character varying(64) NOT NULL,
    last_sync_at timestamp without time zone,
    max_source_time timestamp without time zone,
    last_status character varying(32),
    last_message character varying(500),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_kg_sync_watermark_pkey PRIMARY KEY (source_table)
);

COMMENT ON TABLE public.ai_kg_sync_watermark IS '知识图谱同步水位';
COMMENT ON COLUMN public.ai_kg_sync_watermark.source_table IS '同步来源表名';
COMMENT ON COLUMN public.ai_kg_sync_watermark.last_sync_at IS '最近同步完成时间';
COMMENT ON COLUMN public.ai_kg_sync_watermark.max_source_time IS '已同步数据的最大来源时间（增量水位）';
COMMENT ON COLUMN public.ai_kg_sync_watermark.last_status IS '最近同步结果：SUCCESS/FAILED';
COMMENT ON COLUMN public.ai_kg_sync_watermark.last_message IS '最近同步结果说明';
COMMENT ON COLUMN public.ai_kg_sync_watermark.update_time IS '更新时间';
CREATE TABLE IF NOT EXISTS public.ai_mcp_call_log (
    id bigint NOT NULL,
    direction character varying(8) NOT NULL,
    client_id bigint,
    upstream_id bigint,
    agent_id bigint,
    tool_name character varying(160) NOT NULL,
    success boolean DEFAULT true NOT NULL,
    error_message character varying(500),
    duration_ms integer,
    user_id character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_mcp_call_log_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.ai_mcp_call_log IS 'MCP 工具调用日志';
COMMENT ON COLUMN public.ai_mcp_call_log.id IS '主键';
COMMENT ON COLUMN public.ai_mcp_call_log.direction IS '调用方向：SERVE=外部 Client 调用本平台 Tool，CONSUME=本平台（Agent）调用上游 Tool（历史值 OUT/IN 由 db/patch_mcp_call_log_direction.sql 归一）';
COMMENT ON COLUMN public.ai_mcp_call_log.client_id IS '对外平面调用方 Client id（ai_mcp_client.id；平台 JWT 调用为 NULL）';
COMMENT ON COLUMN public.ai_mcp_call_log.upstream_id IS '被调用的上游 id（ai_mcp_upstream.id）';
COMMENT ON COLUMN public.ai_mcp_call_log.agent_id IS '关联智能体 id（可空）';
COMMENT ON COLUMN public.ai_mcp_call_log.tool_name IS '工具名称';
COMMENT ON COLUMN public.ai_mcp_call_log.success IS '是否成功：true=成功';
COMMENT ON COLUMN public.ai_mcp_call_log.error_message IS '失败原因';
COMMENT ON COLUMN public.ai_mcp_call_log.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN public.ai_mcp_call_log.user_id IS '调用用户 id';
COMMENT ON COLUMN public.ai_mcp_call_log.create_time IS '调用时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_mcp_call_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_mcp_call_log_id_seq OWNED BY public.ai_mcp_call_log.id;
CREATE TABLE IF NOT EXISTS public.ai_mcp_client (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    key_prefix character varying(32) NOT NULL,
    secret_hash character varying(128) NOT NULL,
    bound_user_id bigint NOT NULL,
    bound_username character varying(128),
    capabilities text DEFAULT '[]'::text NOT NULL,
    rpm_limit integer DEFAULT 60 NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    remark character varying(500),
    last_used_at timestamp without time zone,
    created_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_mcp_client_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_mcp_client_prefix UNIQUE (key_prefix)
);

COMMENT ON TABLE public.ai_mcp_client IS 'MCP Hub 客户端凭证（供外部调用本平台 MCP）';
COMMENT ON COLUMN public.ai_mcp_client.id IS '主键';
COMMENT ON COLUMN public.ai_mcp_client.name IS '客户端名称';
COMMENT ON COLUMN public.ai_mcp_client.key_prefix IS '密钥前缀（展示用，便于识别）';
COMMENT ON COLUMN public.ai_mcp_client.secret_hash IS '密钥哈希（SHA-256，不存明文）';
COMMENT ON COLUMN public.ai_mcp_client.bound_user_id IS '绑定用户 id（调用时以其身份鉴权）';
COMMENT ON COLUMN public.ai_mcp_client.bound_username IS '绑定用户登录名（冗余快照）';
COMMENT ON COLUMN public.ai_mcp_client.capabilities IS '允许的能力范围（JSON 数组）';
COMMENT ON COLUMN public.ai_mcp_client.rpm_limit IS '每分钟请求上限（空=不限）';
COMMENT ON COLUMN public.ai_mcp_client.status IS '状态：ENABLED/DISABLED';
COMMENT ON COLUMN public.ai_mcp_client.remark IS '备注';
COMMENT ON COLUMN public.ai_mcp_client.last_used_at IS '最近使用时间';
COMMENT ON COLUMN public.ai_mcp_client.created_by IS '创建人用户 id';
COMMENT ON COLUMN public.ai_mcp_client.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_mcp_client.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_mcp_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_mcp_client_id_seq OWNED BY public.ai_mcp_client.id;
CREATE TABLE IF NOT EXISTS public.ai_mcp_upstream (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    protocol character varying(32) DEFAULT 'STREAMABLE_HTTP'::character varying NOT NULL,
    base_url character varying(512) NOT NULL,
    endpoint character varying(128) DEFAULT '/mcp'::character varying NOT NULL,
    auth_header_enc text,
    request_timeout_ms integer DEFAULT 20000 NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    health_status character varying(16) DEFAULT 'UNKNOWN'::character varying NOT NULL,
    health_message character varying(500),
    last_probe_at timestamp without time zone,
    remark character varying(500),
    created_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_mcp_upstream_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_mcp_upstream_code UNIQUE (code)
);

COMMENT ON TABLE public.ai_mcp_upstream IS 'MCP 上游服务（本平台作为客户端接入）';
COMMENT ON COLUMN public.ai_mcp_upstream.id IS '主键';
COMMENT ON COLUMN public.ai_mcp_upstream.code IS '上游编码（唯一）';
COMMENT ON COLUMN public.ai_mcp_upstream.name IS '显示名称';
COMMENT ON COLUMN public.ai_mcp_upstream.protocol IS '协议：STREAMABLE_HTTP/SSE';
COMMENT ON COLUMN public.ai_mcp_upstream.base_url IS '服务基础地址';
COMMENT ON COLUMN public.ai_mcp_upstream.endpoint IS 'MCP 端点路径';
COMMENT ON COLUMN public.ai_mcp_upstream.auth_header_enc IS 'Authorization 头密文（AES-GCM，enc: 前缀）';
COMMENT ON COLUMN public.ai_mcp_upstream.request_timeout_ms IS '请求超时（毫秒）';
COMMENT ON COLUMN public.ai_mcp_upstream.status IS '状态：ENABLED/DISABLED';
COMMENT ON COLUMN public.ai_mcp_upstream.health_status IS '健康状态：UNKNOWN/UP/DOWN';
COMMENT ON COLUMN public.ai_mcp_upstream.health_message IS '最近探活结果说明';
COMMENT ON COLUMN public.ai_mcp_upstream.last_probe_at IS '最近探活时间';
COMMENT ON COLUMN public.ai_mcp_upstream.remark IS '备注';
COMMENT ON COLUMN public.ai_mcp_upstream.created_by IS '创建人用户 id';
COMMENT ON COLUMN public.ai_mcp_upstream.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_mcp_upstream.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_mcp_upstream_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_mcp_upstream_id_seq OWNED BY public.ai_mcp_upstream.id;
CREATE TABLE IF NOT EXISTS public.ai_mcp_upstream_tool (
    id bigint NOT NULL,
    upstream_id bigint NOT NULL,
    original_name character varying(128) NOT NULL,
    exposed_name character varying(160) NOT NULL,
    description text,
    enabled boolean DEFAULT true NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_mcp_upstream_tool_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_mcp_upstream_tool UNIQUE (upstream_id, original_name)
);

COMMENT ON TABLE public.ai_mcp_upstream_tool IS 'MCP 上游工具清单（含对外暴露名与启停）';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.id IS '主键';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.upstream_id IS '所属上游 id（ai_mcp_upstream.id）';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.original_name IS '上游原始工具名';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.exposed_name IS '对外暴露的工具名（可重命名）';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.description IS '工具说明';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.enabled IS '是否启用：true=对本平台可见';
COMMENT ON COLUMN public.ai_mcp_upstream_tool.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_mcp_upstream_tool_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_mcp_upstream_tool_id_seq OWNED BY public.ai_mcp_upstream_tool.id;
CREATE TABLE IF NOT EXISTS public.chat_session (
    id bigint NOT NULL,
    conversation_id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    scene character varying(32) DEFAULT 'CHAT'::character varying NOT NULL,
    title character varying(128) DEFAULT '新会话'::character varying NOT NULL,
    agent_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chat_session_pkey PRIMARY KEY (id),
    CONSTRAINT uk_chat_session_conversation UNIQUE (conversation_id)
);

COMMENT ON TABLE public.chat_session IS 'AI 会话（会话标题与 Agent 绑定）';
COMMENT ON COLUMN public.chat_session.id IS '主键';
COMMENT ON COLUMN public.chat_session.conversation_id IS '多轮会话 ID（与 qa_history / Memory 一致）';
COMMENT ON COLUMN public.chat_session.user_id IS '所属用户 id';
COMMENT ON COLUMN public.chat_session.scene IS '会话场景（如 CHAT、AGENT）';
COMMENT ON COLUMN public.chat_session.title IS '会话标题（取自首条提问）';
COMMENT ON COLUMN public.chat_session.agent_id IS '绑定智能体 id（可空）';
COMMENT ON COLUMN public.chat_session.create_time IS '创建时间';
COMMENT ON COLUMN public.chat_session.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.chat_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.chat_session_id_seq OWNED BY public.chat_session.id;
CREATE TABLE IF NOT EXISTS public.knowledges (
    id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    file_type character varying(50) NOT NULL,
    file_path character varying(500) NOT NULL,
    file_size bigint,
    content text,
    category_id bigint,
    upload_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    processed boolean DEFAULT false,
    CONSTRAINT knowledges_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.knowledges IS '知识库文档';
COMMENT ON COLUMN public.knowledges.id IS '主键';
COMMENT ON COLUMN public.knowledges.file_name IS '原始文件名';
COMMENT ON COLUMN public.knowledges.file_type IS '文件类型/扩展名';
COMMENT ON COLUMN public.knowledges.file_path IS '文件存储路径';
COMMENT ON COLUMN public.knowledges.file_size IS '文件大小（字节）';
COMMENT ON COLUMN public.knowledges.content IS '解析后的纯文本内容';
COMMENT ON COLUMN public.knowledges.category_id IS '所属分类 id（knowledges_category.id）';
COMMENT ON COLUMN public.knowledges.upload_time IS '上传时间';
COMMENT ON COLUMN public.knowledges.update_time IS '更新时间';
COMMENT ON COLUMN public.knowledges.processed IS '是否已切分向量化入库：true=已处理';
CREATE TABLE IF NOT EXISTS public.knowledges_category (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(500),
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT knowledges_category_pkey PRIMARY KEY (id),
    CONSTRAINT uk_knowledges_category_code UNIQUE (code)
);

COMMENT ON TABLE public.knowledges_category IS '知识库分类';
COMMENT ON COLUMN public.knowledges_category.id IS '主键';
COMMENT ON COLUMN public.knowledges_category.code IS '唯一编码';
COMMENT ON COLUMN public.knowledges_category.name IS '分类名称';
COMMENT ON COLUMN public.knowledges_category.description IS '分类描述';
COMMENT ON COLUMN public.knowledges_category.sort_order IS '排序值（升序）';
COMMENT ON COLUMN public.knowledges_category.status IS '状态：1=启用，0=停用';
COMMENT ON COLUMN public.knowledges_category.create_time IS '创建时间';
COMMENT ON COLUMN public.knowledges_category.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.knowledges_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.knowledges_category_id_seq OWNED BY public.knowledges_category.id;
CREATE SEQUENCE IF NOT EXISTS public.knowledges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.knowledges_id_seq OWNED BY public.knowledges.id;
CREATE TABLE IF NOT EXISTS public.open_app (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    allowed_scopes text DEFAULT '[]'::text NOT NULL,
    remark character varying(500),
    created_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    access_key character varying(64),
    secret_key_enc text,
    aksk_generated_at timestamp without time zone,
    last_used_at timestamp without time zone,
    CONSTRAINT open_app_pkey PRIMARY KEY (id),
    CONSTRAINT uk_open_app_code UNIQUE (code)
);

COMMENT ON TABLE public.open_app IS '开放应用（知枢 OpenAPI 调用方）';
COMMENT ON COLUMN public.open_app.id IS '主键';
COMMENT ON COLUMN public.open_app.code IS '应用编码（唯一）';
COMMENT ON COLUMN public.open_app.name IS '应用名称';
COMMENT ON COLUMN public.open_app.status IS '状态：ENABLED/DISABLED';
COMMENT ON COLUMN public.open_app.allowed_scopes IS '允许的权限范围（逗号分隔的 scope 列表）';
COMMENT ON COLUMN public.open_app.remark IS '备注';
COMMENT ON COLUMN public.open_app.created_by IS '创建人用户 id';
COMMENT ON COLUMN public.open_app.create_time IS '创建时间';
COMMENT ON COLUMN public.open_app.update_time IS '更新时间';
COMMENT ON COLUMN public.open_app.access_key IS 'Access Key（可展示）';
COMMENT ON COLUMN public.open_app.secret_key_enc IS 'Secret Key 密文（AES-GCM，enc: 前缀）';
COMMENT ON COLUMN public.open_app.aksk_generated_at IS 'AK/SK 最近生成时间';
COMMENT ON COLUMN public.open_app.last_used_at IS '最近调用时间';
CREATE TABLE IF NOT EXISTS public.open_app_credential (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    key_prefix character varying(32) NOT NULL,
    secret_hash character varying(128) NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    expires_at timestamp without time zone,
    last_used_at timestamp without time zone,
    created_by character varying(64),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT open_app_credential_pkey PRIMARY KEY (id),
    CONSTRAINT uk_open_app_credential_prefix UNIQUE (key_prefix)
);

COMMENT ON TABLE public.open_app_credential IS '开放 API 应用凭证；secret_hash 为 SHA-256 十六进制';
COMMENT ON COLUMN public.open_app_credential.id IS '主键';
COMMENT ON COLUMN public.open_app_credential.app_id IS '所属应用 id（open_app.id）';
COMMENT ON COLUMN public.open_app_credential.key_prefix IS '凭证前缀（展示用）';
COMMENT ON COLUMN public.open_app_credential.secret_hash IS '凭证哈希（SHA-256 十六进制，不存明文）';
COMMENT ON COLUMN public.open_app_credential.status IS '状态：ACTIVE/REVOKED/EXPIRED';
COMMENT ON COLUMN public.open_app_credential.expires_at IS '过期时间（空=长期有效）';
COMMENT ON COLUMN public.open_app_credential.last_used_at IS '最近使用时间';
COMMENT ON COLUMN public.open_app_credential.created_by IS '创建人用户 id';
COMMENT ON COLUMN public.open_app_credential.create_time IS '创建时间';
COMMENT ON COLUMN public.open_app_credential.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.open_app_credential_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.open_app_credential_id_seq OWNED BY public.open_app_credential.id;
CREATE SEQUENCE IF NOT EXISTS public.open_app_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.open_app_id_seq OWNED BY public.open_app.id;
CREATE TABLE IF NOT EXISTS public.qa_history (
    id bigint NOT NULL,
    user_id character varying(64) NOT NULL,
    scene character varying(32) NOT NULL,
    question text NOT NULL,
    answer text NOT NULL,
    model character varying(64),
    document_id bigint,
    conversation_id character varying(64),
    agent_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT qa_history_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE public.qa_history IS '知识库问答历史';
COMMENT ON COLUMN public.qa_history.id IS '主键';
COMMENT ON COLUMN public.qa_history.user_id IS '提问用户 id';
COMMENT ON COLUMN public.qa_history.scene IS '问答场景（如 KNOWLEDGE、AGENT）';
COMMENT ON COLUMN public.qa_history.question IS '用户问题';
COMMENT ON COLUMN public.qa_history.answer IS '模型回答';
COMMENT ON COLUMN public.qa_history.model IS '本次使用的模型 id';
COMMENT ON COLUMN public.qa_history.document_id IS '关联知识库文档 id（可空）';
COMMENT ON COLUMN public.qa_history.conversation_id IS '多轮会话 id';
COMMENT ON COLUMN public.qa_history.agent_id IS '使用的智能体 id（可空）';
COMMENT ON COLUMN public.qa_history.create_time IS '创建时间';
CREATE SEQUENCE IF NOT EXISTS public.qa_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.qa_history_id_seq OWNED BY public.qa_history.id;
ALTER TABLE ONLY public.ai_agent ALTER COLUMN id SET DEFAULT nextval('public.ai_agent_id_seq'::regclass);
ALTER TABLE ONLY public.ai_agent_mcp_upstream ALTER COLUMN id SET DEFAULT nextval('public.ai_agent_mcp_upstream_id_seq'::regclass);
ALTER TABLE ONLY public.ai_agent_run ALTER COLUMN id SET DEFAULT nextval('public.ai_agent_run_id_seq'::regclass);
ALTER TABLE ONLY public.ai_mcp_call_log ALTER COLUMN id SET DEFAULT nextval('public.ai_mcp_call_log_id_seq'::regclass);
ALTER TABLE ONLY public.ai_mcp_client ALTER COLUMN id SET DEFAULT nextval('public.ai_mcp_client_id_seq'::regclass);
ALTER TABLE ONLY public.ai_mcp_upstream ALTER COLUMN id SET DEFAULT nextval('public.ai_mcp_upstream_id_seq'::regclass);
ALTER TABLE ONLY public.ai_mcp_upstream_tool ALTER COLUMN id SET DEFAULT nextval('public.ai_mcp_upstream_tool_id_seq'::regclass);
ALTER TABLE ONLY public.chat_session ALTER COLUMN id SET DEFAULT nextval('public.chat_session_id_seq'::regclass);
ALTER TABLE ONLY public.knowledges ALTER COLUMN id SET DEFAULT nextval('public.knowledges_id_seq'::regclass);
ALTER TABLE ONLY public.knowledges_category ALTER COLUMN id SET DEFAULT nextval('public.knowledges_category_id_seq'::regclass);
ALTER TABLE ONLY public.open_app ALTER COLUMN id SET DEFAULT nextval('public.open_app_id_seq'::regclass);
ALTER TABLE ONLY public.open_app_credential ALTER COLUMN id SET DEFAULT nextval('public.open_app_credential_id_seq'::regclass);
ALTER TABLE ONLY public.qa_history ALTER COLUMN id SET DEFAULT nextval('public.qa_history_id_seq'::regclass);
CREATE INDEX IF NOT EXISTS idx_agent_run_status ON public.ai_agent_run USING btree (status);
CREATE INDEX IF NOT EXISTS idx_agent_run_time ON public.ai_agent_run USING btree (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_agent_run_user ON public.ai_agent_run USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_default ON public.ai_agent USING btree (is_default) WHERE (is_default = true);
CREATE INDEX IF NOT EXISTS idx_ai_agent_mcp_upstream_agent ON public.ai_agent_mcp_upstream USING btree (agent_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_agent ON public.ai_agent_run USING btree (agent_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_agent_run_conv ON public.ai_agent_run USING btree (conversation_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_status ON public.ai_agent USING btree (status);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_call_log_dir ON public.ai_mcp_call_log USING btree (direction, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_call_log_time ON public.ai_mcp_call_log USING btree (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_client_status ON public.ai_mcp_client USING btree (status);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_upstream_status ON public.ai_mcp_upstream USING btree (status);
CREATE INDEX IF NOT EXISTS idx_ai_mcp_upstream_tool_up ON public.ai_mcp_upstream_tool USING btree (upstream_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_scene_time ON public.chat_session USING btree (user_id, scene, update_time DESC);
CREATE INDEX IF NOT EXISTS idx_knowledges_category ON public.knowledges USING btree (category_id);
CREATE INDEX IF NOT EXISTS idx_knowledges_category_status ON public.knowledges_category USING btree (status, sort_order);
CREATE INDEX IF NOT EXISTS idx_knowledges_file_name ON public.knowledges USING btree (file_name);
CREATE INDEX IF NOT EXISTS idx_knowledges_processed ON public.knowledges USING btree (processed);
CREATE INDEX IF NOT EXISTS idx_knowledges_upload_time ON public.knowledges USING btree (upload_time DESC);
CREATE INDEX IF NOT EXISTS idx_open_app_credential_app ON public.open_app_credential USING btree (app_id, status);
CREATE INDEX IF NOT EXISTS idx_open_app_status ON public.open_app USING btree (status);
CREATE INDEX IF NOT EXISTS idx_qa_history_agent ON public.qa_history USING btree (agent_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_conversation ON public.qa_history USING btree (conversation_id);
CREATE INDEX IF NOT EXISTS idx_qa_history_user_scene_time ON public.qa_history USING btree (user_id, scene, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_open_app_access_key ON public.open_app USING btree (access_key);

