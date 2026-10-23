# 模型网关（zhishu-model-gateway）—— 服务端设计方案

> 状态：**设计（未实施）**。
> 对端契约来源：`lets-work-desktop/custom/plugins/dsh-plugin-model-gateway/docs/design.md` 第 3 节（网关端职责与接口契约）。
> 本文只描述设计，不含实现代码；DDL / 菜单补丁 / 迁移脚本为设计定稿内容，实施阶段按 P1~P5 落地。

## 1. 概述

### 1.1 目标

服务端作为**统一模型网关**：

- **统一协议**：只暴露 OpenAI 兼容的 `/v1` 面（`/v1/models`、`/v1/chat/completions`、`/v1/quota`），供应商差异在网关内部消化。
- **下发目录**：`/v1/models` 返回模型清单 + 能力元数据（上下文窗口、输出上限、模态、reasoning 档位、客户端版本门禁）。
- **权威计量**：网关在推理链路上自己数 token、按登录用户归账、额度不足直接拒绝（HTTP 429 + OpenAI 额度错误体）。
- **凭据集中**：真实供应商 Key 只存服务端，客户端只持有自己的知枢 JWT。
- **内部统一**：平台现有「模型设置 + Agent 绑定模型」收敛到网关目录；控制台 Agent / 知识问答 / AI 简报全部经网关转发，用量计入当前登录用户。

### 1.2 边界

| 项 | 结论 |
|---|---|
| 上游协议 | 本期仅 **OpenAI 兼容透传**；Anthropic Messages 等异构协议只留 `UpstreamProtocolAdapter` SPI |
| 内部消费 | Agent / 问答 / 简报 **经网关 HTTP**（同进程回环），与桌面端共用一条数据面 |
| embedding | **不纳入**网关（向量库、知识入库继续直连默认上游） |
| MCP 上游工具 | 不经网关 |
| 额度粒度 | 按 **用户月度 token**，角色给默认值、用户可覆盖；不做按模型的细粒度额度 |
| prompt 正文 | 默认**不入库**（仅长度 / 摘要哈希，可配置打开） |
| 外部依赖 | 不引入 Redis、Prometheus 等新中间件 |

### 1.3 与现有实现的关系

| 现有组件 | 现状 | 网关落地后 |
|---|---|---|
| `ai_model_provider` + 「模型设置」页 | 上游连接（BaseURL / 加密 Key / 默认生成参数），被 Agent 运行时直接使用 | 降级为**网关的上游连接配置**，仍由该页面维护 |
| `ModelProviderRegistry` | 按 `provider_id` 直连上游构建 `ChatModel` | 仅保留上游配置读取与 embedding；chat 路径改由 `GatewayChatModelRegistry` 提供 |
| `ai_agent.model` / `ai_agent.model_provider_id` | 语义是「供应商模型名 + 供应商 ID」 | `model` 语义变为**网关 model_id**；`model_provider_id` 弃用置空 |
| `ModelConfigPort.currentChatModel()` | 读旧单例 `ai_model_config` | 返回网关默认模型 `model_id`（legacy 回退保留一个版本） |
| 桌面客户端 | 无统一模型入口 | 直接对接 `/v1`，只有一条路由与一个 token |

## 2. 总体架构

### 2.1 数据流

```
桌面客户端（dsh-plugin-model-gateway）
    │  Authorization: Bearer <当前用户知枢 JWT>
    ▼
┌────────────────────── zhishu-core 进程 ──────────────────────┐
│  /v1/** GatewayAuthFilter → GatewayChatController            │
│      │                                                       │
│  控制台 Agent / 问答 / 简报                                   │
│      │ ChatClient(OpenAiChatModel, baseUrl=<本机>/v1)        │
│      └──► /v1/chat/completions（同一条链路）                  │
│                                                              │
│  网关内部：校验 JWT → 解析用户 → 目录查模型 → 额度预留         │
│            → 选上游 provider → 协议适配 → 转发（含 SSE）       │
│            → 按 usage 结算 → 写台账                            │
└───────────────────────────┬──────────────────────────────────┘
                            ▼
              真实供应商（ai_model_provider.base_url）
```

### 2.2 模块落位

新增 Maven 模块 **`zhishu-model-gateway`**，Java 包名 `cn.datafuturex.zhishu.gateway`，由 `zhishu-core` 同进程装配（与 `zhishu-desktop` 一致）。

落位理由：

1. `AiGlobalExceptionHandler` 的 `basePackages = "cn.datafuturex.zhishu.ai"` 会把异常包成 `Result`，OpenAI 协议面必须不受其影响。
2. 协议面与 AI 业务面解耦，避免被 `zhishu-ai` 的 Spring Modulith 模块边界约束。
3. 依赖方向单向：`zhishu-core` → `zhishu-model-gateway` → {`zhishu-security`, `zhishu-ai`}，无环。

### 2.3 工程改动清单

