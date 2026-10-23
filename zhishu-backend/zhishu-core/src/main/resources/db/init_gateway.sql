-- =============================================================================
-- 模型网关数据库脚本（供应商 / 模型 / 额度 / 调用台账 / 结构对齐）
--
-- 来源：以开发库实际结构导出后幂等化，与 cn.datafuturex.zhishu.gateway.* 实体一致。
-- 幂等：可重复执行；仅初始化 DEFAULT 额度行（模型与供应商由管理面录入）。
-- 执行：由 GatewaySchemaInitializer 启动期自动执行（脚本内无触发器/函数定义，可被分号分隔加载）
-- =============================================================================

-- ---------- 表结构与字段备注（每表 DDL 后紧跟 COMMENT）----------
CREATE TABLE IF NOT EXISTS public.ai_gateway_model (
    id bigint NOT NULL,
    model_id character varying(64) NOT NULL,
    display_name character varying(64) NOT NULL,
    provider_id bigint NOT NULL,
    upstream_model character varying(128) NOT NULL,
    context_window integer,
    max_output_tokens integer,
    input_modalities character varying(64) DEFAULT 'text'::character varying NOT NULL,
    system_prompt_update character varying(16) DEFAULT 'in-history'::character varying NOT NULL,
    reasoning_efforts character varying(64),
    default_reasoning_effort character varying(16),
    client_requirements text,
    extra_params text,
    is_default boolean DEFAULT false NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    remark character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    model_type character varying(16) DEFAULT 'CHAT'::character varying NOT NULL,
    temperature numeric(3,2),
    top_p numeric(3,2),
    max_tokens integer,
    CONSTRAINT ai_gateway_model_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_gateway_model_model_id UNIQUE (model_id)
);

COMMENT ON TABLE public.ai_gateway_model IS '网关模型（对外模型 id 与上游模型的映射及能力参数）';
COMMENT ON COLUMN public.ai_gateway_model.id IS '主键';
COMMENT ON COLUMN public.ai_gateway_model.model_id IS '对外模型 id（客户端 model 字段取值）';
COMMENT ON COLUMN public.ai_gateway_model.display_name IS '展示名称';
COMMENT ON COLUMN public.ai_gateway_model.provider_id IS '所属供应商 id（ai_model_provider.id）';
COMMENT ON COLUMN public.ai_gateway_model.upstream_model IS '上游真实模型名（转发时使用）';
COMMENT ON COLUMN public.ai_gateway_model.context_window IS '上下文窗口 token 数';
COMMENT ON COLUMN public.ai_gateway_model.max_output_tokens IS '最大输出 token 数';
COMMENT ON COLUMN public.ai_gateway_model.input_modalities IS '输入模态（JSON 数组，如 ["text","image"]）';
COMMENT ON COLUMN public.ai_gateway_model.system_prompt_update IS '系统提示词处理策略（in-history=历史消息中，leading-only=仅置顶）';
COMMENT ON COLUMN public.ai_gateway_model.reasoning_efforts IS '支持的推理档位（JSON 数组，如 ["low","medium","high"]）';
COMMENT ON COLUMN public.ai_gateway_model.default_reasoning_effort IS '默认推理档位';
COMMENT ON COLUMN public.ai_gateway_model.client_requirements IS '客户端能力要求（JSON，如是否必须流式）';
COMMENT ON COLUMN public.ai_gateway_model.extra_params IS '透传给上游的额外参数（JSON）';
COMMENT ON COLUMN public.ai_gateway_model.is_default IS '是否网关默认模型（全局至多一条）';
COMMENT ON COLUMN public.ai_gateway_model.status IS '状态：ENABLED=启用，DISABLED=停用';
COMMENT ON COLUMN public.ai_gateway_model.sort_order IS '排序值（升序）';
COMMENT ON COLUMN public.ai_gateway_model.remark IS '备注';
COMMENT ON COLUMN public.ai_gateway_model.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_gateway_model.update_time IS '更新时间';
COMMENT ON COLUMN public.ai_gateway_model.model_type IS '模型类型：CHAT=对话，EMBEDDING=向量';
COMMENT ON COLUMN public.ai_gateway_model.temperature IS '默认采样温度（请求未指定时使用）';
COMMENT ON COLUMN public.ai_gateway_model.top_p IS '默认核采样阈值（请求未指定时使用）';
COMMENT ON COLUMN public.ai_gateway_model.max_tokens IS '默认最大输出 token';
CREATE SEQUENCE IF NOT EXISTS public.ai_gateway_model_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_gateway_model_id_seq OWNED BY public.ai_gateway_model.id;
CREATE TABLE IF NOT EXISTS public.ai_gateway_quota (
    id bigint NOT NULL,
    subject_type character varying(16) NOT NULL,
    subject_key character varying(64) NOT NULL,
    period_kind character varying(16) DEFAULT 'MONTHLY'::character varying NOT NULL,
    token_limit bigint,
    used_tokens bigint DEFAULT 0 NOT NULL,
    period_start timestamp without time zone NOT NULL,
    period_end timestamp without time zone NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    remark character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ai_gateway_quota_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_gateway_quota_subject UNIQUE (subject_type, subject_key)
);

