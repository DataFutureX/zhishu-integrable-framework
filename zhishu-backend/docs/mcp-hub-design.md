# MCP 中枢（MCP Hub）业务功能与实现逻辑

> 本文 §1–§10 描述**迁移前**的结构（`zhishu-ai` 的 `ai.mcp` 包 + 独立的 `zhishu-mcp` 模块），作为行为背景与设计缘由保留；
> **迁移后的现行结构见 §11（as-built）**：MCP 已全部合并到 `zhishu-mcp` 单模块的双平面。
> 对外接入的协议细节见 [mcp-service-guide.md](./mcp-service-guide.md)，迁移决策与实施记录见
> [mcp-module-migration-design.md](./mcp-module-migration-design.md)。

---

## 1. 一句话概览

知枢的 MCP 能力是**双平面 + 两个模块**：

| 平面 | 本平台角色 | 端点/入口 | 主要实现 |
| ---- | ---------- | --------- | -------- |
| 入站平面（对外提供） | MCP **Server** | `POST /mcp`（MCP Streamable HTTP） | `zhishu-ai` 的 `McpAuthFilter` + `McpGuardedToolCallback`；`zhishu-mcp` 的 `SystemIntroductionTool` |
| 出站平面（接入他方） | MCP **Client** | 无独立端点，由 Agent 运行时在会话中调用 | `zhishu-ai` 的 `McpUpstreamConnectionManager`、`McpExternalToolAdapter` |
| 控制面 | 管理 API | `GET/POST/PUT/DELETE /api/v1/mcp/**` | `McpHubController` + `McpClientAdminService` / `McpUpstreamAdminService` |

两个模块都在 **`zhishu-core` 同一进程内**装配（`zhishu-core/pom.xml` 同时依赖 `zhishu-ai` 与 `zhishu-mcp`），
启动类 `YqapApplication` 扫描 `cn.datafuturex.zhishu`，因此两边的 `@Configuration`、`@Component` 均被注册。

```text
                    ┌──────────────── 同一 Spring Boot 进程（zhishu-core） ────────────────┐
 外部 MCP Client ──▶│ /mcp  → McpAuthFilter(ai) → McpAuthFilter(zhishu-mcp) → Spring AI    │
 (Bearer wxmcp_*)   │                                                    MCP Server       │
                    │                        ToolCallbackProvider 汇总：                  │
                    │                        ├─ wanxiangOutboundMcpTools（守卫通道，默认关）│
                    │                        └─ zhishuMcpSystemTools（系统介绍 Tool）       │
                    │                                                                     │
 Agent 会话 ───────▶│ ChatClientSupport.resolveTools → ExternalToolPort                   │
                    │   → McpExternalToolAdapter → McpUpstreamConnectionManager            │
                    └───────────────────────────────┬─────────────────────────────────────┘
                                                    ▼
                                          他方 MCP Server（如 wanxiang-mcp）
```

---

## 2. 代码结构与职责

### 2.1 `zhishu-mcp`（系统 Tool 出口，轻量模块）

| 文件 | 职责 |
| ---- | ---- |
| `config/McpServerAutoConfiguration.java` | 注册 `ToolCallbackProvider`（系统 Tool）与 `/mcp` 的 `X-API-Key` 过滤器；受 `zhishu.mcp.server.enabled` 控制 |
| `config/McpServerProperties.java` | `zhishu.mcp.server.*`：`enabled` / `auth-key` / `auth-header` |
| `security/McpAuthFilter.java` | 仅在 `/mcp`、`/mcp/*` 生效；`auth-key` 为空即放行（开发模式），非空则校验请求头，失败返回 401 `{"error":"Unauthorized",...}` |
| `tool/SystemIntroductionTool.java` | 手写 `ToolCallback`：`getSystemIntroduction`，返回系统名称/版本/特性/架构/技术栈 JSON |
| `pom.xml` | 仅依赖 `spring-boot-starter-webmvc` + `spring-ai-starter-mcp-server-webmvc` + lombok，**不依赖 AI 业务代码** |

特点：**没有数据库、没有业务依赖**，只做「系统自我介绍」这一类与业务无关的静态 Tool，
因此在架构上它与 AI 领域的 MCP Hub 解耦，即使 AI 模块演进也不影响对外最小可用面。

### 2.2 `zhishu-ai` 的 `mcp` 包（MCP Hub 主体）

Spring Modulith 模块声明（`mcp/package-info.java`）：

```java
@ApplicationModule(displayName = "Mcp", allowedDependencies = {"shared", "biztools :: api"})
package cn.datafuturex.zhishu.ai.mcp;
```

| 子包 | 关键类 | 职责 |
| ---- | ------ | ---- |
| `client` | `McpUpstreamConnectionManager` | 上游连接池、`tools/list` 拉取、工具缓存落库、健康标记、stdio 拒绝 |
| `config` | `McpProperties` | `wanxiang.mcp.*` 配置属性 |
| `config` | `McpAuthFilter` | `/mcp` 入站 `wxmcp_*` 鉴权 + CORS + 调用方上下文注入 |
| `config` | `McpOutboundToolsConfiguration` | 把「默认目录内的业务 Tool」包装为带守卫的对外 Tool（默认关闭，见 §4.4） |
| `config` | `McpSchemaInitializer` | 启动时执行 `archive/migration_ai_mcp.sql`，并预热连接所有 ENABLED 上游 |
| `controller` | `McpHubController` | 控制面 REST API（`/api/v1/mcp`） |
| `service` | `McpAuthService` | API Key 校验、能力码 → 工具名白名单、写 `McpCallerContext` |
| `service` | `McpClientAdminService` | 对外 Client 的增删改查、密钥签发/轮换 |
| `service` | `McpUpstreamAdminService` | 上游登记/改/删/探活/工具启停，含环回与唯一性校验 |
| `service` | `McpExternalToolAdapter` | 实现 `ExternalToolPort`：按 Agent 解析上游工具、绑定关系维护 |
| `service` | `McpTerminalOverviewPort` | 实现 `TerminalOverviewPort`：把上游 `getTerminalOnlineOverview` 转成会话直出表格 |
| `service` | `McpCallLogService` | 入站/出站调用审计（`@Async` 落 `ai_mcp_call_log`） |
| `support` | `McpGuardedToolCallback` | 出站入口守卫：调用方上下文 → 工具白名单 → RPM 限流 → 用户身份切换 → 审计 |
| `support` | `McpRateLimiter` | 进程内滑动窗口限流（按 Client + 60s 窗口） |
| `support` | `McpUpstreamCircuit` | 上游简易熔断（3 次失败 → 摘除 60s） |
| `support` | `PrefixedToolCallback` / `InboundAuditingToolCallback` | 上游工具改名与描述前缀、调用审计包装 |
| `support` | `McpCallerContext` / `McpJson` / `McpOutboundCatalog` | 调用方 ThreadLocal、JSON 小工具、对外默认目录 |
| `domain` | `entity` / `dto` / `vo` / `mapper` | 5 张表实体与 API 契约 |