| 文件 | 改动 |
|---|---|
| `zhishu-backend/pom.xml` | `<modules>` 与 `<dependencyManagement>` 增 `zhishu-model-gateway` |
| `zhishu-backend/zhishu-model-gateway/pom.xml` | 新建；依赖 `zhishu-security`、`zhishu-ai`、`spring-boot-starter-webmvc`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot4-starter`、`caffeine`、`swagger-annotations`、`lombok` |
| `zhishu-backend/zhishu-core/pom.xml` | 增 `zhishu-model-gateway` 依赖 |
| `zhishu-ai/.../modelconfig/api/ModelUpstreamPort.java` | 新增跨模块端口（modelconfig 的 `api` NamedInterface） |
| `zhishu-ai/.../modelconfig/service/impl/ModelUpstreamService.java` | 新增实现：复用 Key 解密，返回上游 baseUrl / 明文 key / 模型 / 生成参数 |
| `zhishu-ai/.../gatewayclient/`（新应用模块） | `GatewayChatModelRegistry`、`GatewayModelCatalogClient`、`GatewayServiceTokenProvider`、`GatewayInternalProperties`、`package-info` |
| `zhishu-ai/.../shared/UserContext.java` | `Snapshot` 增 `token`，供内部调用透传当前用户 JWT |
| `zhishu-ai/.../platform/config/UserContextInterceptor.java` | 从 `Authorization` 头读取原始 bearer 写入 `UserContext` |
| `zhishu-security/.../config/security/SecurityConfig.java` | `permitAll("/v1/**")`（鉴权交给网关过滤器） |
| `zhishu-security/.../config/security/JwtAuthenticationFilter.java` | `shouldNotFilter` 增 `/v1/`，避免每请求查权限表与重复解析 |
| `zhishu-core/src/main/resources/db/init_gateway.sql` | 新建：网关表 DDL |
| `zhishu-core/src/main/resources/db/patch_menu_model_gateway.sql` | 新建：菜单 IA 改造 |
| `zhishu-core/src/main/resources/db/patch_agent_model_to_gateway.sql` | 新建：provider → 网关目录 + `ai_agent.model` 回填 |
| `zhishu-core/src/main/resources/application.yml`（dev / prod） | 增 `zhishu.gateway.*` |
| `zhishu-frontend/src/views/gateway/*.vue`、`src/api/gateway.ts` | 新建 4 个页面 + API 封装 |
| `zhishu-frontend/src/views/ai/AgentManage.vue`、`src/api/aiAgent.ts` | 模型下拉改用网关目录，移除 `modelProviderId` |

### 2.4 包结构

```
zhishu-model-gateway/
└── src/main/java/cn/datafuturex/zhishu/gateway/
    ├── auth/        GatewayAuthFilter, GatewayPrincipal, GatewayAuthService
    ├── compat/      OpenAiError(+Writer), SseFrameCodec, TokenEstimator, ChatCompletionRequest
    ├── catalog/     GatewayModel{Entity,Mapper,Service}, CatalogCache
    ├── quota/       GatewayQuota{Entity,Mapper,Service}, QuotaPeriod
    ├── usage/       GatewayUsage{Entity,Mapper,Service}
    ├── upstream/    UpstreamProtocolAdapter(SPI), OpenAiCompletionsAdapter, UpstreamHttpClient, UpstreamTarget
    ├── api/       Gateway{Chat,Models,Quota}Controller（对外 /v1 协议面）；admin/controller/ 为管理面
    ├── config/      GatewayProperties, GatewaySchemaInitializer, GatewayFilterConfig, GatewayExceptionAdvice
    └── domain/{entity,dto,vo}
```

## 3. 对外契约

baseURL = `<知枢后端根>`（默认 `http://192.168.11.14:8180`），客户端拼 `/v1`。

### 3.1 `GET /v1/models`

字段名固定 `snake_case`（Jackson 3 默认 camelCase，需 `@JsonProperty` 显式标注）。

```jsonc
{
  "object": "list",
  "default": { "model": "gw-dashscope-qwen-plus" },
  "data": [
    {
      "id": "gw-dashscope-qwen-plus",
      "object": "model",
      "name": "通义千问 Plus",
      "owned_by": "dashscope",
      "created": 1730000000,
      "context_window": 131072,
      "max_output_tokens": 8192,
      "input_modalities": ["text"],
      "system_prompt_update": "in-history",
      "reasoning": { "efforts": ["low", "high"], "default_effort": "high" },
      "client_requirements": { "LetsWork": { "min_version": "0.2.1" } }
    }
  ]
}
```

规则：

- 仅返回 `status=ENABLED` 且其 `ai_model_provider` 同样 `ENABLED` 的模型。
- `context_window` / `max_output_tokens` / `reasoning` / `client_requirements` 缺省时不输出该字段（客户端按保守默认运行）。
- `owned_by` 取 `ai_model_provider.provider_key`，仅展示与审计用，不参与客户端逻辑。
- `default` 取 `ai_gateway_model.is_default = true` 的行；无默认行时省略该字段。

### 3.2 `POST /v1/chat/completions`

标准 OpenAI Chat Completions 入参（`model`、`messages`、`tools`、`stream`、`max_tokens`、`temperature`、`top_p`、`reasoning_effort`、`stream_options`…）。

行为：

1. 校验 JWT → 解析用户 → 校验用户启用状态。
2. 查目录得 `model_id → (provider_id, upstream_model, extra_params)`；不可见/停用返回 404。
3. 额度预留（见第 6 节），不足直接 429，**不转发上游**。
4. 协议适配并注入服务端供应商 Key，转发上游（流式 / 非流式）。
5. 流式：以 OpenAI 兼容 SSE 回帧，`data: {chunk}` … `data: [DONE]`，**末帧带 `usage`**。
6. 按响应 `usage` 结算额度并写台账。

请求体上限 8MB（`zhishu.gateway.upstream.max-request-bytes`）。

### 3.3 `GET /v1/quota`

```jsonc
{
  "unit": "tokens",
  "period": { "kind": "monthly", "reset_at": "2026-11-01T00:00:00Z" },
  "subject": { "type": "USER", "key": "admin" },
  "limit": 5000000,
  "used": 3200000,
  "remaining": 1800000
}
```

只读展示用；拦截本身不依赖该接口。

### 3.4 错误体

```jsonc
{ "error": { "type": "quota_exceeded", "code": "insufficient_quota",
             "message": "本月 token 额度已用尽，2026-11-01 重置",
             "reset_at": "2026-11-01T00:00:00Z",
             "limit": 5000000, "used": 5000000 } }
```

| 场景 | HTTP | `error.type` | `error.code` |
|---|---|---|---|
| 缺 token / 解析失败 | 401 | `invalid_request_error` | `invalid_api_key` |
| token 已吊销（登出/改密） | 401 | `invalid_request_error` | `token_revoked` |
| 用户不存在/已禁用 | 403 | `permission_error` | `account_disabled` |
| 模型不存在或不可见 | 404 | `invalid_request_error` | `model_not_found` |
| 额度不足 | 429 | `quota_exceeded` | `insufficient_quota` |
| 用户/模型限流 | 429 | `rate_limit_error` | `rate_limit_exceeded`（带 `retry-after`） |
| 上游不可达 | 503 | `upstream_error` | `upstream_unavailable` |
| 网关未就绪（迁移未完成） | 503 | `gateway_unavailable` | `gateway_not_ready` |
| 上游超时 | 504 | `upstream_error` | `upstream_timeout` |
| 请求体非法 | 400 | `invalid_request_error` | `invalid_request_error` |

`insufficient_quota` 命中桌面端 `isQuotaExceededError` 的终态词法，客户端会走 `QUOTA → ACCOUNT_QUOTA` 分支、不重试。

### 3.5 与桌面插件解码规则的对应

| 桌面端 `parseModelListing` 关注项 | 服务端来源 |
|---|---|
| `id` / `name` / `created` / `owned_by` | `model_id` / `display_name` / `create_time` 秒级时间戳 / `provider_key` |
| `context_window` / `max_output_tokens` | 同名列，空则不输出 |
| `input_modalities` | `input_modalities`（逗号分隔转数组） |
| `system_prompt_update` | 同名列（`in-history` / `leading-only`） |
| `reasoning.efforts` / `default_effort` | `reasoning_efforts`（逗号分隔转数组）/ `default_reasoning_effort` |
| `client_requirements` | jsonb 原样输出（键为客户端名，值为 `{min_version}`） |

## 4. 数据模型

> **v2 变更**：`ai_model_provider` 瘦身为「纯供应商连接」，`ai_gateway_model` 增加 `model_type` 与生成参数。本节描述拆分后的目标结构，拆分细节与迁移见第 16 节。

### 4.1 表结构

#### `ai_gateway_model` —— 对外模型目录

```sql
CREATE TABLE IF NOT EXISTS ai_gateway_model (
    id                       BIGSERIAL PRIMARY KEY,
    model_id                 VARCHAR(64)  NOT NULL,
    display_name             VARCHAR(64)  NOT NULL,
    provider_id              BIGINT       NOT NULL,
    upstream_model           VARCHAR(128) NOT NULL,
    context_window           INT,
    max_output_tokens        INT,
    input_modalities         VARCHAR(64)  NOT NULL DEFAULT 'text',
    system_prompt_update     VARCHAR(16)  NOT NULL DEFAULT 'in-history',
    reasoning_efforts        VARCHAR(64),
    default_reasoning_effort VARCHAR(16),
    client_requirements      JSONB,
    extra_params             JSONB,
    is_default               BOOLEAN      NOT NULL DEFAULT FALSE,
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    sort_order               INT          NOT NULL DEFAULT 0,
    remark                   VARCHAR(500),
    create_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_gateway_model_model_id UNIQUE (model_id)
);

-- 全局至多一条默认模型
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default
    ON ai_gateway_model (is_default) WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_status
    ON ai_gateway_model (status, sort_order);
```

- `model_id` 文法：`^[a-z0-9][a-z0-9.\-]{0,63}$`（小写连字符，与桌面端 provider key 文法一致）。
- `provider_id` 逻辑外键指向 `ai_model_provider.id`（不建物理外键，保持既有迁移风格）。
- `extra_params` 用于上游专有参数（如 `{"enable_thinking": true}`），请求时合并进上游 body，冲突时以目录配置为准。
- `client_requirements` 与 `extra_params` **采用 TEXT 存 JSON 文本**（而非 JSONB）：网关只做 JSON 序列化/反序列化，避免 MyBatis jsonb 类型处理器带来的写入兼容问题；如后续需要 DB 侧查询再改 JSONB。

#### `ai_gateway_quota` —— 额度主体

```sql
CREATE TABLE IF NOT EXISTS ai_gateway_quota (
    id           BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL,           -- USER | ROLE | DEFAULT
    subject_key  VARCHAR(64) NOT NULL,           -- USER: username；ROLE: role_code；DEFAULT: '*'
    period_kind  VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    token_limit  BIGINT,                         -- NULL = 不限量
    used_tokens  BIGINT      NOT NULL DEFAULT 0,
    period_start TIMESTAMP   NOT NULL,
    period_end   TIMESTAMP   NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    remark       VARCHAR(500),
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_gateway_quota_subject UNIQUE (subject_type, subject_key)
);
```

- `subject_key` 对用户存 **username**，与 `UserContext.getUserId()`（现为 username）及 `qa_history.user_id` 口径一致。

#### `ai_gateway_usage` —— 调用台账（兼预留凭据）

```sql
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

CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_user   ON ai_gateway_usage (user_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_model  ON ai_gateway_usage (model_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_channel ON ai_gateway_usage (channel, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_gateway_usage_time   ON ai_gateway_usage (create_time DESC);
```

状态机：

| status | 含义 | 额度效果 |
|---|---|---|
| `RESERVED` | 已预留、上游未结束 | `used_tokens` 已加 `reserved_tokens` |
| `SUCCESS` | 正常结束、拿到 usage | 按 `total_tokens` 结算差额 |
| `ERROR` | 上游 4xx/5xx | 全额退回预留 |
| `REJECTED_QUOTA` | 额度不足被拒 | 未扣减 |
| `ABORTED` | 客户端断开/上游中断 | 按已累计正文估算结算 |
| `TIMEOUT` | 空闲超时 | 按已累计正文估算结算 |

`channel` 由请求头 `X-Zhishu-Channel` 决定（`DESKTOP` / `CONSOLE` / `AGENT` / `BRIEFING` / `OPENAPI` / `TEST`）；无请求头时，来源为本机回环地址（127.0.0.1 / ::1）判为 `CONSOLE`（平台内部调用），其余判为 `DESKTOP`。
`X-Zhishu-Channel` 优先于上述启发式：平台内部经 gatewayclient 回环时主动声明通道（Agent 运行为 `AGENT`，其余默认取 `zhishu.gateway.internal.channel`，现为 `CONSOLE`）。

#### ~~`ai_gateway_internal`~~ —— 内部调用主体（本期不建表）

内部调用的额度主体由**客户端侧**开关决定（`zhishu.gateway.internal.quota-subject=caller|platform`）：
`caller` 时透传当前用户 JWT，`platform` 时改用服务账号 token，因此网关侧无需额外表即可区分。
若后续需要按调用方细粒度配置，再引入该表。

### 4.2 建表执行方式

`GatewaySchemaInitializer implements ApplicationRunner @Order(17)`，用 `ResourceDatabasePopulator`（`setContinueOnError(true)`）幂等执行 `db/init_gateway.sql`，与 `ModelConfigSchemaInitializer`（`@Order(16)`）保持一致的迁移风格。

### 4.3 迁移脚本（`patch_agent_model_to_gateway.sql`）

目标：把现有 provider + agent 绑定关系翻译成网关目录与 `model_id`，全部语句幂等、可重复执行。

```sql
-- 1) 每个启用的 provider 生成一条目录行
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model,
    context_window, max_output_tokens, input_modalities, system_prompt_update,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT
    'gw-' || lower(regexp_replace(p.provider_key, '[^a-zA-Z0-9]+', '-', 'g'))
          || '-' || lower(regexp_replace(p.chat_model,  '[^a-zA-Z0-9.]+', '-', 'g')),
    p.name || ' · ' || p.chat_model,
    p.id, p.chat_model,
    32768, 4096, 'text', 'in-history',
    p.is_default, 'ENABLED', p.sort_order, '由模型设置迁移生成',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.status = 'ENABLED'
ON CONFLICT (model_id) DO NOTHING;

-- 2) Agent 覆盖了非默认模型名的组合，补建目录行
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model,
    context_window, max_output_tokens, input_modalities, system_prompt_update,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT DISTINCT
    'gw-' || lower(regexp_replace(p.provider_key, '[^a-zA-Z0-9]+', '-', 'g'))
          || '-' || lower(regexp_replace(a.model,        '[^a-zA-Z0-9.]+', '-', 'g')),
    p.name || ' · ' || a.model,
    p.id, a.model,
    32768, 4096, 'text', 'in-history',
    FALSE, 'ENABLED', p.sort_order, '由 Agent 绑定迁移生成',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_agent a
JOIN ai_model_provider p ON p.id = a.model_provider_id
WHERE a.model IS NOT NULL AND btrim(a.model) <> ''
  AND a.model <> p.chat_model
  AND p.status = 'ENABLED'
ON CONFLICT (model_id) DO NOTHING;

-- 3) 回填 ai_agent.model 为网关 model_id
UPDATE ai_agent a
SET model = m.model_id,
    update_time = CURRENT_TIMESTAMP
FROM ai_model_provider p
JOIN ai_gateway_model m ON m.provider_id = p.id
WHERE a.model_provider_id = p.id
  AND m.upstream_model = COALESCE(NULLIF(btrim(a.model), ''), p.chat_model);

-- 4) 弃用 provider 绑定（保留列，不再参与路由）
UPDATE ai_agent
SET model_provider_id = NULL,
    update_time = CURRENT_TIMESTAMP
WHERE model_provider_id IS NOT NULL;

-- 5) 服务账号（内部无用户上下文的调用主体，不可登录）
INSERT INTO sys_user (id, username, real_name, password, role, status, create_time, update_time)
VALUES (9000000000000000001, 'gateway-service', '模型网关服务账号', 'disabled', 'SERVICE', 1,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- 6) 缺省额度兜底
INSERT INTO ai_gateway_quota (subject_type, subject_key, period_kind, token_limit, used_tokens,
                              period_start, period_end, status, remark)
VALUES ('DEFAULT', '*', 'MONTHLY', 5000000, 0,
        date_trunc('month', CURRENT_TIMESTAMP),
        date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '1 month',
        'ENABLED', '缺省月额度')
ON CONFLICT (subject_type, subject_key) DO NOTHING;
```

要点：

- 第 3 步依赖第 1、2 步生成的目录行，执行顺序不可颠倒；脚本整体在单个事务中运行。
- 迁移后 `ai_agent.model` 一定是已存在的 `model_id`，不再是自由文本模型名（Assumption 9）。
- 服务账号密码写 `disabled`（非合法 BCrypt 串，`BCryptPasswordEncoder.matches` 恒为 false），仅用于签发服务 token。

## 5. 认证与身份

### 5.1 `GatewayAuthFilter`

`OncePerRequestFilter`，注册方式对齐 `DesktopAuthFilter`：

```java
var registration = new FilterRegistrationBean<>(new GatewayAuthFilter(...));
registration.addUrlPatterns("/v1/*");
registration.setOrder(Integer.MIN_VALUE + 120);   // 与 DesktopAuthFilter(+110) 同为 Security 链之前的 Servlet 过滤器
```

流程：

1. 取 `Authorization: Bearer`；`JwtUtil.validateToken` → `TokenBlacklistService.isRevoked`（登出与改密即时生效）→ `username`。
2. `UserService.findByUsername` 校验存在且 `status=1`（Caffeine 60s 缓存，避免热路径查库）。
3. 写 `GatewayPrincipal(username, userId)` 到 request attribute 与 `UserContext`，供 SSE 线程 `snapshot/restore`。
4. 失败即以 OpenAI 错误体直接响应，不进 Controller。

`zhishu.gateway.auth.mode=jwt`（默认）为唯一主路径；`static` 模式用 `static-token` 映射固定主体，用于无门禁 / 单租户部署。

### 5.2 与 Spring Security 的关系

- `SecurityConfig`：`auth.requestMatchers("/v1/**").permitAll()`——放行给网关过滤器，避免 `RestAuthenticationEntryPoint` 用 `Result` 形状响应。
- `JwtAuthenticationFilter.shouldNotFilter`：增加 `/v1/` 前缀，避免每请求调 `PermissionService.listPermissionCodesByUsername`（热路径额外查库）且与网关过滤器重复解析。

### 5.3 已知限制

`TokenBlacklistStore` 为进程内单实例实现：多实例部署时登出吊销不跨实例共享。本期按单实例部署设计；横向扩展需引入共享存储或在网关侧独立校验。

## 6. 额度与计量

### 6.1 有效额度解析

```
USER(subject_key = username)
  → 不存在则取 ROLE(subject_key = sys_user.role)
  → 再不存在则取 DEFAULT(subject_key = '*')
  → 仍不存在则用 zhishu.gateway.quota.default-monthly-tokens
```

解析结果按 subject+period 缓存在进程内（Caffeine，10s TTL），写操作后主动失效。

### 6.2 周期滚动

不做定时任务，请求入口惰性滚动（单条 CAS 语句）：

```sql
UPDATE ai_gateway_quota
SET used_tokens = 0,
    period_start = date_trunc('month', CURRENT_TIMESTAMP),
    period_end   = date_trunc('month', CURRENT_TIMESTAMP) + INTERVAL '1 month',
    update_time  = CURRENT_TIMESTAMP
WHERE id = :id AND period_end <= CURRENT_TIMESTAMP;
```

滚动后重新读取额度再进入预留；同一周期内该语句不影响任何行。

### 6.3 预留 → 结算

- 估算器 `TokenEstimator`：`tokens ≈ ceil(字符数 / estimate-chars-per-token)`（默认 2.0，中文保守）；估算输入包含 `messages` 文本、`tools` JSON 与 `system`。
- 预留量：

```
reserve = est(prompt) + min(request.max_tokens, model.max_output_tokens, reserve-max-output-tokens)
```

- 结算量：

| 场景 | actual |
|---|---|
| 上游返回 `usage` | `prompt_tokens + completion_tokens` |
| 非流式无 `usage` | `est(prompt) + est(输出正文)`，`estimated = true` |
| 流式中断 / 客户端断开 | `est(prompt) + est(已下发正文)`，`status = ABORTED/TIMEOUT`，`estimated = true` |
| 上游 4xx/5xx，未产出 | `0`（全额退回） |

`delta = actual - reserve`，`delta` 可为负（退回多预留）。

### 6.4 原子性

唯一并发安全点是单条 UPDATE：

```sql
UPDATE ai_gateway_quota
SET used_tokens = used_tokens + :delta,
    update_time = CURRENT_TIMESTAMP
WHERE id = :id
  AND (token_limit IS NULL OR used_tokens + :delta <= token_limit);
```

- 预留阶段影响行数 0 → 返回 429 `insufficient_quota`，不转发上游，台账写 `REJECTED_QUOTA`。
- 结算阶段若影响行数为 0（并发下已触顶），仍按实际写入台账，`used_tokens` 允许轻微超出上限一次（由下一请求拒绝），不阻断已开始的流。

### 6.5 边界

- 重试不重复扣：`request_id` 唯一，客户端重试是新 `request_id`，只有真正打到上游的那次产生结算。
- 预留不跨周期：结算始终回到预留那条 `ai_gateway_quota` 行（按 `id`），即使跨月也不串账。
- 并发：`delta` 正负混用同一条 SQL，天然幂等；无需表锁。

## 7. 上游转发

### 7.1 适配器 SPI

```java
public interface UpstreamProtocolAdapter {
    String protocol();                                  // openai-completions
    boolean supports(String protocol);
    byte[] buildRequestBody(UpstreamTarget target, ChatCompletionRequest request, boolean includeUsage);
    GatewayUsage parseUsage(byte[] body);               // 非流式
    StreamFrame parseStreamLine(String dataLine);       // 流式
}
```

本期只注册 `OpenAiCompletionsAdapter`；新增 Anthropic 适配器时只加实现类，控制器与额度链路不动。

`UpstreamTarget` 来自 `ModelUpstreamPort.resolve(providerId)`：`providerKey`、`baseUrl`、`apiKey`（解密后）、`defaultChatModel`、`temperature`、`maxTokens`、`topP`。

### 7.2 请求改造

1. `model` → `upstream_model`。
2. `stream=true` 时强制注入 `stream_options: {"include_usage": true}`（客户端显式关闭也覆盖，用于计费）。
3. 合并 `ai_gateway_model.extra_params`（目录优先）。
4. 其余字段（`messages` / `tools` / `temperature` / `top_p` / `reasoning_effort` / `stop` …）原样透传。
5. 注入 `Authorization: Bearer <provider apiKey>`，不带客户端原始 Header。

### 7.3 响应与 SSE

- 非流式：解析上游 JSON，把 `model` 改写为网关 `model_id`，提取 `usage`，其余字段原样回给客户端。
- 流式：逐帧解析 `data:` 行；`choices` 帧内 `model` 改写为网关 `model_id`；捕获末帧 `usage`；`[DONE]` 原样透传。
- 上游错误：读取 body 但**不回传给客户端**（可能包含内部地址），按第 3.4 节映射为网关错误体；原始错误写入台账 `error_message`（截断 500 字符）与日志。

### 7.4 HTTP 客户端与超时

- 使用 JDK `HttpClient` + `BodyHandlers.ofPublisher()`：零新增依赖，字节级可控；经 `JdkFlowAdapter` 转为 `Flux` 后按 SSE 行切分。
- 超时：连接 5s；非流式整体 120s；流式只用「两帧间空闲超时」60s（整体超时会把长 答案切断）。
- 客户端断开：`Flux` 转 `Stream` 后关闭 → 取消订阅 → 关闭上游响应体 → 按已累计正文结算。
- 控制器直写 `HttpServletResponse`（不套 `Result`、不依赖 MVC 的 `Flux` 桥接），逐帧 flush；一条流占一个请求线程，生产建议开启 `spring.threads.virtual.enabled=true`。

### 7.5 自环与安全校验

- 保存「模型设置」时校验 `base_url` 不得解析到网关自身地址（`zhishu.gateway.internal.base-url` 与当前监听端口），避免自环打死进程。
- 仅允许 `http/https`，拒绝对 `file:`、内网元数据地址的配置。

## 8. 平台内部统一接入

### 8.1 解析链

```
控制台 Agent / 知识问答 / AI 简报 / 平台对话
   ↓ Spring AI ChatClient（OpenAiChatModel，baseUrl = <网关>/v1）
   ↓ Authorization: Bearer <当前用户 JWT | 服务账号 JWT>
网关（鉴权 → 目录 → 额度 → 转发）→ 上游 provider
```

- 模型唯一来源 = `ai_gateway_model`。
- `ai_model_provider` 只承担「上游连接」职责（供网关转发与 embedding 使用）。

### 8.2 身份透传

- `UserContext` 增加 `token` 字段，`Snapshot` 同步携带；`UserContextInterceptor` 从 `Authorization` 头读取原始 bearer 写入。
- 现有 `TracingToolCallback`、`McpGuardedToolCallback` 已用 `snapshot()/restore()` 传递上下文，无需改动即可带上 token。
- 无请求上下文（简报定时任务、启动自检）：`GatewayServiceTokenProvider` 用 `JwtUtil.generateToken("gateway-service")` 生成服务 token，按 `token-renew-seconds` 提前续签。
- 应急开关：`zhishu.gateway.internal.quota-subject=platform` 时内部调用一律改用服务账号主体。

### 8.3 `zhishu-ai` 改造清单

| 位置 | 现状 | 改造 |
|---|---|---|
| `modelconfig/runtime/ModelProviderRegistry` | 按 `providerId` 直连上游构建 `ChatModel` | 新增 `gatewayclient/GatewayChatModelRegistry.resolve(modelId)`，按 `(tokenHash, modelId)` LRU 缓存指向网关的 `OpenAiChatModel`（复用 `RefreshableOpenAiModels.chatModel`）；`ModelProviderRegistry` 仅保留上游配置读取与 embedding |
| `agent/runtime/ChatClientSupport#buildOptions` | 用 `entity.modelProviderId` + provider 默认参数 | 入参改 `gatewayModelId`；默认 `maxTokens/temperature` 取网关目录能力元数据 |
| `agent/runtime/ChatClientSupport#buildClient` | 入参 `Long providerId` | 入参改 `String gatewayModelId` |
| `agent/runtime/ChatClientSupport#resolveModelName` | 回退 `resolveChatModelName(providerId)` | 返回 `entity.getModel()`（已是网关 `model_id`） |
| `agent/runtime/ChatClientSupport.LoggingChatModel` | 日志 endpoint 取 provider baseUrl | endpoint 固定 `<gateway>/v1/chat/completions` |
| `agent/runtime/ChatClientAgentEngine`（7 处调用） | 传 `agent.getModelProviderId()` | 传 `agent.getModel()` |
| `agent/graph/GraphWorkflowExecutor`（3 处调用） | 同上 | 同上 |
| `modelconfig/config/DynamicOpenAiConfig#runtimeChatModel` | 委托默认 provider | 返回**默认网关模型**的 ChatModel（懒解析 + 按当前用户 token 解析） |
| `modelconfig/api/ModelConfigPort#currentChatModel` | 读 legacy `ai_model_config` | 返回 `ai_gateway_model.is_default` 的 `model_id`（读不到再回退 legacy，保留一个版本） |
| `modelconfig/service/impl/ModelProviderServiceImpl#testConnection` | 直连上游测试 | 保留（诊断上游 Key 是否有效）；网关模型页另提供端到端测试 |
| `chat/service/impl/AiChatServiceImpl` | 用 primary `ChatClient` + legacy 模型名 | 无需改结构：`ChatClient` 已指向网关，模型名来自 `ModelConfigPort` |
| `knowledge/service/impl/QuestionAnsweringServiceImpl` | 同上（`streamChatClient`） | 无需改 |
| `agent/domain/entity/AiAgentEntity#modelProviderId` | 参与路由 | 标 `@Deprecated`，不再参与路由 |
| `agent/domain/dto/AgentCreateDTO`、`AgentUpdateDTO`、`agent/domain/vo/AgentVO` | 暴露 `modelProviderId` | 移除该字段；`model` 承载网关 `model_id` |
| `agent/service/impl/AgentDefinitionServiceImpl` | 映射 `modelProviderId` | 去掉映射；保存时校验 `model` 存在于网关目录 |

`gatewayclient` 另需 `GatewayModelCatalogClient`：拉取 `/v1/models`（服务 token），60s 缓存，供 Agent 保存校验与前端下拉的后端代理使用。

### 8.4 灰度与回退

`zhishu.gateway.internal.enabled=false`：

- `GatewayChatModelRegistry` 不再被装配，`DynamicOpenAiConfig` 回退 `ModelProviderRegistry` 的默认 provider。
- `ChatClientSupport` 走旧的 `resolve(providerId)` 分支（此时 `ai_agent.model_provider_id` 仍需可用，故灰度期迁移脚本最后一步「置空 provider」延后执行）。

灰度顺序：Agent → 问答 → 简报，逐段切换并观察 `ai_gateway_usage` 与错误率。

### 8.5 风险与代价

| 风险 | 说明 | 应对 |
|---|---|---|
| 多一跳 | 本机回环 HTTP + 二次 SSE 解析，首 token 增加个位数毫秒 | 可接受；`internal.base-url` 直连 127.0.0.1 不走 LB |
| SPOF | 目录表 / 额度表异常会中断控制台 AI | `internal.enabled=false` 回退直连；表操作全部幂等可重放 |
| 额度阻断内部功能 | 用户额度耗尽会同时拒绝控制台对话 | AI 页面明确提示「额度不足/重置时间」，管理员可在额度页调高 |
| 自环 | provider base_url 误配为网关地址 | 保存期与加载期双重校验 |
| 双份计量 | `ai_agent_run.token_count/ttft_ms`（监控口径）与网关 `usage`（计费口径）不一致 | 文档明确两者语义，不要求相等 |

### 8.6 未纳入统一

| 能力 | 处理 |
|---|---|
| embedding（向量库、知识入库） | 继续用 `runtimeEmbeddingModel` 直连默认 provider |
| MCP 上游工具调用 | 不经网关 |
| 多模态生成（图片、语音） | 本期无此能力 |

## 9. 管理面 REST（`/api/v1/model-gateway`）

统一 `Result<T>` 包装，鉴权沿用「登录 + 菜单/按钮可见性」现状。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/models` | 目录列表（含上游 provider 名称、状态、是否默认） |
| POST | `/models` | 新建目录行（`model_id` 唯一，文法校验） |
| PUT | `/models/{id}` | 修改（`model_id` 不可改，避免客户端已缓存的映射失效） |
| DELETE | `/models/{id}` | 删除（被 Agent 引用时拒绝，返回引用列表） |
| POST | `/models/{id}/default` | 设为默认（唯一约束保证至多一条） |
| POST | `/models/{id}/enable` `/disable` | 启停 |
| POST | `/models/{id}/test` | 端到端测试：经网关发一条 `请只回复 OK`，返回首包耗时与 usage |
| GET | `/models/available` | 内部使用：给 Agent 编辑器下拉的轻量列表 |
| GET | `/quotas` | 额度列表（按 subject_type 过滤） |
| POST/PUT | `/quotas`、`/quotas/{id}` | 新建/修改角色默认或用户覆盖 |
| DELETE | `/quotas/{id}` | 删除（DEFAULT 行不可删） |
| GET | `/usages` | 台账分页：用户 / 模型 / 通道 / 状态 / 时间区间 |
| GET | `/usages/summary` | 聚合：按用户、按模型、按天 |

写操作记录到既有操作日志 SPI（与 `ModelSettingController` 一致）。

## 10. 菜单 IA 改造与前端页面

> **v2 变更**：菜单 105 就地改造为「供应商管理」，菜单 1201 更名为「模型设置」；详见 16.5。

### 10.1 目标菜单树

```
模型网关 (12, DIRECTORY, /gateway → /gateway/models, sort=3)
├── 网关模型   (1201, /gateway/models)
├── 模型设置   (105,  /gateway/model-settings)   ← 原「智能中心」下迁移，组件路径不变
├── 额度策略   (1202, /gateway/quotas)
├── 调用用量   (1203, /gateway/usage)
└── 接入说明   (1204, /gateway/guide)
```

一级目录 sort 顺延：`模型网关=3`、`账号管理 5 → 4`、`系统设置 6 → 5`、`个人中心 7` 保持 9。

### 10.2 `patch_menu_model_gateway.sql` 要点

```sql
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(12,   0,  '模型网关', '/gateway',           'ModelGateway', '/gateway/models', 'Connection',
                'DIRECTORY', 1, 1, 3, NULL, NULL, 1),
(1201, 12, '网关模型', '/gateway/models',    'GatewayModels', NULL, 'Grid',
                'MENU', 1, 1, 1, 'views/gateway/GatewayModelList.vue',  '{"title":"网关模型"}', 1),
(1202, 12, '额度策略', '/gateway/quotas',    'GatewayQuotas', NULL, 'Histogram',
                'MENU', 1, 1, 3, 'views/gateway/GatewayQuotaPolicy.vue', '{"title":"额度策略"}', 1),
(1203, 12, '调用用量', '/gateway/usage',     'GatewayUsage',  NULL, 'DataLine',
                'MENU', 1, 1, 4, 'views/gateway/GatewayUsageList.vue',  '{"title":"调用用量"}', 1),
(1204, 12, '接入说明', '/gateway/guide',     'GatewayGuide',  NULL, 'Document',
                'MENU', 1, 1, 5, 'views/gateway/GatewayGuide.vue',      '{"title":"接入说明"}', 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id, title = EXCLUDED.title, path = EXCLUDED.path,
    route_name = EXCLUDED.route_name, redirect = EXCLUDED.redirect, icon = EXCLUDED.icon,
    menu_type = EXCLUDED.menu_type, visible = EXCLUDED.visible, sort = EXCLUDED.sort,
    component = EXCLUDED.component, meta = EXCLUDED.meta, status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 模型设置迁入模型网关目录（组件路径不变，避免路由回归）
UPDATE sys_menu SET parent_id = 12, path = '/gateway/model-settings', sort = 2,
       update_time = CURRENT_TIMESTAMP WHERE id = 105;

-- 智能中心剩余子项重排（105 已移出）
UPDATE sys_menu SET sort = 7, update_time = CURRENT_TIMESTAMP WHERE id = 110;
UPDATE sys_menu SET sort = 8, update_time = CURRENT_TIMESTAMP WHERE id = 108;

-- 一级目录排序顺延
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 5;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 6;

-- 按钮权限
INSERT INTO sys_menu (id, parent_id, title, path, route_name, redirect, icon, menu_type,
                      visible, requires_auth, sort, component, meta, status)
VALUES
(120101, 1201, '网关模型查询', NULL, 'gateway:model:list',   NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(120102, 1201, '网关模型维护', NULL, 'gateway:model:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(120201, 1202, '额度查询',     NULL, 'gateway:quota:list',   NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1),
(120202, 1202, '额度维护',     NULL, 'gateway:quota:update', NULL, NULL, 'BUTTON', 0, 1, 2, NULL, NULL, 1),
(120301, 1203, '用量查询',     NULL, 'gateway:usage:query',  NULL, NULL, 'BUTTON', 0, 1, 1, NULL, NULL, 1)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id, title = EXCLUDED.title, route_name = EXCLUDED.route_name,
    menu_type = EXCLUDED.menu_type, sort = EXCLUDED.sort, status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- ADMIN 授权
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + m.id, 1, m.id
FROM sys_menu m WHERE m.id IN (12, 105, 1201, 1202, 1203, 1204, 120101, 120102, 120201, 120202, 120301)
ON CONFLICT (id) DO UPDATE SET menu_id = EXCLUDED.menu_id;
```

### 10.3 前端页面

| 文件 | 职责 |
|---|---|
| `src/views/gateway/GatewayModelList.vue` | 网关目录 CRUD、设默认、启停、端到端测试；表格列展示上游供应商、能力元数据、状态 |
| `src/views/gateway/GatewayQuotaPolicy.vue` | 角色默认额度表 + 用户覆盖（新增/编辑/删除），展示当前窗口与用量 |
| `src/views/gateway/GatewayUsageList.vue` | 台账分页查询 + 汇总卡片（按用户/模型/天） |
| `src/views/gateway/GatewayGuide.vue` | 接入说明：baseURL、三个端点、curl 示例、错误码表、`X-Zhishu-Channel` 说明 |
| `src/api/gateway.ts` | 上述接口封装 |
| `src/views/ai/AgentManage.vue` | 模型下拉改 `GET /api/v1/model-gateway/models/available`，值 = `model_id` |
| `src/views/ai/ModelSettings.vue` | 保留组件与文件名，仅调整副标题文案（供模型网关转发的上游连接） |

`src/router/dynamicRouteViews.ts` 通过 `import.meta.glob('@/views/**/*.vue')` 自动解析，新增页面无需改路由。同步更新根 `MENU_ROUTES.json` 与 `zhishu-frontend/docs/MENU_ROUTES.json`。