COMMENT ON TABLE public.ai_gateway_quota IS '调用额度策略（用户/角色/全局默认三级）';
COMMENT ON COLUMN public.ai_gateway_quota.id IS '主键';
COMMENT ON COLUMN public.ai_gateway_quota.subject_type IS '额度主体类型：USER=按用户，ROLE=按角色，DEFAULT=全局默认';
COMMENT ON COLUMN public.ai_gateway_quota.subject_key IS '主体标识（用户 id 或角色编码；DEFAULT 行可为空）';
COMMENT ON COLUMN public.ai_gateway_quota.period_kind IS '统计周期：DAILY/WEEKLY/MONTHLY/TOTAL';
COMMENT ON COLUMN public.ai_gateway_quota.token_limit IS '周期内 token 上限';
COMMENT ON COLUMN public.ai_gateway_quota.used_tokens IS '当前周期已用 token 数';
COMMENT ON COLUMN public.ai_gateway_quota.period_start IS '当前周期开始时间';
COMMENT ON COLUMN public.ai_gateway_quota.period_end IS '当前周期结束时间（滚动重置依据）';
COMMENT ON COLUMN public.ai_gateway_quota.status IS '状态：ENABLED=生效，DISABLED=停用';
COMMENT ON COLUMN public.ai_gateway_quota.remark IS '备注';
COMMENT ON COLUMN public.ai_gateway_quota.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_gateway_quota.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_gateway_quota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_gateway_quota_id_seq OWNED BY public.ai_gateway_quota.id;
CREATE TABLE IF NOT EXISTS public.ai_gateway_usage (
    id bigint NOT NULL,
    request_id character varying(64) NOT NULL,
    user_id character varying(64),
    username character varying(64),
    channel character varying(16) DEFAULT 'DESKTOP'::character varying NOT NULL,
    model_id character varying(64) NOT NULL,
    provider_id bigint,
    upstream_model character varying(128),
    stream boolean DEFAULT false NOT NULL,
    status character varying(16) NOT NULL,
    http_status integer,
    reserved_tokens bigint DEFAULT 0 NOT NULL,
    prompt_tokens integer,
    completion_tokens integer,
    total_tokens integer,
    cached_tokens integer,
    reasoning_tokens integer,
    estimated boolean DEFAULT false NOT NULL,
    latency_ms integer,
    ttft_ms integer,
    client_name character varying(32),
    client_version character varying(32),
    ip character varying(64),
    user_agent character varying(255),
    error_code character varying(64),
    error_message character varying(500),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ai_gateway_usage_pkey PRIMARY KEY (id),
    CONSTRAINT uk_ai_gateway_usage_request UNIQUE (request_id)
);