---

## 3. 数据模型（MCP 双平面表）

建表脚本：`zhishu-ai/src/main/resources/archive/migration_ai_mcp.sql`（幂等，启动自动执行），
全量初始化脚本另见 `zhishu-core/src/main/resources/db/init_ai.sql`。

| 表 | 语义 | 关键字段 |
| -- | ---- | -------- |
| `ai_mcp_client` | **入站**：他方调用本平台 `/mcp` 的凭据与授权 | `name`、`key_prefix`（唯一，明文 Key 前 14 位）、`secret_hash`（SHA-256）、`bound_user_id`/`bound_username`（绑定身份，复用工程 ACL）、`capabilities`（JSON 数组）、`rpm_limit`、`status`、`last_used_at`、`created_by` |
| `ai_mcp_upstream` | **出站**：登记的他方 MCP Server | `code`（唯一，小写）、`protocol`（`STREAMABLE_HTTP`/`SSE`）、`base_url`、`endpoint`、`auth_header_enc`（AES-GCM，`enc:` 前缀）、`request_timeout_ms`、`status`、`health_status`/`health_message`/`last_probe_at` |
| `ai_mcp_upstream_tool` | 上游工具缓存（探活/连接时刷新） | `upstream_id`、`original_name`、`exposed_name`（`ext_<code>_<原名>`）、`description`、`enabled`；唯一键 `(upstream_id, original_name)` |
| `ai_agent_mcp_upstream` | 智能体 ↔ 上游绑定 | `agent_id`、`upstream_id`、`allowed_tools`（JSON 数组，空=全部已启用工具）；唯一键 `(agent_id, upstream_id)` |
| `ai_mcp_call_log` | 调用审计（双向） | `direction`（代码写 `IN`/`OUT`）、`client_id`、`upstream_id`、`agent_id`、`tool_name`、`success`、`error_message`（≤500）、`duration_ms`、`user_id`、`create_time` |

> 注意：`init_ai.sql` 中 `direction` 列的注释写作 `INBOUND/OUTBOUND`，而代码实际写入 `IN/OUT`
> （`McpCallLogService.recordOut` / `recordIn`）。查询过滤请按 `IN`/`OUT` 传参，
> `GET /api/v1/mcp/calls?direction=OUT` 内部会转大写。

---

## 4. 入站平面：对外提供 MCP 服务

### 4.1 协议与端点配置

`/mcp` 端点由 Spring AI MCP Server 自动装配提供，配置在 `application.yml` 的 `spring.ai.mcp.server.*`：

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: ${ZHISHU_MCP_SERVER_ENABLED:true}
        protocol: STREAMABLE          # Streamable HTTP 传输
        name: zhishu-integrable-framework
        version: 1.0.0
        type: SYNC
        annotation-scanner:
          enabled: true               # 扫描 @Tool Bean
        streamable-http:
          mcp-endpoint: /mcp