## 11. 配置项

```yaml
zhishu:
  gateway:
    enabled: true
    chat-path: /v1/chat/completions
    models-path: /v1/models
    quota-path: /v1/quota
    auth:
      mode: jwt                          # jwt | static
      static-token: ${ZHISHU_GATEWAY_TOKEN:}
      static-username: gateway-service
    upstream:
      connect-timeout-ms: 5000
      request-timeout-ms: 120000         # 非流式整体超时
      stream-idle-timeout-ms: 60000      # 流式两帧间最大空闲
      max-request-bytes: 8388608
    quota:
      enabled: true
      default-monthly-tokens: 5000000
      estimate-chars-per-token: 2.0
      reserve-max-output-tokens: 4096
      rate-limit:
        enabled: false                   # 本期默认关闭，开启为「用户×模型」QPS
        qps: 5
    catalog:
      cache-ttl-seconds: 60
    log:
      prompt-bodies: false               # true 才落 prompt 正文（仅排障期）
    internal:
      enabled: true                      # false = 回退直连上游（灰度/回滚）
      base-url: http://127.0.0.1:${server.port}
      service-username: gateway-service
      quota-subject: caller              # caller | platform
      token-renew-seconds: 3600
```

约束：所有参数显式配置，加载期校验（`enabled`、`base-url` 必须为 http(s)、`chat-path` 与 `models-path` 需落在 `/v1` 前缀下）。