COMMENT ON TABLE public.ai_gateway_usage IS '网关调用台账（预留与结算、用量与延迟）';
COMMENT ON COLUMN public.ai_gateway_usage.id IS '主键';
COMMENT ON COLUMN public.ai_gateway_usage.request_id IS '单次请求唯一标识（网关生成）';
COMMENT ON COLUMN public.ai_gateway_usage.user_id IS '调用用户 id（匿名时可空）';
COMMENT ON COLUMN public.ai_gateway_usage.username IS '调用用户登录名';
COMMENT ON COLUMN public.ai_gateway_usage.channel IS '调用通道：CONSOLE=控制台，DESKTOP=桌面端，AGENT=内部智能体，OPENAPI=开放接口';
COMMENT ON COLUMN public.ai_gateway_usage.model_id IS '对外模型 id（ai_gateway_model.model_id）';
COMMENT ON COLUMN public.ai_gateway_usage.provider_id IS '供应商 id';
COMMENT ON COLUMN public.ai_gateway_usage.upstream_model IS '上游真实模型名';
COMMENT ON COLUMN public.ai_gateway_usage.stream IS '是否流式调用：true=SSE';
COMMENT ON COLUMN public.ai_gateway_usage.status IS '调用状态：SUCCESS/FAILED/CANCELED/REJECTED';
COMMENT ON COLUMN public.ai_gateway_usage.http_status IS '返回给客户端的 HTTP 状态码';
COMMENT ON COLUMN public.ai_gateway_usage.reserved_tokens IS '调用前预留的 token 数（额度控制用）';
COMMENT ON COLUMN public.ai_gateway_usage.prompt_tokens IS '输入 token 数';
COMMENT ON COLUMN public.ai_gateway_usage.completion_tokens IS '输出 token 数';
COMMENT ON COLUMN public.ai_gateway_usage.total_tokens IS '总 token 数（结算依据）';
COMMENT ON COLUMN public.ai_gateway_usage.cached_tokens IS '命中缓存的 token 数';
COMMENT ON COLUMN public.ai_gateway_usage.reasoning_tokens IS '推理过程 token 数';
COMMENT ON COLUMN public.ai_gateway_usage.estimated IS '用量是否为估算值：true=上游未返回 usage，由本地估算';
COMMENT ON COLUMN public.ai_gateway_usage.latency_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN public.ai_gateway_usage.ttft_ms IS '首 token 延迟（毫秒，流式）';
COMMENT ON COLUMN public.ai_gateway_usage.client_name IS '客户端名称';
COMMENT ON COLUMN public.ai_gateway_usage.client_version IS '客户端版本';
COMMENT ON COLUMN public.ai_gateway_usage.ip IS '客户端 IP';
COMMENT ON COLUMN public.ai_gateway_usage.user_agent IS 'User-Agent';
COMMENT ON COLUMN public.ai_gateway_usage.error_code IS '错误码（上游或网关定义）';
COMMENT ON COLUMN public.ai_gateway_usage.error_message IS '错误信息（截断）';
COMMENT ON COLUMN public.ai_gateway_usage.create_time IS '请求开始时间';
COMMENT ON COLUMN public.ai_gateway_usage.update_time IS '结算/更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_gateway_usage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_gateway_usage_id_seq OWNED BY public.ai_gateway_usage.id;
CREATE TABLE IF NOT EXISTS public.ai_model_provider (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    provider_key character varying(64) NOT NULL,
    base_url character varying(512) NOT NULL,
    api_key_enc text,
    api_key_masked character varying(128),
    is_default boolean DEFAULT false NOT NULL,
    status character varying(16) DEFAULT 'ENABLED'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    remark character varying(500),
    create_time timestamp without time zone DEFAULT now() NOT NULL,
    update_time timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ai_model_provider_pkey PRIMARY KEY (id),
    CONSTRAINT ai_model_provider_provider_key_key UNIQUE (provider_key)
);