```

Spring AI 会把容器内所有 `ToolCallbackProvider` 汇总成 `tools/list` 的结果，本项目共有两个 Provider：

1. `zhishuMcpSystemTools`（`zhishu-mcp`）— 系统介绍 Tool；
2. `wanxiangOutboundMcpTools`（`zhishu-ai`）— 带守卫的业务 Tool 目录（受开关控制，当前默认关闭）。

### 4.2 鉴权链路（/mcp 请求的完整过滤顺序）

`SecurityConfig` 对 `/mcp`、`/mcp/**` 是 `permitAll`，`JwtAuthenticationFilter` 也显式跳过该路径，
`UserContextInterceptor` 的排除列表同样包含 `/mcp`。
**真正的鉴权由两个 Servlet Filter 完成**，按 Order 由小到大执行：

```text
POST /mcp
  │
  ├─(1) cn.datafuturex.zhishu.ai.mcp.config.McpAuthFilter      @Order(HIGHEST_PRECEDENCE + 20)
  │      · 统一写 CORS 头（含 Mcp-Session-Id 暴露、预检 204）
  │      · 取 Key：Authorization: Bearer <token>，或 ?token=<token>
  │      · McpAuthService.authenticate：
  │          前缀必须是 wxmcp_ → 用前 14 位 key_prefix 查 ai_mcp_client
  │          → status 必须 ENABLED → SHA-256 比对 secret_hash
  │          → 写 McpCallerContext（clientId/名称/绑定用户/工具白名单/RPM）
  │          → 写 UserContext（后续业务按该绑定用户做 ACL）
  │          → touch last_used_at
  │      · 失败：401 {"error":"invalid_mcp_key"}
  │      · finally：清理 McpCallerContext 与 UserContext
  │
  ├─(2) cn.datafuturex.zhishu.mcp.security.McpAuthFilter       order = Integer.MIN_VALUE + 100
  │      · 仅当 zhishu.mcp.server.auth-key 非空时校验 Header（默认 X-API-Key）
  │      · 失败：401 {"error":"Unauthorized","message":"Invalid or missing API Key"}
  │
  ├─(3) Spring Security：/mcp permitAll（JWT 链不介入）
  │
  └─(4) Spring AI MCP Server：initialize / tools/list / tools/call
```

**要点**

- API Key 明文形如 `wxmcp_<48 位 hex>`（`CredentialCrypto.newApiKey()`），库里只存前缀与 SHA-256，
  **明文仅在「创建」与「轮换」响应里返回一次**（`McpClientVO.apiKey`，其余场景为 `null`）。
- 入站调用方不是「匿名」：`bound_user_id`/`bound_username` 会被注入 `UserContext`，
  下游业务查询沿用该用户的数据权限。
- 两个 Filter 都拦 `/mcp`，属于**双层**鉴权：生产环境 `ProductionConfigGuard` 要求
  `zhishu.mcp.server.auth-key` 非空，因此生产调用方需要同时带
  `Authorization: Bearer wxmcp_...` 与 `X-API-Key: ...`；开发环境后者留空自动放行。
- 因此 [mcp-service-guide.md](./mcp-service-guide.md) 里只写了 `X-API-Key` 一种鉴权头，
  那是**第二层**；走 Hub 的 `wxmcp_*` 客户端还必须在 `Authorization` 里带 Bearer Key。
  （尚未收敛前，接入文档与实际链路存在这个差异，见 §9 第 2 条。）

### 4.3 能力白名单、限流与审计（守卫通道）

`McpOutboundToolsConfiguration` 在 `wanxiang.mcp.server-enabled=true` 时生效：

```java
ToolCallback[] all = ToolCallbacks.from(bizToolProviderPort.toolBeans());
// 只保留 McpOutboundCatalog.DEFAULT_TOOL_NAMES 内的工具
// 再逐个包上 McpGuardedToolCallback(callLogService, rateLimiter)
```

`McpGuardedToolCallback.call()` 的执行顺序（`support/McpGuardedToolCallback.java`）：

1. 无 `McpCallerContext` → 返回 `{"error":"未认证的 MCP 调用"}`；
2. 工具名不在 `allowedToolNames` → 记 `OUT` 失败审计，返回 `{"error":"当前 MCP Client 未授权工具: xxx"}`；
3. `McpRateLimiter.tryAcquire(clientId, rpmLimit)` 失败 → 记 `{"error":"超过调用频率限制"}`；
4. `UserContext.snapshot()` 后按绑定用户重设身份；
5. 执行真实 Tool，成功/异常均写 `ai_mcp_call_log`（`direction=OUT`）；
6. `finally` 还原 `UserContext`。

限流实现是**单机内存**滑动窗口：`Map<clientId, Deque<timestamp>>`，窗口 60s，
`rpmLimit <= 0` 时回落到 60（`McpClientAdminService` 创建时也以 `wanxiang.mcp.default-rpm` 兜底）。

### 4.4 能力码 → 工具白名单（当前形态）

`McpAuthService.allowedTools(List<String>)` 把 Client 的 `capabilities` 翻译成可调用工具名集合：

| 能力码 | 映射到的工具名 |
| ------ | -------------- |
| `STATION_LATEST` / `STATION_HISTORY` / `STATION_COMPARE` | `queryStationLatestElements` / `queryStationHistoryElements` / `compareStations` |
| `ONLINE` | `getTerminalOnlineOverview`、`queryTerminalOnlineStatus`、`listTerminals` |
| `PROJECT` | `listProjects` |
| `ALERT` | `queryRecentAlerts`、`queryAlertTrends` |
| `INSPECTION_PLAN` / `INSPECTION_TASK` / `INSPECTION_ISSUE` / `INSPECTION_SUMMARY` | `listInspectionPlans`、`getInspectionPlan` / `listInspectionTasks`、`getInspectionTaskDetail` / `listOpenInspectionIssues` / `getInspectionSummary` |
| `KNOWLEDGE_GRAPH` | `searchGraphEntities`、`getGraphNeighbors`、`findGraphPath`、`getProjectTopology`、`getAlertImpact` |
| `MCP_TOOLS`、`NL2SQL`、`RAG` 等 | 不映射（MCP 不开放） |

**但要注意当前的实际状态**：

- `McpOutboundCatalog.DEFAULT_CAPABILITIES = []`、`DEFAULT_TOOL_NAMES = Set.of()`（空集）；
- `BizToolProviderPort` 的实现是 `EmptyBizToolProviderPort`（返回空数组），
  监测类 Tool 已迁到「万象 wanxiang-mcp 上游」由出站平面提供；
- 因此 `wanxiangOutboundMcpTools` 过滤后为 0 个工具，**守卫通道目前不暴露任何 Tool**，
  能力白名单/限流/`OUT` 审计对 `getSystemIntroduction` 也不生效；
- `wanxiang.mcp.server-enabled` 默认 `false`（`application.yml`），开关本身也是关闭的。

结论：**当前 `/mcp` 对外只提供 `zhishu-mcp` 的 `getSystemIntroduction`**，
与 [mcp-service-guide.md](./mcp-service-guide.md) 的工具列表一致。上表是保留的能力模型，
待业务 Tool 重新落回 `BizToolProviderPort` 或改为「按 Client 白名单透出上游工具」时才会重新生效。

### 4.5 入站审计的缺口

`ai_mcp_call_log.direction=OUT` 是「他方调用本平台」的记录，但它挂在 `McpGuardedToolCallback` 上。
由于 §4.4 的现状，**未走守卫通道的 Tool（含 `getSystemIntroduction`）不会写审计**。
若需要覆盖全部入站调用，可在 `McpAuthFilter` 层记录 `tools/call` 或为所有 Provider 统一加壳。

---

## 5. 出站平面：接入他方 MCP Server

### 5.1 上游登记校验（`McpUpstreamAdminService`）

| 校验 | 行为 |
| ---- | ---- |
| `code` 唯一 | 小写归一后查重，重复抛「上游编码已存在」；DTO 侧限制 `^[a-z][a-z0-9_-]{1,63}$` |
| 协议白名单 | `STDIO`/`COMMAND` 直接拒绝（`McpUpstreamConnectionManager.rejectStdio`，生产禁止本地进程型上游）；其余只接受 `STREAMABLE_HTTP`/`HTTP`/`SSE` |
| 环回保护 | `rejectSelfLoop`：`localhost/127.0.0.1/::1` + 本机端口 + `/mcp` 路径 → 禁止，防止把自己配成上游形成工具环回 |
| URL 归一 | `base_url` 去掉尾部 `/`；`endpoint` 缺省 `/mcp`；`request_timeout_ms` 缺省 20000 |
| 凭证加密 | `auth_header_enc = CredentialCrypto.encrypt(authHeader, wanxiang.mcp.crypto-key)`；未配置 crypto-key 时**明文落库**（启动仅 WARN） |
| 创建后试连 | `probeQuietly`：ENABLED 且 client 开关打开时立即连一次，失败只写健康字段不阻断保存 |
| 更新后重连 | 先 `disconnect(id)` 释放旧连接，再试连刷新健康与工具缓存 |
| 删除 | `disconnect` → 删工具缓存 → 删智能体绑定 → 删上游 |

### 5.2 连接与工具发现（`McpUpstreamConnectionManager`）

```text
connectAndList(upstream)   // synchronized，避免并发重复建连
  ├─ closeQuietly(id)                       // 幂等重建
  ├─ openClient(upstream)
  │    ├─ rejectStdio(protocol)
  │    ├─ auth = CredentialCrypto.decrypt(auth_header_enc, crypto-key)
  │    ├─ SSE          → HttpClientSseClientTransport.builder(baseUrl).sseEndpoint(endpoint)
  │    └─ STREAMABLE_HTTP/HTTP → HttpClientStreamableHttpTransport.builder(baseUrl).endpoint(endpoint)
  │    ├─ httpRequestCustomizer：注入 Authorization: Bearer <auth>
  │    │                         + X-On-Behalf-Of: <当前登录用户>（UserContext 非空时）
  │    ├─ McpClient.sync(transport).requestTimeout(timeout).build()
  │    └─ client.initialize()               // 握手失败 → markHealth(DOWN) + BusinessException
  ├─ SyncMcpToolCallbackProvider(client).getToolCallbacks()
  ├─ persistToolCache(upstream, tools)      // 截断到 max-tools-per-upstream（默认 40）
  ├─ markHealth(UP, "ok, tools=N") / circuit.success
  └─ 失败 → circuit.failure(id) 并抛出
```

`openClient` 的异常会翻译成人话（`explainFailure`）：连接被拒 → 「无法连接 xxx（对端未启动）」；
含 `failed to initialize` → 「握手失败 xxx（服务未启动、路径/协议不匹配或鉴权失败）」，
并逐层打印 `Caused by` 便于排查。

**工具缓存落库规则**（`persistToolCache`）：

1. 读取旧缓存，记住每个 `original_name` 的 `enabled`（管理员手工启停状态）；
2. 整批删除后重插（保持「以对端为准」的全量刷新）；
3. `exposed_name = "ext_" + sanitize(code) + "_" + original_name`
   （`sanitize` 转小写、非 `[a-z0-9_]` 一律替换为 `_`），用于避免多上游同名工具冲突；
4. 新工具默认 `enabled=true`，老工具沿用上次的人工开关。

`callbacks(upstreamId)` 是运行时取工具的唯一入口：

- 熔断打开 → 直接返回空列表（不抛异常，避免拖垮整轮会话）；
- 无连接 → 查库确认 ENABLED 后现场建连；
- 有连接 → 重新 `SyncMcpToolCallbackProvider(client).getToolCallbacks()`（按 Spring AI 实现会再发一次
  `tools/list`），失败则 `circuit.failure` + 断开连接并返回空列表。

### 5.3 熔断（`McpUpstreamCircuit`）

| 参数 | 值 |
| ---- | -- |
| 失败阈值 | 3 次 |
| 摘除时长 | 60 秒 |
| 成功 | 立即清零 |
| 存储 | 进程内 `ConcurrentHashMap<upstreamId, State>`（**多实例不共享**） |

### 5.4 智能体绑定与运行时装配

绑定入口：`AgentDefinitionServiceImpl.create/update` 调用
`externalToolPort.bindAgentUpstreams(agentId, dto.mcpUpstreamIds())`；
`AgentVO` 通过 `listBoundUpstreamIds` 回显绑定的上游 id 列表；删除 Agent 时解绑。

`bindAgentUpstreams` 语义：

- `upstreamIds` 去空、去重；
- 数量上限 `wanxiang.mcp.max-upstreams-per-agent`（默认 5），超限抛异常；
- **先全删后全插**（整体替换语义）；逐个校验上游是否存在；
- `allowed_tools` 目前不通过 API 写入，因此运行时白名单为空 → 视为「该上游下所有启用工具都可用」。

运行时装配（`McpExternalToolAdapter.resolveForAgent`）：

```text
agentId
  ├─ 开关检查：wanxiang.mcp.enabled && client-enabled
  ├─ 查 ai_agent_mcp_upstream 绑定
  └─ 对每个绑定：
       ├─ 上游必须存在且 ENABLED
       ├─ 读 ai_mcp_upstream_tool 缓存 → Map<original_name, 行>
       ├─ allow = bind.allowed_tools（空 = 不限制）
       ├─ remote = connectionManager.callbacks(upstreamId)   // 实连，可能空
       └─ 每个 remote Tool：
            ├─ 缓存里 enabled=false → 跳过（管理员禁用）
            ├─ exposed = 缓存 exposed_name（缺省现算 ext_<code>_<原名>）
            ├─ allow 非空且不在其中 → 跳过
            └─ wrap：PrefixedToolCallback(名称=exposed, 描述前缀 "[MCP:<code>] ")
                     → InboundAuditingToolCallback(upstreamId, agentId)
```

`PrefixedToolCallback` 只改**对外名称与描述**，入参 schema 原样透传，实际调用仍转给上游原始回调。

### 5.5 与 Agent 运行时的集成点

| 位置 | 作用 |
| ---- | ---- |
| `shared/mcp/ExternalToolPort` | mcp 模块向外提供的端口（`resolveForAgent` / `listBoundUpstreamIds` / `bindAgentUpstreams`），避免 Agent 模块直接依赖 mcp 内部实现 |
| `agent/runtime/ChatClientSupport.resolveTools(caps, traces, onProgress, agentId)` | 先取能力内置工具（`ToolCapabilityRegistry`），再 `agentId != null` 时追加 `externalToolPort.resolveForAgent(agentId)`，最后统一套 `TracingToolCallback` 供执行监控 |
| `ChatClientAgentEngine` / `GraphWorkflowExecutor` / `AiChatServiceImpl` | 三条主要调用路径都传了 `agent.getId()`，因此都能拿到上游 MCP 工具 |
| `agent/enums/AgentCapability.MCP_TOOLS` | 只是提示词层面的能力标记（`AgentPromptDraftService` 用它生成「业务数据请调用已绑定的上游 MCP 工具」规则），**不参与工具过滤** |

### 5.6 会话直出：在线概览短路（`McpTerminalOverviewPort`）

为避免「模型只写说明不贴明细」，`AiChatServiceImpl` 在识别到在线概览类问题时直接短路出 Markdown 表格，
数据来源就是上游 MCP：

```text
build(limit)
  ├─ pickUpstream()：优先 code=wanxiang-monitor 且 ENABLED；
  │                  否则任取 ENABLED 上游（健康 UP 优先）
  ├─ isToolEnabledInDb(upstreamId, "getTerminalOnlineOverview")
  │     缓存中存在且 enabled=false → 视为不可用
  │     缓存为空（尚未探测）→ 允许尝试
  ├─ connectionManager.callbacks(upstreamId) → 按名字后缀匹配
  │     未找到 → connectAndList 强制重连再匹配一次
  ├─ tool.call("{\"limit\":N}")（空结果再降级为 "{}"）
  └─ parse：
        · 纯文本 → 直接当 Markdown
        · JSON  → unwrap MCP content 包裹（支持 [ {type,text} ] / {result} / {content} / {data}）
                  → 读 total/onlineCount/offlineCount/items/markdown
                  → 拼 Overview(total, online, offline, items, summary, markdown, structured)
```

`StationCompareResult` 结构会被 SSE/结构化响应复用，等于把「上游 MCP 工具输出」适配成平台既有表格协议。

### 5.7 启动预热（`McpSchemaInitializer`）

`@Order(30)` 的 `ApplicationRunner`：

1. 执行 `archive/migration_ai_mcp.sql`（`continueOnError=true`，失败只 WARN，不阻断启动）；
2. `wanxiang.mcp.crypto-key` 为空 → WARN「上游 Authorization 将明文存储」；
3. `enabled && client-enabled` 时，遍历 `ai_mcp_upstream.status = ENABLED` 逐个 `connectAndList`，
   失败仅 WARN（记 `code` 与原因），不阻断启动。

---

## 6. 控制面 API（`/api/v1/mcp`）

`McpHubController`，读接口仅需登录，写接口统一要求 `ai:mcp:edit`（权限行 `sys_menu.id=10411`，
见 `db/archive/patch_menu_biz_permissions.sql`）。

| 方法 | 路径 | 权限 | 说明 |
| ---- | ---- | ---- | ---- |
| GET | `/overview` | 登录 | 概览：`serverEnabled`、`endpoint`、Client 数、上游数、启用上游数、`cryptoConfigured` |
| GET | `/catalog` | 登录 | 对外默认能力码与默认工具名（当前均为空列表） |
| GET | `/clients` | 登录 | 对外 Client 列表（不含明文 Key） |
| POST | `/clients` | `ai:mcp:edit` | 创建 Client，响应携带**一次性明文 Key** |
| PUT | `/clients/{id}` | `ai:mcp:edit` | 更新名称/绑定用户/能力/RPM/状态/备注 |
| POST | `/clients/{id}/rotate-key` | `ai:mcp:edit` | 轮换 Key，响应携带新明文 Key（旧 Key 立即失效） |
| DELETE | `/clients/{id}` | `ai:mcp:edit` | 删除 Client |
| GET | `/upstreams` | 登录 | 上游列表（含健康状态、工具数） |
| POST | `/upstreams` | `ai:mcp:edit` | 登记上游，保存后立即试连 |
| PUT | `/upstreams/{id}` | `ai:mcp:edit` | 更新上游，断开旧连接后重连 |
| DELETE | `/upstreams/{id}` | `ai:mcp:edit` | 删除上游（连带工具缓存与 Agent 绑定） |
| POST | `/upstreams/{id}/probe` | `ai:mcp:edit` | 探活并刷新 `tools/list`（失败会抛业务异常） |
| GET | `/upstreams/{id}/tools` | 登录 | 上游工具缓存 |
| PUT | `/upstreams/{id}/tools` | `ai:mcp:edit` | 按 `originalName` 启停单个工具 |
| GET | `/calls` | 登录 | 调用审计，`direction` 取 `IN` 或 `OUT`（不传=全部）、`limit` 默认 50，夹紧到 1~200 |

前端对应页面：`zhishu-frontend/src/views/ai/McpHub.vue`（菜单 `sys_menu.id=104`，路由 `/ai/mcp`，
组件 `views/ai/McpHub.vue`），三个 Tab：

- **接入上游**：卡片列表 + 健康标签（UP/DOWN/UNKNOWN）+ 工具抽屉 + 「粘贴 curl / MCP URL 自动解析填表」；
- **对外 Client**：卡片 + 能力选择（`MCP_TOOLS`、`KNOWLEDGE_GRAPH`）+ RPM 说明 + 一次性 Key 展示，
  点击卡片弹出「Cursor 配置 / curl 探测」接入指引（`Authorization: Bearer wxmcp_*`）；
- **调用日志**：按方向过滤的审计表（时间/方向/Tool/结果/耗时/错误）。

> 概览里的 `serverEnabled` 取的是 `wanxiang.mcp.enabled && wanxiang.mcp.server-enabled`，
> 与 `spring.ai.mcp.server.enabled` **不是同一个开关**。默认 `wanxiang.mcp.server-enabled=false`，
> 页面会显示「对外服务未启用」，但 `/mcp` 端点实际由 `zhishu-mcp` 提供并可用。

---

## 7. 配置项清单

| 配置 | 默认值 | 作用 |
| ---- | ------ | ---- |
| `spring.ai.mcp.server.enabled` | `true`（`ZHISHU_MCP_SERVER_ENABLED`） | MCP Server 自动装配总开关 |
| `spring.ai.mcp.server.protocol` | `STREAMABLE` | 传输协议 |
| `spring.ai.mcp.server.streamable-http.mcp-endpoint` | `/mcp` | 协议端点路径 |
| `spring.ai.mcp.client.enabled` / `.type` / `.toolcallback.enabled` | `true` / `SYNC` / `true` | Spring AI 自动装配的 MCP Client 连接（Hub 上游为手工建连，不依赖它） |
| `zhishu.mcp.server.enabled` | `true` | `zhishu-mcp` 模块开关（关掉则不注册系统 Tool 与它的 Filter） |
| `zhishu.mcp.server.auth-key` | 空（`ZHISHU_MCP_AUTH_KEY`） | `/mcp` 第二层 Header 鉴权；空=放行；**生产必填** |
| `zhishu.mcp.server.auth-header` | `X-API-Key` | 第二层鉴权的 Header 名 |
| `wanxiang.mcp.enabled` | `true` | Hub 总开关（出站解析、启动预热、Client 开关的前置条件） |
| `wanxiang.mcp.server-enabled` | `false`（`application.yml` 显式关闭） | 是否装配「带守卫的对外 Tool 目录」`wanxiangOutboundMcpTools` |
| `wanxiang.mcp.client-enabled` | `true` | 是否允许出站连接/探活/预热 |
| `wanxiang.mcp.crypto-key` | 空 | 上游 `Authorization` 加密密钥（空=明文落库，仅 WARN） |
| `wanxiang.mcp.default-rpm` | `60` | 新 Client 的默认每分钟调用上限 |
| `wanxiang.mcp.max-upstreams-per-agent` | `5` | 单个 Agent 可绑定的上游数量上限 |
| `wanxiang.mcp.max-tools-per-upstream` | `40` | 单个上游入库的工具条数上限（超出截断） |
| `server.port` | `8180` | 环回校验会用到（判断是否指向本机 `/mcp`） |

生产守卫（`zhishu-core/config/ProductionConfigGuard`）：`zhishu.mcp.server.enabled=true` 时
`zhishu.mcp.server.auth-key` 不能为空，否则**拒绝启动**；应急可用 `ZHISHU_PROD_GUARD_ENABLED=false`。

---

## 8. 关键流程时序

### 8.1 外部 Client 调用本平台 Tool

```text
Client                         ai.McpAuthFilter            zhishu-mcp.Filter        MCP Server / Tool
  │ POST /mcp initialize             │                          │                        │
  │ Authorization: Bearer wxmcp_…    │                          │                        │
  ├─────────────────────────────────▶│ 校验 Key → CallerContext │                        │
  │                                  ├─────────────────────────▶│ 校验 X-API-Key（若配置）│
  │                                  │                          ├───────────────────────▶│ initialize
  │◀─────────────────────────────────┴──────────────────────────┴────────────────────────┤ serverInfo
  │ POST /mcp tools/call             │                          │                        │
  ├─────────────────────────────────▶│                          │                        │
  │                                  │                          │  ToolCallbackProvider  │
  │                                  │                          │  ├ getSystemIntroduction│
  │                                  │                          │  └（守卫通道，当前为空）│
  │◀─────────────────────────────────────────────────────────────┴────────────────────────┤ content[]
```

### 8.2 Agent 会话调用上游 MCP 工具

```text
用户消息 → AiChatServiceImpl / ChatClientAgentEngine
  → ChatClientSupport.resolveTools(caps, traces, onProgress, agentId)
      → ExternalToolPort.resolveForAgent(agentId)
          → ai_agent_mcp_upstream（绑定）
          → ai_mcp_upstream_tool（启用状态 / exposed_name）
          → McpUpstreamConnectionManager.callbacks(upstreamId)
              → 熔断? → 空
              → 连接? → McpSyncClient → tools/list
          → PrefixedToolCallback + InboundAuditingToolCallback
  → ChatClient 调 LLM（模型网关 AGENT 通道）
  → 模型发起 tool call → 回调 → 上游 /mcp tools/call（带 Authorization + X-On-Behalf-Of）
  → 写 ai_mcp_call_log(direction=IN, upstream_id, agent_id)
  → 结果回灌模型 → 生成最终回答
```

### 8.3 探活 / 刷新工具

```text
POST /api/v1/mcp/upstreams/{id}/probe  (ai:mcp:edit)
  → McpUpstreamAdminService.probe → connectionManager.connectAndList
      → 重建连接 → tools/list → 覆盖 ai_mcp_upstream_tool（保留人工 enabled）
      → ai_mcp_upstream.health_status = UP/DOWN + last_probe_at
  → 返回最新 McpUpstreamVO（含 toolCount）
```

---

## 9. 现状盘点与演进建议

| # | 现状 | 影响 | 建议 |
| - | ---- | ---- | ---- |
| 1 | `McpOutboundCatalog` 与 `BizToolProviderPort` 均为空实现 | `/mcp` 只暴露 `getSystemIntroduction`，能力白名单/RPM/`OUT` 审计链处于「空转」 | 明确取舍：要么把平台业务 Tool 重新落回 `BizToolProviderPort`，要么把「上游工具按 Client 白名单对外」做成正式能力 |
| 2 | 两个 `McpAuthFilter` 同时拦 `/mcp` | 生产必须同时提供 `Authorization: Bearer wxmcp_*` 与 `X-API-Key`，接入方容易踩坑 | 二选一收敛（建议保留 Hub 的 `wxmcp_*`，把 `zhishu.mcp.server.auth-key` 定位为开关或直接合并校验） |
| 3 | 前端 Client 能力选项为 `MCP_TOOLS`/`KNOWLEDGE_GRAPH`，后端 `allowedTools` 不识别 `MCP_TOOLS` | 勾选「上游 MCP 工具」不会带来任何授权效果 | 统一能力码字典；或让 `MCP_TOOLS` 映射到「该 Client 可用上游工具」 |
| 4 | `ai_mcp_upstream_tool.enabled=false` 只影响 Agent 侧装配与在线概览短路 | 从上游直连方式的调用仍可用该工具 | 在对外透出上游工具时同样以 `enabled` 为准 |
| 5 | 熔断状态、限流窗口均为进程内内存 | 多实例部署时阈值实际被放大 N 倍，重启即清空 | 需要严格限流时换 Redis/网关层实现 |
| 6 | `wanxiang.mcp.crypto-key` 未配置时上游凭证明文落库 | 库被读取即泄露上游凭证 | 与 `zhishu.ai.crypto-key` 一样纳入生产必填校验 |
| 7 | `callbacks()` 每次都会重新 `tools/list` | 每轮 Agent 装配多一次网络往返，上游慢时放大延迟 | 按 TTL 缓存工具定义，仅在 probe/失败时刷新 |
| 8 | 无 MCP 相关单元/集成测试（`src/test` 下无 `*Mcp*`） | 改动易回归（尤其双 Filter、工具缓存刷新） | 至少补：`McpAuthService` 鉴权与白名单、`persistToolCache` 开关保持、`rejectSelfLoop` |

---

## 10. 相关文件索引

**`zhishu-mcp`**

- `zhishu-mcp/pom.xml`
- `zhishu-mcp/src/main/java/cn/datafuturex/zhishu/mcp/config/McpServerAutoConfiguration.java`
- `zhishu-mcp/src/main/java/cn/datafuturex/zhishu/mcp/config/McpServerProperties.java`
- `zhishu-mcp/src/main/java/cn/datafuturex/zhishu/mcp/security/McpAuthFilter.java`
- `zhishu-mcp/src/main/java/cn/datafuturex/zhishu/mcp/tool/SystemIntroductionTool.java`

**`zhishu-ai`（mcp 包）**

- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/mcp/package-info.java`
- `.../mcp/client/McpUpstreamConnectionManager.java`
- `.../mcp/config/{McpAuthFilter,McpOutboundToolsConfiguration,McpProperties,McpSchemaInitializer}.java`
- `.../mcp/controller/McpHubController.java`
- `.../mcp/service/{McpAuthService,McpClientAdminService,McpUpstreamAdminService,McpExternalToolAdapter,McpTerminalOverviewPort,McpCallLogService}.java`
- `.../mcp/support/{McpGuardedToolCallback,McpRateLimiter,McpUpstreamCircuit,InboundAuditingToolCallback,PrefixedToolCallback,McpCallerContext,McpJson,McpOutboundCatalog}.java`
- `.../mcp/domain/**`、`.../mcp/mapper/**`
- `zhishu-ai/src/main/resources/archive/migration_ai_mcp.sql`

**跨模块约定与集成点**

- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/shared/mcp/ExternalToolPort.java`
- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/shared/crypto/CredentialCrypto.java`
- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/biztools/api/{BizToolProviderPort,TerminalOverviewPort}.java`
- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/agent/runtime/ChatClientSupport.java`
- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/agent/service/impl/AgentDefinitionServiceImpl.java`
- `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/chat/service/impl/AiChatServiceImpl.java`
- `zhishu-security/src/main/java/cn/datafuturex/zhishu/config/security/{SecurityConfig,JwtAuthenticationFilter}.java`
- `zhishu-core/src/main/resources/application.yml`、`application-prod.yml`
- `zhishu-core/src/main/java/cn/datafuturex/zhishu/config/ProductionConfigGuard.java`
- `zhishu-core/src/main/resources/db/init_ai.sql`、`db/archive/patch_menu_biz_permissions.sql`
- `zhishu-frontend/src/views/ai/McpHub.vue`

**相关文档**

- [mcp-service-guide.md](./mcp-service-guide.md)：对外接入指南（端点、鉴权头、SDK 调用示例）
- [model-gateway-design.md](./model-gateway-design.md)：Agent 调用模型所经的网关通道
- [agent-execution-monitor-design.md](./agent-execution-monitor-design.md)：Tool 调用的追踪与监控

---

## 11. 迁移后结构（as-built，2026-10-27）

MCP 已全部合并进 **`zhishu-mcp` 单模块**，内部按两个不交叉的平面划分；`zhishu-ai` 的 `ai.mcp` 包已删除。

### 11.1 模块与依赖方向

```text
zhishu-api ← zhishu-security ← zhishu-mcp ← zhishu-ai ← zhishu-core
```

- `zhishu-mcp` **不依赖 AI 内核**（由 `zhishu-core` 的 `McpArchitectureGuardTest` 强制）；
  `zhishu-ai` 反向依赖 `zhishu-mcp`，且只能使用其导出契约 `consume.api` / `provide.spi`。
- 两个 MCP starter（`spring-ai-starter-mcp-server-webmvc` / `-mcp-client`）已从 `zhishu-ai` 移到 `zhishu-mcp`。

### 11.2 包结构（`cn.datafuturex.zhishu.mcp`）

| 包 | 平面 | 内容 |
| -- | ---- | ---- |
| `provide.security` | 对外提供 | `McpEndpointAuthFilter`（单过滤器双凭据）、`McpClientAuthenticator`、`McpCallerContext`（`CLIENT_KEY` / `PLATFORM_JWT`） |
| `provide.catalog` | 对外提供 | `McpProvidedToolCatalog`（公开目录 + 能力字典）、`McpProvidedTool`、`SystemIntroductionTool` |
| `provide.guard` | 对外提供 | `McpGuardedToolCallback`（**覆盖全部对外 Tool**）、`McpRateLimiter`（键改 String） |
| `provide.config` | 对外提供 | `McpProvideAutoConfiguration`（`ToolCallbackProvider` 装配） |
| `provide.spi` | 对外出口 | `McpToolContributor`（由 AI 实现） |
| `provide.client` | 对外提供 | Client 管理（`McpClientAdminService` + entity/mapper/dto/vo） |
| `consume.api` | 对外出口 | `McpAgentToolPort`（原 `ExternalToolPort`）、`McpTerminalOverviewQuery` + 中立 `McpOnlineOverview` |
| `consume.client` / `circuit` / `tool` / `runtime` / `upstream` | 接入他方 | 连接管理、熔断、改名与审计包装、上游管理与实体、`McpAgentToolAdapter`、`McpOnlineOverviewQuery` |
| `common.config` / `json` / `schema` / `audit` | 共享内层 | `McpProperties`（`zhishu.mcp.*`）、`McpJson`、`McpSchemaInitializer`、审计实体/Mapper/`McpCallLogService`/`McpCallDirection` |
| `web` | 控制面 | `McpProvideAdminController`、`McpConsumeAdminController`、`McpOverviewController`、`McpExceptionAdvice` |

表归属随之拆分：`ai_mcp_client` → provide，`ai_mcp_upstream*` / `ai_agent_mcp_upstream` → consume，
`ai_mcp_call_log` → common（共用）；建表脚本移到 `zhishu-mcp/src/main/resources/db/mcp_schema.sql`（对象名已全写 `public.`）。

### 11.3 行为变更清单

| 项 | 迁移前 | 迁移后 |
| -- | ------ | ------ |
| `/mcp` 鉴权 | 两个过滤器串联（`Bearer wxmcp_*` **且** `X-API-Key`） | 单过滤器二选一：`Bearer wxmcp_*` 或 **平台登录 JWT**；静态 `X-API-Key` 移除 |
| JWT 走 `?token=` | 允许 | **不允许**（仅 `wxmcp_*` 可用，避免 token 落访问日志） |
| 守卫覆盖 | 仅目录内业务 Tool（当前为空），系统 Tool 裸奔 | 所有对外 Tool 统一包 `McpGuardedToolCallback` |
| 能力回落 | `DEFAULT_TOOL_NAMES` 为空集 → 授权集为空 | `PUBLIC_TOOLS = {getSystemIntroduction}`，空能力/未知能力均回落到它 |
| 审计方向 | `OUT`（被调用）/ `IN`（调上游） | `SERVE` / `CONSUME`；读侧兼容旧值，历史数据跑 `db/patch_mcp_call_log_direction.sql` |
| 限流桶 | `Map<Long clientId, …>` | `client:{id}` / `user:{username}` 字符串桶 |
| 配置前缀 | `wanxiang.mcp.*` + `zhishu.mcp.server.*` | `zhishu.mcp.*`（`provide` / `consume` 两组），旧键一次性删除 |
| 生产守卫 | 必填 `ZHISHU_MCP_AUTH_KEY` | 不再要求静态密钥（`ProductionConfigGuard` 该项已删） |
| 启动职责 | `McpSchemaInitializer` 建表 + 预热 | 建表（`@Order(30)`）与预热（`McpConsumeWarmupRunner` `@Order(40)`）分开，避免 common 反向依赖 consume |
| 错误形状 | 由 `AiGlobalExceptionHandler` 处理 | `McpExceptionAdvice`（`basePackages=….mcp.web`）接管，保持 `code`/HTTP 语义不变 |

#### 11.3.1 启动验收发现的两个缺陷（已修）

| 现象 | 根因 | 修复 |
| ---- | ---- | ---- |
| `/mcp` 能调 `getSystemIntroduction`，但 `ai_mcp_call_log` **不写任何记录** | Spring AI 的 `ToolCallbackConverterAutoConfiguration.syncTools()` 会同时收集 **`List<ToolCallback>` Bean** 与 `ToolCallbackProvider` 输出。`SystemIntroductionTool` 作为 `ToolCallback` Bean 被直接聚合，裸实例与守卫实例同名，按聚合顺序抢先 → 守卫被完全绕过 | `McpProvidedTool` 不再 `extends ToolCallback`，改提供 `toolCallback()` 工厂方法；`candidates()` 额外按名称去重。并有用例钉住该不变量（`McpProvidedTool` 不得是 `ToolCallback` 子类型） |
| 审计落了库但 `user_id` 永远为空（迁移前 232 行存量数据同样全空） | `McpCallLogService` 是 `@Async`，写入已离开请求线程，在插入处读 `UserContext` ThreadLocal 必为 null | 调用方身份改为**入参传递**：provide 侧用 `caller.username()`（缺失回落 `boundUserId`），consume 侧在包装器内先取 `UserContext.getUserId()` 再传入 |

两个缺陷都由真实启动 + HTTP 冒烟才暴露，单测与编译均发现不了；已补对应的回归用例。

#### 11.3.2 `/mcp` 实际冒烟结果（dev，:8180）

| 场景 | 结果 |
| ---- | ---- |
| 无凭据 | `401 {"error":"missing_credential"}` |
| 错 Client Key | `401 {"error":"invalid_mcp_key"}` |
| JWT 走 `?token=` | `401 {"error":"missing_credential"}`（符合定稿：JWT 必须走 Header） |
| 非 `wxmcp_` 且非法 token | `401 {"error":"invalid_jwt"}` |
| `wxmcp_*` initialize / notify / tools/list / tools/call | `200` / `202` / 目录仅 `getSystemIntroduction` / 返回系统介绍内容 |
| 调用未授权工具 | 协议层 `-32602 Unknown tool`（该工具本就不在 `tools/list`，与守卫的「未授权」分支互补） |
| 平台 JWT 同一流程 | 全部通过，目录同样仅公开目录 |
| 审计写入 | `SERVE`；Client Key 调用带 `client_id=3, user_id=admin`；JWT 调用 `client_id=NULL, user_id=admin` |

### 11.4 类名映射（方便对旧文档）

| 旧 | 新 |
| -- | -- |
| `ai.mcp.config.McpAuthFilter` | `mcp.provide.security.McpEndpointAuthFilter` |
| `mcp.security.McpAuthFilter`（zhishu-mcp） | 已删除（能力合并入上面的过滤器） |
| `ai.mcp.service.McpAuthService` | `mcp.provide.security.McpClientAuthenticator` |
| `ai.mcp.support.McpOutboundCatalog` | `mcp.provide.catalog.McpProvidedToolCatalog` |
| `ai.mcp.config.McpOutboundToolsConfiguration` | `mcp.provide.config.McpProvideAutoConfiguration` |
| `ai.mcp.service.McpExternalToolAdapter` | `mcp.consume.runtime.McpAgentToolAdapter` |
| `ai.mcp.service.McpTerminalOverviewPort` | `mcp.consume.runtime.McpOnlineOverviewQuery`（+ ai 侧 `McpTerminalOverviewAdapter`） |
| `ai.mcp.support.InboundAuditingToolCallback` | `mcp.consume.tool.UpstreamAuditingToolCallback` |
| `ai.mcp.service.McpCallLogService` | `mcp.common.audit.McpCallLogService`（`recordServe` / `recordConsume`） |
| `ai.shared.mcp.ExternalToolPort` | `mcp.consume.api.McpAgentToolPort` |
| `ai.shared.{UserContext, exception.BusinessException, crypto.CredentialCrypto}` | `zhishu-security` 的 `common.context` / `common.exception` / `common.crypto` |

### 11.5 测试护栏

- `zhishu-mcp`（共 38 项）：`McpEndpointAuthFilterTest`（双凭据与清理 ThreadLocal）、`McpClientAuthenticatorTest`、
  `McpProvideAutoConfigurationTest`（上下文级，含“无贡献者也能启动”与“`McpProvidedTool` 不得是 `ToolCallback`”）、
  `McpGuardedToolCallbackTest`（白名单/限流/身份归属）、`McpProvidedToolCatalogTest`、
  `McpRateLimiterTest`、`McpCallDirectionTest`。
- `zhishu-core/architecture.McpArchitectureGuardTest`：provide ✕ consume 双向禁止、common 不反向依赖、
  两平面不依赖 web、mcp 不依赖 ai、ai 只用 mcp 导出契约。
- `GatewayDependencyGuardTest` 禁止清单里的 `ai.mcp..` 已换成 `cn.datafuturex.zhishu.mcp..`。