## 12. 失败模式、安全、观测

### 12.1 失败模式

| 场景 | 表现 | 处理 |
|---|---|---|
| 上游不回 `usage` | 计费按估算 | 台账 `estimated=true`，管理页可筛选核对 |
| 上游不可达 / 超时 | 503 / 504 | `internal.enabled=false` 时控制台回退直连 |
| provider 被禁用/删除 | 该模型从 `/v1/models` 消失，推理返回 404 | 已绑定该模型的 Agent 保存时提示重新选择 |
| 额度表不可用 | 推理 500（拒绝放行） | 不允许「无额度记录即放行」，避免越权 |
| 目录表不可用 | `/v1/models` 500，推理 500 | 目录缓存保留上一份（`catalog.cache-ttl-seconds` 内可用） |
| 客户端中途断开 | 上游连接关闭 | 台账 `ABORTED` + 估算结算 |

### 12.2 安全

- 供应商 Key 只在服务端，沿用 `McpCrypto` AES-GCM 加密列（`api_key_enc`）。
- 客户端 token = 知枢 JWT；网关不回传上游错误原文、不泄露上游模型名与内网地址。
- 日志默认不落 prompt 正文（`log.prompt-bodies=false`），仅记长度与 request_id。
- 请求体大小、超时、并发均显式设限。
- 自环防护：目录保存与启动加载双重校验 provider baseUrl 不等于网关地址。
- 上游严格 `http/https`，不跟随重定向（JDK `HttpClient.Redirect.NEVER`）。