COMMENT ON TABLE public.ai_model_provider IS '模型供应商（上游 Base URL 与凭据）';
COMMENT ON COLUMN public.ai_model_provider.id IS '主键';
COMMENT ON COLUMN public.ai_model_provider.name IS '供应商名称';
COMMENT ON COLUMN public.ai_model_provider.provider_key IS '供应商标识（唯一，如 deepseek）';
COMMENT ON COLUMN public.ai_model_provider.base_url IS '上游 OpenAI 兼容 Base URL';
COMMENT ON COLUMN public.ai_model_provider.api_key_enc IS 'API Key 密文（AES-GCM，enc: 前缀）';
COMMENT ON COLUMN public.ai_model_provider.api_key_masked IS 'API Key 脱敏展示串';
COMMENT ON COLUMN public.ai_model_provider.is_default IS '是否默认供应商（全局至多一条）';
COMMENT ON COLUMN public.ai_model_provider.status IS '状态：ENABLED=启用，DISABLED=停用';
COMMENT ON COLUMN public.ai_model_provider.sort_order IS '排序值（升序）';
COMMENT ON COLUMN public.ai_model_provider.remark IS '备注';
COMMENT ON COLUMN public.ai_model_provider.create_time IS '创建时间';
COMMENT ON COLUMN public.ai_model_provider.update_time IS '更新时间';
CREATE SEQUENCE IF NOT EXISTS public.ai_model_provider_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.ai_model_provider_id_seq OWNED BY public.ai_model_provider.id;
ALTER TABLE ONLY public.ai_gateway_model ALTER COLUMN id SET DEFAULT nextval('public.ai_gateway_model_id_seq'::regclass);
ALTER TABLE ONLY public.ai_gateway_quota ALTER COLUMN id SET DEFAULT nextval('public.ai_gateway_quota_id_seq'::regclass);
ALTER TABLE ONLY public.ai_gateway_usage ALTER COLUMN id SET DEFAULT nextval('public.ai_gateway_usage_id_seq'::regclass);
ALTER TABLE ONLY public.ai_model_provider ALTER COLUMN id SET DEFAULT nextval('public.ai_model_provider_id_seq'::regclass);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_status ON public.ai_gateway_model USING btree (status, sort_order);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_type ON public.ai_gateway_model USING btree (model_type, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_quota_type ON public.ai_gateway_quota USING btree (subject_type, status);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_channel ON public.ai_gateway_usage USING btree (channel, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_model ON public.ai_gateway_usage USING btree (model_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_time ON public.ai_gateway_usage USING btree (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_user ON public.ai_gateway_usage USING btree (user_id, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_chat ON public.ai_gateway_model USING btree (is_default) WHERE ((is_default = true) AND ((model_type)::text = 'CHAT'::text));
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_embedding ON public.ai_gateway_model USING btree (is_default) WHERE ((is_default = true) AND ((model_type)::text = 'EMBEDDING'::text));
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_provider_upstream ON public.ai_gateway_model USING btree (provider_id, model_type, upstream_model);

-- ---------- 种子数据（结构/配置类，幂等）----------
SELECT pg_catalog.set_config('search_path', '', false);
INSERT INTO public.ai_gateway_quota (id, subject_type, subject_key, period_kind, token_limit, used_tokens, period_start, period_end, status, remark, create_time, update_time) VALUES (1, 'DEFAULT', '*', 'MONTHLY', 5000000, 0, '2026-10-01 00:00:00', '2026-11-01 00:00:00', 'ENABLED', '缺省月额度', '2026-10-22 06:24:34.959529', '2026-10-22 06:24:34.959529') ON CONFLICT DO NOTHING;
INSERT INTO public.ai_gateway_quota (id, subject_type, subject_key, period_kind, token_limit, used_tokens, period_start, period_end, status, remark, create_time, update_time) VALUES (13, 'USER', 'admin', 'MONTHLY', 5000000, 3475, '2026-10-01 00:00:00', '2026-11-01 00:00:00', 'ENABLED', '首次调用自动创建', '2026-10-23 10:01:43.591703', '2026-10-23 10:54:32.435722') ON CONFLICT DO NOTHING;
SELECT pg_catalog.setval('public.ai_gateway_quota_id_seq', 82, true);