### 12.3 观测

- `ai_gateway_usage` 即审计与统计主数据源（通道、模型、用户、状态、tokens、耗时）。
- 结构化日志：`request_id`、`user`、`model_id`、`provider`、`status`、`latency`、`ttft` 贯穿一次请求。
- 管理页「调用用量」提供按天/用户/模型的聚合视图，替代 Prometheus（项目未引入）。
- 慢请求与上游错误率通过日志采样告警（阈值可配）。

## 13. 测试与验收

### 13.1 单元

| 目标 | 用例 |
|---|---|
| `TokenEstimator` | 中文/英文/混合文本、超长 messages、空输入 |
| `SseFrameCodec` | 跨包分片、`data:` 空行、`[DONE]`、非 JSON 行忽略 |
| 额度窗口 | `period_end` 到期滚动、并发滚动只生效一次 |
| 预留结算 | `delta` 正负、`token_limit=NULL`、并发触顶 |
| 错误映射 | 401/403/404/429/503/504 与 `error.code` 一一对应 |

### 13.2 集成（`zhishu-core/src/test/java/.../apitest`）

用 `com.sun.net.httpserver.HttpServer` 起假上游（零新增依赖），插入 provider + gateway model + quota 行：

1. 非流式与流式转发成功，SSE 帧合法且末帧带 `usage`。
2. `usage` 精确扣减；同一 `request_id` 重放不重复扣。
3. `remaining` 不足返回 429 `insufficient_quota`，且假上游**未收到请求**。
4. 两个不同 JWT 用户拿到互不可见的目录与独立额度。
5. `/v1/models` 缺 `context_window` 时仍合法（字段省略）。
6. 上游宕机：`/v1/models` 走缓存，推理返回 503。
7. 控制台 Agent 对话经网关（假上游只接受来自网关的调用），台账 `channel=AGENT` 且计入当前用户。
8. 简报/定时任务（无用户上下文）用服务账号主体入账。
9. `internal.enabled=false` 时回退直连且不产生网关台账。
10. 迁移脚本幂等：连续执行两次，`ai_gateway_model` 与 `ai_agent.model` 不重复、不丢失。

### 13.3 与桌面插件联调

对齐 `dsh-plugin-model-gateway/docs/design.md` 第 11 节的 8 条验收：目录字段完整、429 语义与 `reset_at`、登出后 token 立即失效、`client_requirements` 门禁生效、设置模型页三类提供方并存与网关只读、账号页额度展示一致。

## 14. 实施阶段

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P0（本次）** | 设计定稿：契约、DDL、迁移与菜单补丁、内部接入清单 | 本文档 |
| P1 | `zhishu-model-gateway` 模块骨架、`GatewayAuthFilter`、`/v1/models`、非流式透传、台账 | 可跑通「拉目录 + 非流式推理」 |
| P2 | 流式 SSE、额度预留/结算、错误归一、限流 | 流式与额度闭环 |
| P3 | 管理 REST、菜单 IA 改造、网关模型/额度/用量三个前端页面 | 管理面可用 |
| P4 | 内部统一接入：`GatewayChatModelRegistry`、`UserContext.token`、Agent 迁移、前端下拉改造 | Agent → 问答 → 简报 依次切换 |
| P5 | 桌面插件联调、面向客户端的接入文档、停用 `ModelProviderRegistry` 直连 chat 路径 | 端到端验收 |

## 15. 假设与限制

1. 桌面插件按 `dsh-plugin-model-gateway` 契约发 `Authorization: Bearer <知枢 JWT>`，`baseUrl` 直连后端 `:8180` 根；若改用前端 Nginx 域名，需补 `/v1/` 反向代理并关闭缓冲（`proxy_buffering off`）。
2. 上游连接由「供应商管理」（`ai_model_provider`）维护，模型（含生成参数与能力元数据）由「模型设置」（`ai_gateway_model`）维护，网关不新建第二套凭据表。
3. 部署为单实例：token 黑名单、额度缓存、限流均为进程内实现；多实例需引入共享存储。
4. 管理面采用**登录 + 方法级权限注解**：`/api/v1/model-gateway/**` 各接口标 `@PreAuthorize("hasAuthority('gateway:…')")`，权限码与 `sys_menu` 的 BUTTON 行一致（ADMIN 自动获得全部按钮权限）。
5. 额度单位统一为 token，不做金额/积分换算。
6. `/v1/models` 对已认证用户全量可见；按角色/用户的模型白名单列为后续扩展点，本期不建表。
7. 内部调用与网关同进程、同端口（`127.0.0.1:${server.port}`），不做跨实例内部调用。
8. 内部调用额度计入当前登录用户；服务账号仅承担无用户上下文的后台任务。
9. `ai_agent.model` 语义变更为网关 `model_id`，历史自由文本模型名由迁移脚本映射，不做模糊匹配。
10. embedding 不纳入网关推理面；其配置从供应商行迁移到 `model_type=EMBEDDING` 的模型行，`runtimeEmbeddingModel` 改为按该行构建（见 16.4）。

## 16. 设计变更 v2：供应商与模型设置拆分

### 16.1 目标与概念划分

原实现把「连接」与「模型」混在一张表（`ai_model_provider` 同时有 baseUrl/apiKey 与 chat_model/embedding_model/生成参数），导致：同一供应商无法挂多个模型、参数无法按模型差异化、菜单语义模糊。

拆分后职责单一：

| 概念 | 表 | 职责 | 典型字段 |
|---|---|---|---|
| **供应商**（Supplier） | `ai_model_provider`（瘦身） | 只管连接与凭据 | `name`、`provider_key`、`base_url`、`api_key_enc`、`status`、`is_default`、`remark` |
| **模型**（Model） | `ai_gateway_model`（扩展） | 对外 id + 能力元数据 + 生成参数 | `model_id`、`provider_id`、`upstream_model`、`model_type`、`context_window`、`temperature`、`top_p`、`max_tokens` |

约束：

- 一个供应商下可挂 **多个 CHAT 模型** 与 **至多一个默认 EMBEDDING 模型**。
- 供应商被模型引用时**不可删除**；停用供应商 → 其全部模型自动从 `/v1/models` 消失。
- 生成参数一律在模型行维护，供应商不再持有 chat 相关参数。

### 16.2 表结构变更

`ai_gateway_model` 新增 4 列：

```sql
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS model_type  VARCHAR(16) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS temperature NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS top_p       NUMERIC(3,2);
ALTER TABLE ai_gateway_model ADD COLUMN IF NOT EXISTS max_tokens  INT;

-- 默认模型改为「按类型各自唯一」
DROP INDEX IF EXISTS uk_ai_gateway_model_default;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_chat
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'CHAT';
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_gateway_model_default_embedding
    ON ai_gateway_model (is_default) WHERE is_default = TRUE AND model_type = 'EMBEDDING';

CREATE INDEX IF NOT EXISTS idx_ai_gateway_model_type
    ON ai_gateway_model (model_type, status, sort_order);
```

`ai_model_provider` 目标字段：`id`、`name`、`provider_key`、`base_url`、`api_key_enc`、`api_key_masked`、`is_default`、`status`、`sort_order`、`remark`、时间戳。
`chat_model`、`embedding_model`、`temperature`、`max_tokens`、`top_p`：**保留列但可为 NULL、标 DEPRECATED、不再作为配置源**。
迁移时先把它们下沉为 `ai_gateway_model` 的 CHAT/EMBEDDING 模型行（含默认行），原值快照到 `ai_model_provider_legacy_backup`，然后**置 NULL**：
保证「模型与参数的单一事实来源是模型行」，避免供应商行上的历史值被误读（回退链路与 API 均不再暴露它们）。下个版本可 `DROP COLUMN`。

新库直接由更新后的 `init_gateway.sql` + `V_add_model_provider.sql`（已改为可空）建结构；已有库走 16.3 的补丁脚本。

### 16.3 迁移脚本（`patch_provider_model_split.sql`）

```sql
-- 0) 前置检查：provider 只应有一个默认（否则下方 partial unique 会冲突）
-- SELECT count(*) FROM ai_model_provider WHERE is_default = TRUE;   -- 必须 <= 1

-- 1) 把供应商上的生成参数下沉到 CHAT 模型行
UPDATE ai_gateway_model m
SET temperature = COALESCE(m.temperature, p.temperature),
    top_p       = COALESCE(m.top_p, p.top_p),
    max_tokens  = COALESCE(m.max_tokens, p.max_tokens)
FROM ai_model_provider p
WHERE m.provider_id = p.id AND m.model_type = 'CHAT';

-- 2) 每个供应商的 embedding_model 生成一条 EMBEDDING 模型行
INSERT INTO ai_gateway_model (
    model_id, display_name, provider_id, upstream_model, model_type,
    is_default, status, sort_order, remark, create_time, update_time)
SELECT 'gw-' || lower(regexp_replace(p.provider_key,   '[^a-zA-Z0-9]+', '-', 'g'))
             || '-' || lower(regexp_replace(p.embedding_model, '[^a-zA-Z0-9.]+', '-', 'g'))
             || '-emb',
       p.name || ' · ' || p.embedding_model || '（向量）',
       p.id, p.embedding_model, 'EMBEDDING',
       p.is_default, 'ENABLED', p.sort_order, '由供应商 embedding_model 迁移生成',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_model_provider p
WHERE p.embedding_model IS NOT NULL AND btrim(p.embedding_model) <> ''
ON CONFLICT (model_id) DO NOTHING;

-- 3) 若没有任何 EMBEDDING 默认行，把第一条 EMBEDDING 行置为默认
UPDATE ai_gateway_model SET is_default = TRUE, update_time = CURRENT_TIMESTAMP
WHERE id = (SELECT id FROM ai_gateway_model
             WHERE model_type = 'EMBEDDING' AND status = 'ENABLED'
             ORDER BY sort_order, id LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM ai_gateway_model
                   WHERE model_type = 'EMBEDDING' AND is_default = TRUE);
```

幂等：全部语句可重复执行；第 1 步用 `COALESCE` 保证不覆盖运维在模型页改过的值。
迁移后供应商页仍能读到旧列（仅展示用），写操作不再更新它们。

### 16.4 embedding 链路（不新增网关端点）

embedding 仍**不经** `/v1`，但配置来源改为 EMBEDDING 模型行：

1. 在 `zhishu-ai` 的 `modelconfig.api` 新增端口：

```java
public interface AiEmbeddingTargetPort {
    Optional<Target> currentEmbedding();

    record Target(String modelId, String upstreamModel, String baseUrl, String apiKey) {}
}
```

2. 实现类 `GatewayEmbeddingTargetService` 放在 `zhishu-model-gateway`：读 `ai_gateway_model`（`model_type='EMBEDDING' AND is_default AND status='ENABLED'`）+ `ModelUpstreamPort.resolve(providerId)` 得 baseUrl/apiKey。
   编译期依赖仍是 gateway → ai（接口在 ai），无环。
3. `ModelProviderRegistry.resolveEmbeddingModel()` 通过 `ObjectProvider<AiEmbeddingTargetPort>` 优先取该目标；不可用或未配置时回退「默认供应商 + 旧 embedding_model 列」（即当前行为）。
4. `ModelConfigPort.currentEmbeddingModel()` 返回该目标的上游模型名（展示/历史记录用）。

### 16.5 菜单与前端

**菜单（复用 105，不新增 id）**

| id | 标题 | path | component | 说明 |
|---|---|---|---|---|
| 105 | 供应商管理 | `/gateway/providers` | `views/gateway/GatewayProviderList.vue` | 就地改造，保留原 id 与角色授权 |
| 1201 | 模型设置 | `/gateway/models` | `views/gateway/GatewayModelList.vue` | 原标题「网关模型」改名 |
| 1202 / 1203 / 1204 | 额度策略 / 调用用量 / 接入说明 | 不变 | 不变 | — |

```sql
UPDATE sys_menu SET title = '供应商管理', path = '/gateway/providers',
       component = 'views/gateway/GatewayProviderList.vue', sort = 1,
       update_time = CURRENT_TIMESTAMP WHERE id = 105;
UPDATE sys_menu SET title = '模型设置', sort = 2,
       update_time = CURRENT_TIMESTAMP WHERE id = 1201;
UPDATE sys_menu SET sort = 3, update_time = CURRENT_TIMESTAMP WHERE id = 1202;
UPDATE sys_menu SET sort = 4, update_time = CURRENT_TIMESTAMP WHERE id = 1203;
UPDATE sys_menu SET sort = 5, update_time = CURRENT_TIMESTAMP WHERE id = 1204;
```

**前端**

| 文件 | 变更 |
|---|---|
| `views/gateway/GatewayProviderList.vue` | 新建：供应商表格（名称/标识/BaseURL/Key 状态/默认/状态）+ 新建编辑抽屉 +「测试连接」+「拉取上游模型」 |
| `views/gateway/GatewayModelList.vue` | 改造：**默认展示全部供应商的模型**（顶部供应商可选「全部」，类型可选「全部/对话/向量」），表列含供应商与类型；**新建模型弹窗首字段为必选的供应商** + 生成参数表单 +「从上游导入」（弹窗内先选供应商再拉列表）|
| `views/ai/ModelSettings.vue` | 删除（其职责已由上述两页接管） |
| `api/gateway.ts`、`types/modelGateway.ts` | 增加供应商 CRUD / 上游模型列表 / 批量导入；模型类型增加 `model_type`、`temperature`、`top_p`、`max_tokens` |

`AgentManage.vue` 无需再改（已绑定网关 `model_id`）。

### 16.6 管理面 REST

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/model-gateway/providers` | 供应商列表（含 `modelCount`、`isDefault`、Key 是否已配置） |
| POST / PUT / DELETE | `/api/v1/model-gateway/providers[/{id}]` | 增删改（被模型引用时拒删） |
| POST | `/api/v1/model-gateway/providers/{id}/default` | 设为默认供应商 |
| POST | `/api/v1/model-gateway/providers/{id}/test` | 连通性：`GET {baseUrl}/models`，返回可用模型数与耗时 |
| GET | `/api/v1/model-gateway/providers/{id}/upstream-models` | 拉上游模型列表，返回 `UpstreamModelInfo[]`（id/展示名/上下文窗口/输出上限/模态/推理档位/system 更新方式），供「导入模型」 |
| GET | `/api/v1/model-gateway/models?type=CHAT\|EMBEDDING&providerId=` | 按类型/供应商过滤 |
| POST | `/api/v1/model-gateway/models/batch` | 批量导入（入参：providerId + 类型 + `models[]` 条目，每个条目可带上游元数据；兼容旧的 `upstreamModels[]` 名字列表），model_id 自动生成 `gw-<provider>-<model>` |

旧接口 `/api/v1/model-settings/**` 已随 v2 收尾**移除**；前端旧封装（`src/api/ai.ts` 的 model-settings 函数与 mock 数据）及 `types/modelProvider.ts` 一并删除。

### 16.7 网关行为补充

1. **目录过滤**：`/v1/models` 仅输出 `model_type='CHAT'`；EMBEDDING 行永不下发。
2. **默认模型**：`/v1/models` 的 `default` 取 `model_type='CHAT' AND is_default`；无默认行时省略。
3. **默认参数注入**：转发上游前，若请求体缺 `temperature` / `top_p` / `max_tokens`，用模型行的默认值补齐；客户端显式传值优先。
4. **回退模式**（`internal.enabled=false`）的 `ChatModel` 构建同样改为按模型行取 `upstream_model` 与生成参数，不再读供应商旧列。

### 16.8 影响面清单

| 模块 | 文件 | 变更 |
|---|---|---|
| zhishu-ai | `modelconfig/domain/ModelProviderEntity` | 删除 chat 相关 5 字段（v2）；类本体已迁至 gateway（见 17.1） |
| zhishu-ai | `modelconfig/dto/ModelProviderCreateDTO`、`ModelProviderUpdateDTO`、`vo/ModelProviderVO` | 字段裁剪 |
| zhishu-ai | `modelconfig/service/impl/ModelProviderServiceImpl` | CRUD 瘦身；`testConnection` 改为 `GET /models` |
| zhishu-ai | `modelconfig/runtime/ModelProviderRegistry` | chat 构建按模型行；embedding 走新端口 |
| zhishu-ai | `modelconfig/api/AiEmbeddingTargetPort`（新） | embedding 目标端口 |
| zhishu-ai | `modelconfig/service/impl/ModelConfigServiceImpl#currentEmbeddingModel` | 改读 embedding 默认模型 |
| 网关 | `catalog/GatewayModelEntity`、`GatewayCatalogService` | 新增 4 字段；目录按类型过滤 |
| 网关 | `chat/GatewayChatServiceImpl` | 默认参数注入 |
| 网关 | `admin/controller/GatewayProviderAdminController`（新） | 供应商 REST |
| 网关 | `admin/controller/GatewayModelAdminController` | 类型过滤、批量导入 |
| 网关 | `GatewayEmbeddingTargetService`（新） | 实现 embedding 目标端口 |
| 初始化 | `db/init_gateway.sql` | 新列与索引 |
| 初始化 | `db/patch_provider_model_split.sql`（新） | 迁移脚本 |
| 初始化 | `db/patch_menu_model_gateway.sql` | 105/1201 改名与 path |
| 前端 | 见 16.5 | 两页改造 + 旧页删除 |

### 16.9 阶段与验收

- **P0（本文）**：设计定稿，不写实现代码。
- P1：DDL（新库 + 补丁）+ 实体/DTO/VO 瘦身与扩展。
- P2：供应商 REST + 模型 REST（类型、生成参数、批量导入）+ 旧接口弃用。
- P3：embedding 目标端口与回退（向量库、知识入库回归）。
- P4：菜单补丁 + 两个前端页面 + 删除旧页。
- P5：文档与联调（桌面插件不受影响，平台内部 Agent 绑定 `model_id` 不变）。

验收：

1. 供应商页可增删改查、设默认、测试连接、拉取上游模型；被引用时拒删。
2. 模型设置必须选供应商；同一供应商可挂多个 CHAT 模型与一个 EMBEDDING 模型。
3. `/v1/models` 不出现 EMBEDDING 行，`default` 只指向 CHAT 模型。
4. 停用供应商后其模型立即从目录消失，推理返回 `model_not_found`。
5. 知识库入库/检索（embedding）在迁移后仍正常，且数据源为 EMBEDDING 默认行。
6. 客户端未传生成参数时按模型行默认值转发；显式传值不被覆盖。
7. 迁移脚本重复执行不改变运维已调整的模型参数。

### 16.10 风险与回滚

| 风险 | 说明 | 应对 |
|---|---|---|
| 旧列仍有写入方 | 无 | 历史列与备份表已由 `db/patch_drop_model_legacy.sql` 统一清除 |
| 默认供应商多条 | 迁移时 partial unique 冲突 | 前置检查 SQL，先人工收敛 |
| embedding 断档 | 迁移未生成 EMBEDDING 行 | 第 5/6 步自动兜底；旧列已置 NULL，确实缺失时 `resolveEmbeddingModel` 明确报错提示补建向量模型 |
| 菜单路径变化 | 书签 `/gateway/model-settings` 失效 | 保留一条隐藏 PAGE 路由指向新页一个版本 |
| 回滚 | 新列/新索引引入 | 旧列未删（值已清空+快照）；一般情况只需 `internal.enabled=false`；彻底回滚见下方恢复 SQL |

彻底回滚（恢复 v1 「供应商行即模型设置」的读取路径；**仅适用于尚未执行 `db/patch_drop_model_legacy.sql` 的库**，已清理的环境需在「模型设置」重建模型行）：

```sql
-- 从备份表把历史列写回（只回填当前为 NULL 的列，不覆盖人工修改）
UPDATE ai_model_provider p
SET chat_model      = COALESCE(p.chat_model, b.chat_model),
    embedding_model = COALESCE(p.embedding_model, b.embedding_model),
    temperature     = COALESCE(p.temperature, b.temperature),
    max_tokens      = COALESCE(p.max_tokens, b.max_tokens),
    top_p           = COALESCE(p.top_p, b.top_p)
FROM ai_model_provider_legacy_backup b
WHERE b.id = p.id;
```

配合 `zhishu.gateway.internal.enabled=false`（回退直连）或 `zhishu.gateway.enabled=false`（整体关网关）。

### 16.11 实施落地记录（与本节设计的差异）

已按本节实现，落地时补充/确认了以下细节：

1. **回退链路新增 `AiChatTargetPort`**：除嵌入端口外，额外在 `modelconfig.api` 增加对话目标端口；
   `GatewayChatModelRegistry` 在 `internal.enabled=false` 时优先按模型行直连上游，取不到才退回旧 provider 配置。
2. **`ModelProviderPort` 扩展**：新增 `setDefault(id)`、`listUpstreamModels(id)`；`testConnection` 改为 `GET {baseUrl}/models`（不再需要 chat 模型名）。
3. **供应商 REST 委托**：网关侧 `GatewayProviderAdminController` 直接复用 `ModelProviderPort`，不复制实体/Mapper；
   删除前额外校验该供应商下的模型数量（>0 拒绝）。
4. **旧接口已移除**：`/api/v1/model-settings/**`（`ModelSettingController`）已删除；供应商与模型统一走 `/api/v1/model-gateway/providers|models`。
5. **菜单 id**：105 复用为「供应商管理」、1201 更名为「模型设置」；`AgentManage.vue` 仍绑定网关 `model_id`，无需改动。
6. **前端旧页与旧接口封装**：`views/ai/ModelSettings.vue`、`src/api/ai.ts` 中的 model-settings 函数与 mock 数据、`types/modelProvider.ts` 均已删除。
7. **验证**：后端 `mvn -DskipTests compile` 全量通过；前端 `vue-tsc --noEmit` 与 `eslint` 通过。

### 16.12 加固项（第二轮）

| 加固 | 实现 |
|---|---|
| **DDL 顺序风险** | `GatewaySchemaInitializer` 先跑 `init_gateway.sql`，检测缺 `model_type` 且默认供应商唯一时**自动执行** `patch_provider_model_split.sql`；仍缺列则标记网关不可用 |
| **就绪闸门** | 新增 `GatewayReadiness` + `GatewayReadyFilter`：结构未就绪时 `/v1/*` 返回 503 `gateway_not_ready`（带可操作原因），不抛 SQL 异常 |
| **迁移失败可读** | `GatewayExceptionAdvice` 新增 `BadSqlGrammarException` 处理，返回「请执行 patch_provider_model_split.sql」而不是 500 |
| **channel 判定** | 先看 `X-Forwarded-For`/`X-Real-IP`（反代转发 → `DESKTOP`）；无转发头且源为回环才判 `CONSOLE`；头部 `X-Zhishu-Channel` 优先（内部调用主动声明） |
| **限流接线** | `GatewayRateLimiter`：`quota.rate-limit.enabled/qps` 按「用户×模型」计，超限 429 `rate_limit_exceeded`（进程内单实例语义） |
| **prompt 日志开关** | `log.prompt-bodies=true` 时记录截断正文（2000 字），否则仅记长度；默认关闭 |
| **显式失败** | 供应商未配对话模型 / 无默认向量模型时抛明确的 `AiException`，不再静默回退到错误模型 |
| **菜单** | 目录 12 的 redirect 改为 `/gateway/providers`；新增 `gateway:provider:list` / `gateway:provider:update` 按钮权限并授权 ADMIN |
| **文档** | 根 `MENU_ROUTES.json` 与 `zhishu-frontend/docs/MENU_ROUTES.json` 补齐 id=12 子树与 105/1201 新定义，兼容重定向指向 `/gateway/providers` |

### 17. 待办与已知限制

#### 17.1 本轮已补充完成

| 项 | 内容 |
|---|---|
| **基线收敛** | `init.sql` 直接建 id=12「模型网关」目录 + 105 供应商管理 + 1201~1204 + 按钮权限；一级目录 sort 顺延（5→4、6→5）；ADMIN 授权由 `SELECT ... FROM sys_menu` 自动覆盖 |
| **Agent 字段清理** | `AgentCreateDTO` / `AgentUpdateDTO` / `AgentVO` 移除 `modelProviderId`；`AiAgentEntity#modelProviderId` 保留列但标 `@Deprecated`；`GatewayChatModelRegistry.resolve/describeModel` 去掉 `legacyProvider` 参数，回退模式改用 `AiChatTargetPort` + 默认供应商 |
| **前端清理** | `types/aiAgent.ts` 三个接口去掉 `modelProviderId`；`AgentManage.vue` 表单与提交不再携带 |
| **旧接口移除（P3）** | 删除 `ModelSettingController`（`/api/v1/model-settings`）；删除 `api/ai.ts` 的 model-settings 函数、`demoModelProviders` 与 `types/modelProvider.ts`；`AiModelConfigController` 的 `{@link}` 指向同步修正 |
| **供应商不再残留模型字段** | `V_add_model_provider.sql` 不再建历史列；split 脚本负责把旧列下沉为模型行并快照；后续已由 `patch_drop_model_legacy.sql` 彻底删除列与备份表。`GatewaySchemaInitializer` 不再自动跑破坏性迁移，改为只执行非破坏的 `db/gateway_schema.sql` |
| **客户端接入文档** | 新增 `docs/模型网关接入指南.md`：三个端点、错误码与重试矩阵、TS/curl 流式示例、反代注意项、联调自测清单 |
| **管理面方法级鉴权** | 4 个 admin controller 全部加 `@PreAuthorize`：读接口 `gateway:*.list` / `gateway:usage:query`，写接口 `gateway:*.update`；`PermissionConstants` 新增 7 个常量；`/models/available` 额外允许 `ai:agent:query`（保障 Agent 编辑器下拉）；前端 `constants/permissions.ts` 同步并用 `v-permission` 隐写操作按钮。**前提**：必须先执行 `patch_menu_model_gateway.sql`（建 BUTTON 权限行），否则 ADMIN 也会 403 |
| **模型行防重** | 迁移脚本第 5 步改为按「供应商 + 类型 + 上游模型名」去重（`provider_key` 改名会让 `ON CONFLICT (model_id)` 失效）；服务层 `create`/`update` 拒绝重复三元组，`batchImport` 跳过已存在并记录跳过数；脚本尾部提供查重 SQL 与可选唯一索引 `uk_ai_gateway_model_provider_upstream` |
| **供应商业务代码归属迁移** | `ai_model_provider` 的实现从 `zhishu-ai/modelconfig` 整体迁入 `zhishu-model-gateway/provider`（git mv 保留历史）：`provider/domain/ModelProviderEntity`、`provider/mapper/ModelProviderMapper`、`provider/runtime/ModelProviderRegistry`、`provider/service/impl/ModelProviderServiceImpl`、`provider/service/impl/ModelUpstreamServiceImpl`（原 `ModelUpstreamService`）、`provider/config/ProviderSchemaInitializer`（原 `ModelConfigSchemaInitializer`，@Order(16)）+ `zhishu-model-gateway/src/main/resources/db/V_add_model_provider.sql`。ai 侧只保留真正跨模块的契约：`AiChatTargetPort` / `AiEmbeddingTargetPort` 与共享构建器（方向保持 gateway → ai） |
| **退役 `ai_model_config` 单例配置** | 删除 `ModelConfigPort`、`ModelConfigServiceImpl`、`AiModelConfigEntity/Mapper/VO/UpdateDTO`、`AiModelConfigController`（`/api/v1/model-config` 下线），`init_ai.sql` 不再建该表；新增 `db/patch_retire_ai_model_config.sql`：`sys_config` 加 `chat_enable_rag_default` + 迁移旧值 + 将旧表重命名为 `ai_model_config_retired`。新端口 `zhishu-api` 的 `ChatSettingsApi`（由 `zhishu-biz/systemconfig/spi/ChatSettingsApiImpl` 实现）供 ai 读默认 RAG 开关；`ChatClientAgentEngine` 改用该端口。会话记忆窗口改为配置项 `zhishu.ai.chat.memory-window`（默认 20，由 `AiConfig.chatMemory` 读 `Environment`）。展示/审计与 options 构造均改走 `GatewayChatModelRegistry`（`defaultModelLabel()` / `resolveModelId(null)`），修掉了一类**用旧单例模型名覆盖网关 model_id 导致 `404 model_not_found`** 的隐患（QA/RAG 链路） |
| **废弃表与列清理** | 新增 `db/patch_drop_model_legacy.sql` 并已在开发库执行：删除 `ai_model_provider` 的 5 个历史列、`ai_agent.model_provider_id`、`ai_model_provider_legacy_backup`、`ai_model_config(_retired)`；代码侧删除 `ModelProviderRegistry`（改用 `provider/runtime/ModelRuntimeCache` 按指纹缓存 + 懒代理）、`ModelProviderEntity` 与 `AiAgentEntity` 的废弃字段；`V_add_model_provider.sql` 不再建历史列与 `model_provider_id`；`init_postgresql.sql` 同步删除 `ai_model_config` 建表块；`ProviderSchemaInitializer` 只保留从 `spring.ai.openai.*` 引导默认供应商 |
| **模型配置包瘦身（契约下沉）** | 引用统计发现 `ModelProviderPort`/`ModelUpstreamPort`/`ModelProviderVO`/`ModelProviderCreateDTO`/`ModelProviderUpdateDTO`/`UpstreamModelInfo` 的消费者 100% 在网关侧（ai 为 0）→ 已全部移入 `zhishu-model-gateway/provider/{api,dto,vo}`；同时删除已无消费者的死端口 `GatewayDefaultModelPort`（`GatewayModelCatalogClient` 去掉 implements）、将 `AiEmbeddingTargetPort` 收缩为单方法 `defaultEmbeddingModel()`（`enabled()`/`currentEmbedding()`/`Target` 下沉为实现内部）、把 `DynamicOpenAiConfig` 移到 `ai.gatewayclient`以消除 `modelconfig ↔ gatewayclient` 模块环、给 `modelconfig.runtime` 补 `@NamedInterface("api")`。结果：`modelconfig` 只剩两个推理目标端口 + `ModelConfigProperties` + `RefreshableOpenAiModels`，其 `@ApplicationModule(allowedDependencies={"shared"})` 声明变为真实 |
| **导入带元数据 + 开发库校正** | `listUpstreamModels` 改为返回 `UpstreamModelInfo`（兼容 OpenAI `{"data":[]}` 与 Ollama `{"models":[]}` 两种形状，容错解析 `context_window`/`max_output_tokens`/`input_modalities`/`effort.supported_levels`/`default_level`/`system_prompt_update`）；`models/batch` 新增 `models[]` 条目入参（保留 `upstreamModels[]` 兼容），导入时直接写入容量与推理档位。同时根据 DeepSeek 官方文档核实：现行模型名为 **`deepseek-flash`**（实为 DeepSeek-V4.1-Flash-1017）与 `deepseek-v4-pro`，旧名 `deepseek-chat`/`deepseek-reasoner` 已于 2026-07-24 停用 —— 因此 `gw-deepseek` 的上游名无需修正，已按官方元数据补齐（ctx 1048576 / out 393216 / text,image / low,high,max / high / in-history），并把绑定 DISABLED 模型的 3 个 Agent 改绑 `gw-deepseek` |
| **台账通道标注与回归测试** | 新增 `modelconfig.api.GatewayChannels`（单一来源：`X-Zhishu-Channel` + `DESKTOP/CONSOLE/AGENT/OPENAPI`），`RefreshableOpenAiModels` 的 `chatModel(...)` 增加 headers 重载（走 `OpenAiSetup.setupSyncClient/setupAsyncClient` 的默认头位），`GatewayChatModelRegistry.resolve(modelId, channel)` 把通道参入缓存键，`ChatClientSupport`（Agent 运行时）传 `AGENT`，网关侧 `GatewayChatController` 改用同一常量。验证：Agent 对话台账由 `CONSOLE` 变 `AGENT`（成功行 `id=11`，SUCCESS/569 tokens/estimated=false/ttft=162ms；上游不可达时亦记 AGENT + 503 ERROR）。同时新增 `zhishu-model-gateway` 首个单测 `GatewayChatUsageSettlementTest`（6 例：整帧归约、无 usage 不覆盖、误传内层对象必须落到估算、非流式入账、total 缺失补齐、全估算分支），并为该模块补 `spring-boot-starter-webmvc-test`；结算辅助改为包内静态方法（`captureUsage` / `extractUsage` / `UsageNumbers`）以便测试。另：唯一索引 `uk_ai_gateway_model_provider_upstream` 已在开发库启用（无重复行），建索引语句保留在手工脚本而非启动期自动执行，以免存量重复库因索引创建失败而阻断启动 |
| **包结构治理与架构护栏** | 新增 `AiModules`（`@Modulithic` 标记类，与 biz 的 `BizModules` 同一约定）+ `AiModulithStructureTest`（`ApplicationModules.verify()`）与 `GatewayDependencyGuardTest`（ArchUnit 禁止 gateway 伸进 ai 业务模块），首次跑出 **81 条真实越界**，现已降为 **0**：① `McpCrypto` 从 `ai.mcp.support` 下沉并改名 `ai.shared.crypto.CredentialCrypto`（实际被 mcp/openapi/gateway 三域共用，而 mcp 没有任何导出包）；② `openapi` 补 `@ApplicationModule` 声明（此前无 `package-info`，其 54 条越界完全隐身），并将 `chat.service`、`knowledge.service`、`knowledge.dto`、`agent.service`、`agent.domain.vo` 声明为 `api` 命名接口；③ 新增 `AgentModelUsagePort`（`gatewayclient.api`）+ `AgentModelUsagePortImpl`，删除直读 `ai_agent` 表的 `AgentModelRefMapper`；④ `ai.modelconfig` 整体并入 `ai.gatewayclient`（模型接入层单一入口；`gatewayclient` 的 `allowedDependencies` 收敛为 `{"shared"}`，业务模块改声明 `gatewayclient :: api` + `gatewayclient`）；另抽取 `AgentChatClientFactory` 端口消除 `chat → agent.runtime.ChatClientSupport` 的内部类耦合；清理 `ai/briefing`（13 个空目录）与 `zhishu.briefing.*` 死配置（两张历史表保留数据，退役脚本 `db/patch_retire_briefing.sql` 需人工执行）。验证：全仓 test-compile 通过，`AiModulithStructureTest`、`GatewayDependencyGuardTest`、`GatewayChatUsageSettlementTest` 共 8 个测试全绿 |

#### 17.2 待办

| 优先级 | 项 | 说明 |
|---|---|---|
| P1 | **测试补齐** | 单测（`TokenEstimator`、SSE 切帧、额度滚动、错误映射）与集成测试（假上游、流式、429、双用户隔离、迁移幂等）；项目约定默认不产出测试代码，建议单独立任务 |
| P1 | **桌面插件联调** | 按 `dsh-plugin-model-gateway` 契约在真实客户端跑通「目录→流式推理→429 额度」全链路（接入文档已完成，见 17.1） |
| P2 | **模型可见性白名单** | 按角色/用户控制 `/v1/models`；`ai_gateway_model_acl` 未建 |
| P2 | **供应商页连贯流程** | 拉取上游模型后一键跳「模型设置」并带上 providerId 与预选模型 |

#### 17.3 已知限制

1. **单实例语义**：token 黑名单、限流计数、额度/目录缓存均为进程内；多实例需共享存储。
2. **已清理的列与表**：`ai_model_provider` 的 `chat_model`/`embedding_model`/`temperature`/`max_tokens`/`top_p`、`ai_agent.model_provider_id`、`ai_model_provider_legacy_backup`、`ai_model_config` 均已删除（`db/patch_drop_model_legacy.sql`）；供应商表现在只含连接与凭据字段，模型与参数的唯一存身处是 `ai_gateway_model`。
3. **流式线程模型**：`/v1` 流式为阻塞式响应，一条流占一个请求线程；生产建议开启 `spring.threads.virtual.enabled=true`。
4. **自动与手工迁移的分界**：启动器只自动执行非破坏脚本（`init_gateway.sql`、`gateway_schema.sql`、`V_add_model_provider.sql`）；历史库的数据下沉（`patch_provider_model_split.sql`）与旧列/备份表清理（`patch_retire_ai_model_config.sql`、`patch_drop_model_legacy.sql`）必须手工按顺序执行。
5. **结构未就绪时的表现**：`/v1/*` 返回 503 `gateway_not_ready`，管理面返回「请确认 db/gateway_schema.sql 执行成功」提示，应用本身可正常启动。

