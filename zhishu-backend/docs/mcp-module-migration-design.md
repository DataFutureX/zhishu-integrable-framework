# MCP 模块合并与独立化迁移设计（分析稿）

> 目标：把 `zhishu-ai` 内的 `ai.mcp` 包（41 个 Java 文件）整体迁入 `zhishu-mcp`，
> 与 `zhishu-mcp` 现有的对外 MCP 能力合并为**一个自洽的 MCP 模块**；
> 模块内部严格分为 **provide（我方对外提供）** 与 **consume（接入他方提供）** 两个平面，**互不交叉**。
>
> 本文为设计与分析，未改动任何代码。实现顺序见 §8，待确认的决策点见 §9。
> 现状说明见 [mcp-hub-design.md](./mcp-hub-design.md)，对外协议见 [mcp-service-guide.md](./mcp-service-guide.md)。

---

## 1. 现状事实（已核对，作为设计约束）

| # | 事实 | 对设计的影响 |
| - | ---- | ------------ |
| 1 | `ai.mcp` **没有任何外部生产代码 import**（仅注释提及）；对外只通过 `ai.shared.mcp.ExternalToolPort`、`ai.biztools.api.TerminalOverviewPort` 两个端口暴露能力 | 搬迁是「移动 + 改包名」，不需要重写调用方逻辑，可行性高 |
| 2 | `zhishu-mcp` 现在只有 5 个类：`McpServerAutoConfiguration`、`McpServerProperties`、`security.McpAuthFilter`、`tool.SystemIntroductionTool`、`package-info`；pom 仅依赖 webmvc + `spring-ai-starter-mcp-server-webmvc` + lombok | 合并后它是 MCP 的唯一宿主模块 |
| 3 | MCP 相关代码依赖 `ai.shared` 的 `Result`/`UserContext`/`BusinessException`/`crypto.CredentialCrypto`/`vo.structured.StationCompareResult`，以及 `ai.biztools.api` 的 `BizToolProviderPort`/`TerminalOverviewPort` | **模块依赖方向是本次设计的核心分歧点**（§3） |
| 4 | 引用 `ai.shared` 的文件数：`zhishu-ai` 82 个、`zhishu-model-gateway` 9 个、`zhishu-biz`/`zhishu-security`/`zhishu-desktop`/`zhishu-core` 0 个 | 把 shared 类型下沉 = 约 91 处 import 重写 |
| 5 | `zhishu-model-gateway` 已经依赖 `zhishu-ai`（pom），`CredentialCrypto` 注释也写明「shared 为 OPEN 模块，跨 Maven 模块可合法引用」 | 「特性模块依赖 zhishu-ai」有既有先例，不是破坏性约定 |
| 6 | `spring-ai-starter-mcp-server-webmvc` 与 `spring-ai-starter-mcp-client` 只被 `ai.mcp` 包内 2 个类使用（`McpUpstreamConnectionManager`、`McpOutboundToolsConfiguration`）；`ToolCapabilityRegistry` 用的是 `ai.support.ToolCallbacks`（属 spring-ai core） | 两个 MCP starter 可以从 `zhishu-ai/pom.xml` **移除**，MCP 自动装配只在 `zhishu-mcp` 声明 |
| 7 | `zhishu-mcp` 与 `ai.mcp` **各有一个 `McpAuthFilter` 同时拦 `/mcp`**：前者校验 `X-API-Key`（order `MIN_VALUE+100`），后者校验 `Bearer wxmcp_*`（order `HIGHEST_PRECEDENCE+20`，先执行） | 生产调用方必须同时带两个头；合并后收敛为**单过滤器 + 双凭据**（Client Key / 登录 JWT） |
| 8 | `McpOutboundCatalog` 与 `BizToolProviderPort` 实现均为空，`wanxiang.mcp.server-enabled` 默认 `false`；`/mcp` 实际只暴露 `getSystemIntroduction`，且它**没被守卫包装** | 能力白名单/RPM/审计目前空转；合并时要让守卫覆盖**全部**对外 Tool |
| 9 | 审计字段方向语义反了：代码写 `OUT` = 对外平面被调用、`IN` = 调用他方上游；`init_ai.sql` 列注释写 `INBOUND=外部调用本平台`（与代码相反） | 平面拆分后必须重命名，否则「不交叉」无法自证 |
| 10 | `ai.mcp` 的 `package-info` 带 `@ApplicationModule(allowedDependencies={"shared","biztools :: api"})`，`AiModulithStructureTest` 会校验它；`GatewayDependencyGuardTest` 的禁止清单含 `cn.datafuturex.zhishu.ai.mcp..` | 迁出后 Modulith 视野内不再有 Mcp 模块，**护栏必须换成 ArchUnit 并新增双平面隔离规则** |
| 11 | `ai.biztools.stub` 的 `EmptyTerminalOverviewPort`/`EmptyBizToolProviderPort` 是带 `@ConditionalOnMissingBean` 的 `@Component` | 实现类跨 jar 后，扫描顺序不再可控，需显式化兜底策略 |
| 12 | 启动脚本 `start-all.cmd`/`start-dev.bat` 走根 reactor；`deployment/Dockerfile` 只跑 `zhishu-core` fat jar | 模块归属调整不影响构建与部署产物 |

---

## 2. 目标与非目标

**目标**

1. `zhishu-mcp` 成为 MCP 的唯一宿主：对外 Server + 他方接入 + 控制面管理 API 全在其中。
2. 模块内两个平面**代码级不交叉**：包隔离 + ArchUnit 强约束，任何一侧改动手指不到另一侧。
3. 合并重复能力：一套鉴权链、一套 Tool 目录装配、一套审计写入、一套配置命名空间、一套错误形状。
4. 尽可能保持行为兼容：前端 `McpHub.vue` 与 `wxmcp_*` 客户端、`/api/v1/mcp/**` 路径与响应形状不变。
   **已确认的故意变更**：移除静态 `X-API-Key`（只保留 `wxmcp_*` 与登录 JWT）、审计 `direction` 取新值。
5. `zhishu-mcp` 白名单化对外：不依赖 `cn.datafuturex.zhishu.ai..` 任何包，可单独裁剪 / 复用。

**非目标（本期不做）**

- 不新增业务 Tool（监测/巡检等仍由上游 MCP 提供）。
- 不引入 Redis 分布式限流/熔断（仍是进程内，明确记录为已知限制）。
- 不改数据库表结构（只调整 `direction` 取值语义）。
- 不做 `ai.shared` 的整体下沉（本期只下沉 MCP 必需的 `UserContext`/`BusinessException`/`CredentialCrypto` 三类，见 §3.1；`Result` 与结构化 VO 原地保留）。

---

## 3. 依赖方向（已确认：选项 B — `zhishu-mcp` 不依赖 AI 内核）

目标依赖图（无环，`zhishu-ai` 反向依赖 `zhishu-mcp`）：

```text
zhishu-api ← zhishu-security ← zhishu-mcp ← zhishu-ai ← zhishu-core
                                ↑              ↑
                        zhishu-desktop ┘   zhishu-model-gateway ┘
```

### 3.1 下沉到 `zhishu-security` 的共享类型

`zhishu-security` 已经是跨模块基础层（`common.Result`、`common.PageResult`、`common.GlobalExceptionHandler`、
`common.SecurityUtils`、`config.security.JwtUtil`、`security.TokenBlacklistService`、`security.RsaCryptoUtils`、
`modules.constant.PermissionConstants`），把 MCP 需要的三类类型并进去是顺势而非新开层：

| 类型 | 现位置 | 目标位置 | 引用文件数 | 备注 |
| ---- | ------ | -------- | ---------- | ---- |
| `UserContext` | `ai.shared` | `cn.datafuturex.zhishu.common.context.UserContext` | 23 | 纯 ThreadLocal，无 AI 依赖 |
| `BusinessException` | `ai.shared.exception` | `cn.datafuturex.zhishu.common.exception.BusinessException` | 22 | `AiGlobalExceptionHandler` 同步改 import |
| `CredentialCrypto` | `ai.shared.crypto` | `cn.datafuturex.zhishu.common.crypto.CredentialCrypto` | 9 | security 已有 crypto 包，语义同族；javadoc 里「原位于 ai.mcp.support」的表述一并更新 |

**不下沉**的类型与原因：

| 类型 | 处理方式 |
| ---- | -------- |
| `ai.shared.Result` | 保持原位。MCP 控制面改用 security 的 `common.Result`。已核对前端 `utils/request.ts` 只读 `code/message/data`，不依赖 AI 版独有的 `timestamp`，响应形状兼容 |
| `ai.shared.vo.structured.StationCompareResult` | 保持原位。MCP 不再实现 AI 端口，改为输出中立类型（§3.3） |
| `ai.biztools.api.*` | 保持原位，由 AI 侧实现 MCP 定义的 SPI（§3.3） |

> 下沉批次是**纯机械 import 重写、行为零变化**，约 50 个文件（三类引用有交叉，去重后 ≈ 50），可独立提交并单独 `mvn verify`。

### 3.2 端口方向反转（消除 `mcp → ai`）

| 现状 | 反转后 | 落点 |
| ---- | ------ | ---- |
| `ai.shared.mcp.ExternalToolPort`（3 处引用，实现在 mcp） | 改为 mcp 侧 `consume.api.McpAgentToolPort`（同三个方法） | `ChatClientSupport`、`AgentDefinitionServiceImpl` 改为依赖 mcp 端口；用 `ObjectProvider` 兜底（MCP 关闭时视为空工具） |
| mcp 通过 `ai.biztools.api.BizToolProviderPort` 取候选 Tool | mcp 定义 SPI `provide.spi.McpToolContributor { List<ToolCallback> contribute(); }`，**AI 侧实现**（`ai.biztools.mcp.BizToolMcpContributor` 委托 `BizToolProviderPort`） | mcp 以 `ObjectProvider<List<McpToolContributor>>` 收集，无实现时对外只剩模块内静态 Tool |
| mcp 实现 `ai.biztools.api.TerminalOverviewPort` | mcp 暴露 `consume.api.McpTerminalOverviewQuery`，返回中立 `McpOnlineOverview`（`found/message/total/online/offline/items[name,time,status,remark]/summary/markdown/rawJson`）；**AI 侧** `McpTerminalOverviewAdapter implements TerminalOverviewPort` 做映射 | MCP content 的 unwrap/容错解析留在 mcp（协议知识），`StationCompareResult` 展示契约留在 ai |

### 3.3 随之需要的边界约束

- `zhishu-ai` 只允许依赖 `..mcp.consume.api..`、`..mcp.provide.spi..`，不得触碰 `..mcp.provide..`/`..mcp.consume..` 内部实现。
  该规则写在 ai 侧新增的 `AiToMcpDependencyTest`（ArchUnit）里，与 `GatewayDependencyGuardTest` 同构。
- `AiModulithStructureTest` 仍会通过（`ai.mcp` 包消失、`shared` 少 3 个类），但 MCP 的内部约束改由 `zhishu-mcp` 自己的 ArchUnit 保证（§7）。
- `GatewayDependencyGuardTest` 的禁止清单：`cn.datafuturex.zhishu.ai.mcp..` → 换成 `cn.datafuturex.zhishu.mcp..`（网关同样不得依赖 MCP 模块内部）。

---

## 4. 目标包结构（zhishu-mcp）

命名约定：**`provide` = 我方对外提供 MCP 服务**（旧文档里的「入站/对外/OUT」），
**`consume` = 接入他方提供的 MCP 服务**（旧「出站/上游/IN」）。

```text
cn.datafuturex.zhishu.mcp
├── package-info.java                      模块说明（不再有 @ApplicationModule，约束改由 ArchUnit 保证）
│
├── common/                                ← 两平面唯一允许共享的内层
│   ├── json/McpJson                        （原 support/McpJson）
│   ├── schema/McpSchemaInitializer         只建表 + 调 consume 预热门面，不含业务规则
│   └── config/McpProperties                zhishu.mcp.*（含 provide / consume 两个嵌套组）
│
├── provide/                                ★ 我方对外提供 MCP 服务（Server 侧）
│   ├── spi/McpToolContributor              【对外出口】AI 侧实现，向本平面贡献候选 Tool
│   ├── security/McpEndpointAuthFilter      合并后唯一的 /mcp 鉴权过滤器（凭据 A/B）
│   ├── security/McpCallerContext           调用方身份（type: CLIENT_KEY | PLATFORM_JWT）
│   ├── security/McpClientAuthenticator     wxmcp_ Key 校验 + 能力解析（原 McpAuthService）
│   ├── catalog/McpProvidedToolCatalog      对外目录 + 能力码→工具名（合并旧 McpOutboundCatalog）
│   ├── catalog/McpProvidedTool             模块内静态 Tool 的注册接口
│   ├── catalog/SystemIntroductionTool      从旧 zhishu-mcp 迁入，改为实现 McpProvidedTool
│   ├── guard/McpGuardedToolCallback        白名单 + RPM + 身份切换 + 审计（**对所有对外 Tool 生效**）
│   ├── guard/McpRateLimiter                key 改为 String（client:{id} / user:{username}）
│   ├── config/McpProvideAutoConfiguration  ToolCallbackProvider 装配（provide.enabled 条件）
│   ├── audit/McpProvideAuditService        写 ai_mcp_call_log，direction=SERVE
│   └── admin/                              对外 Client 管理
│       └── client/{McpClientAdminService, AiMcpClientEntity, AiMcpClientMapper, dto, vo}
│
├── consume/                                ★ 接入他方提供的 MCP 服务（Client 侧）
│   ├── api/McpAgentToolPort                【对外出口】原 ai.shared.mcp.ExternalToolPort，由 ai 消费
│   ├── api/McpTerminalOverviewQuery        【对外出口】返回中立 McpOnlineOverview，由 ai 适配
│   ├── client/McpUpstreamConnectionManager
│   ├── circuit/McpUpstreamCircuit
│   ├── upstream/{McpUpstreamAdminService, AiMcpUpstreamEntity, AiMcpUpstreamToolEntity,
│   │             AiAgentMcpUpstreamEntity, mapper×3, dto, vo}
│   ├── runtime/McpAgentToolAdapter         实现 McpAgentToolPort（原 McpExternalToolAdapter）
│   ├── runtime/McpOnlineOverviewQuery      实现 McpTerminalOverviewQuery（原 McpTerminalOverviewPort）
│   ├── audit/McpConsumeAuditService        写 ai_mcp_call_log，direction=CONSUME
│   └── config/McpConsumeAutoConfiguration  （consume.enabled 条件）
│
└── web/                                    控制面（薄，只做参数与权限）
    ├── McpProvideAdminController            /api/v1/mcp/clients、/catalog
    ├── McpConsumeAdminController            /api/v1/mcp/upstreams、/upstreams/{id}/tools、/probe
    ├── McpOverviewController                /api/v1/mcp/overview、/calls（聚合读，只读不交叉）
    └── McpExceptionAdvice                   【必做】@RestControllerAdvice(basePackages=…mcp)
                                             复刻 AiGlobalExceptionHandler 的 code/HTTP 语义
```

**隔离规则（ArchUnit 强制，见 §7）**

1. `..mcp.provide..` ✕ `..mcp.consume..`：任一方向都不得依赖（编译期禁止 import）。
2. 两平面都不得依赖 `..mcp.web..`；`web` 只能调用两平面的 service / 门面。
3. `common` 不得依赖 `provide`/`consume`（防反向泄漏）。`McpSchemaInitializer` 的「启动预热」改为调用
   `consume.api` 内的 `McpUpstreamWarmup` 门面（实现均在 `provide`/`consume` 之外可见）。
4. `provide` / `consume` 对外只暴露 `provide.spi..` 与 `consume.api..`；ai 只能依赖这两个包。
5. **本模块不得依赖 `cn.datafuturex.zhishu.ai..` 任何包**（选项 B 的硬约束）。
   允许的下层依赖：`cn.datafuturex.zhishu.common..`（下沉后的 UserContext/BusinessException/Result/crypto）、
   `cn.datafuturex.zhishu.modules.constant..`（`PermissionConstants`）、`cn.datafuturex.zhishu.config.security.JwtUtil`
   与 `cn.datafuturex.zhishu.security.TokenBlacklistService`（均在 zhishu-security），以及 Spring / Spring AI / MyBatis 框架包。

---

## 5. 能力合并点（逐项设计）

### 5.1 鉴权：一个 Filter、两种凭据（已确认）

两个平面合并为**唯一**的 `McpEndpointAuthFilter`，只接受两种 `Authorization: Bearer` 凭据：

```text
POST /mcp
  └─ McpEndpointAuthFilter（provide.security，@Order(HIGHEST_PRECEDENCE + 20)）
       ├─ 统一 CORS（含 Mcp-Session-Id 暴露）+ OPTIONS 预检 204
       ├─ 取 token：Authorization: Bearer …（兼容 ?token=，沿用现行为）
       ├─ 判定：token 以 "wxmcp_" 开头 → 凭据 A；否则 → 凭据 B
       │
       ├─ A) 平台外 Client Key：McpClientAuthenticator
       │      key_prefix(前14) 查 ai_mcp_client → status=ENABLED → SHA-256 比对 secret_hash
       │      → Caller(type=CLIENT_KEY, clientId, boundUserId/Username,
       │               allowedTools=能力码交集, rpm=client.rpmLimit)
       │      → touch last_used_at；失败 401 {"error":"invalid_mcp_key"}
       │
       ├─ B) 平台登录态 JWT：复用 zhishu-security 的 JwtUtil + TokenBlacklistService
       │      jwtUtil.validateToken(token) && !tokenBlacklistService.isRevoked(token)
       │      → username = jwtUtil.getUsernameFromToken(token)
       │      → Caller(type=PLATFORM_JWT, clientId=null, username,
       │               allowedTools=PUBLIC_TOOLS, rpm=zhishu.mcp.provide.jwt-default-rpm)
       │      → 失败 401 {"error":"invalid_jwt"}
       │
       ├─ 缺失凭据 → 401 {"error":"missing_credential"}
       ├─ 注入 UserContext（A：绑定用户；B：JWT 用户，与 UserContextInterceptor 的 username 口径一致）
       └─ finally：清 McpCallerContext + UserContext
```

设计要点与连带改动：

| 项 | 结论 |
| -- | ---- |
| 静态 `X-API-Key` | **移除**：删 `zhishu-mcp/security/McpAuthFilter` 与 `FilterRegistrationBean`、`zhishu.mcp.server.auth-key`/`auth-header`、`ProductionConfigGuard` 的该项校验、`.env.example` 的 `ZHISHU_MCP_AUTH_KEY`、`deployment/README.md` 必填项表 |
| 为什么不交给 Security 链处理 JWT | `/mcp` 还要接受非 JWT 的 `wxmcp_*`；若把 `/mcp` 改判为 `authenticated()` 会与 Client Key 分支互斥。保持 `SecurityConfig` 对 `/mcp` permitAll + `JwtAuthenticationFilter` 跳过 `/mcp` 不变，由 MCP Filter 单点决策，可测且行为集中 |
| 限流键 | `McpRateLimiter` 的 key 由 `Long clientId` 改为 `String`：`client:{id}` / `user:{username}`，窗口仍 60s |
| JWT 模式的可调用集合 | 本期 = 公开目录 `PUBLIC_TOOLS`（含 `getSystemIntroduction`）。是否按权限码（如 `ai:mcp:edit`）或按 Agent 能力进一步收敛 → §9 待确认 |
| 审计归属 | A 写 `client_id` + `user_id`；B 写 `user_id`、`client_id=null`，控制面日志可区分「外部 Client」与「平台用户」 |
| 生产守卫新规则 | `zhishu.mcp.enabled && provide.enabled` 时不再要求任何静态密钥；改为校验「至少一种凭据可用」：Client Key 模式始终可用，故默认满足；若未来加 `provide.client-key-enabled=false` 开关则要求 JWT 模式在线 |
| 灰度兼容 | **不保留（已定稿）**：静态 `X-API-Key` 一次性移除，`zhishu.mcp.provide.legacy-static-key` 不进入代码 |
| `?token=` 查询串凭据 | 仅对 `wxmcp_*` 生效；平台 JWT 必须走 `Authorization` 头（避免 token 落访问日志） |

### 5.2 对外 Tool 目录：合并两套，守卫全覆盖

```java
// provide.config（zhishu.mcp.provide.enabled=true 条件装配）
ToolCallbackProvider mcpProvidedTools(
        McpProvidedToolCatalog catalog,
        ObjectProvider<List<McpToolContributor>> contributors, ...) {
    // 1) catalog.staticTools()  → 模块内 McpProvidedTool（如 SystemIntroductionTool）
    // 2) catalog.contributed()  → AI 侧 SPI 贡献的 Tool（BizToolMcpContributor 委托 BizToolProviderPort）
    // 3) 全部统一包 McpGuardedToolCallback（修掉现状：系统 Tool 不受白名单/限流/审计）
}
```

- `McpProvidedToolCatalog` 承担三件事：① 哪些 Tool 对外（`PUBLIC` 能力码 + 显式名单）；
  ② 能力码 → 工具名映射（原 `McpAuthService.allowedTools` 的 `ONLINE`/`ALERT`/`INSPECTION_*`/`KNOWLEDGE_GRAPH`…）；
  ③ 与 Client `capabilities` 求交集。空目录时 **`getSystemIntroduction` 仍默认公开**（避免迁移后 `/mcp` 变成空端点，保持 `mcp-service-guide.md` 承诺）。
  **目录与能力字典均内置于 mcp，不引用任何 ai 类型。**
- 保留 `DEFAULT_CAPABILITIES`/`DEFAULT_TOOL_NAMES` 语义但改为 `PUBLIC_TOOLS = Set.of("getSystemIntroduction")`。
- `McpGuardedToolCallback` 的 `caller == null` 分支改为返回 `{"error":"unauthenticated"}`，
  并对 `type=PLATFORM_JWT` 跳过 Client 能力交集（登录态直接用公开目录）。

### 5.3 审计：一张表、两套写入、方向重命名

| 平面 | 写入方 | 新 `direction` | 历史值 | 含义 |
| ---- | ------ | -------------- | ------ | ---- |
| provide | `McpProvideAuditService` | `SERVE` | `OUT` | 外部 Client 调用本平台 Tool |
| consume | `McpConsumeAuditService` | `CONSUME` | `IN` | 本平台（Agent）调用上游 Tool |

- 表结构不变（`ai_mcp_call_log`），两个 Service 各自只写自己的 direction 与归属列（`client_id` vs `upstream_id/agent_id`），
  保证「运行数据不交叉」；共表只为控制面「调用日志」一次查询。
- 读侧（`GET /api/v1/mcp/calls`）接受 `SERVE|CONSUME|IN|OUT`，`IN→CONSUME`、`OUT→SERVE` 归一（兼容补丁执行前的窗口）。
- **历史数据一次性归一（已确认）**：新增 `zhishu-mcp/src/main/resources/db/patch_mcp_call_log_direction.sql`
  （`UPDATE ai_mcp_call_log SET direction='SERVE' WHERE direction='OUT'; … 'IN'→'CONSUME'`），
  属破坏性数据变更，**不随启动自动执行**，需人工跑（遵循「启动仅执行非破坏性 SQL」的既有约定）。
  执行顺序：先部署新版本（读侧已兼容旧值）→ 再手工执行 UPDATE → 最后更新前端日志 Tab 的方向标签。
  回滚窗口：旧代码读到 `SERVE/CONSUME` 会当作「无方向过滤」处理，故新旧版本可共存。
- `init_ai.sql` 的 `direction` 列注释同步纠正为 `SERVE=外部调用本平台 / CONSUME=本平台调用上游`。

### 5.4 配置：两个前缀 → `zhishu.mcp.*` 一树

```yaml
zhishu:
  mcp:
    enabled: ${ZHISHU_MCP_ENABLED:true}
    crypto-key: ${ZHISHU_MCP_CRYPTO_KEY:${ZHISHU_AI_CRYPTO_KEY:}}   # 上游 Authorization 加密；默认沿用模型 Key，避免多环境不同 Key 导致解密失败
    provide:                       # ★ 我方对外提供（合并旧 zhishu.mcp.server.* + wanxiang.mcp.server-enabled）
      enabled: ${ZHISHU_MCP_PROVIDE_ENABLED:true}
      endpoint: /mcp
      client-key:
        enabled: true              # 凭据 A：wxmcp_*
      platform-jwt:
        enabled: true              # 凭据 B：平台登录态 JWT
        default-rpm: 120
      default-rpm: 60               # 新建 Client 的默认 RPM
    consume:                       # ★ 接入他方（合并 wanxiang.mcp.*）
      enabled: ${ZHISHU_MCP_CONSUME_ENABLED:true}
      max-upstreams-per-agent: 5
      max-tools-per-upstream: 40
      warmup-on-startup: true
```

- 旧键 `wanxiang.mcp.*` 与 `zhishu.mcp.server.*` **一次性删除（已确认，不留双读）**：
  `application.yml` 直接换为新树；`ProductionConfigGuard` 不再读 `zhishu.mcp.server.auth-key`。
- 上游凭据兼容性：存量 `auth_header_enc` 无 `enc:` 前缀的值（以前未配 crypto-key 时明文入库）仍能被
  `CredentialCrypto.decrypt` 原样返回，新写入才加密 → 改默认 Key 不会弄坏已有上游。
- `ZHISHU_MCP_SERVER_ENABLED` 仍绑 `spring.ai.mcp.server.enabled`（属框架开关，不动）；
  `ZHISHU_MCP_AUTH_KEY` 从 `.env.example` / `deployment/README.md` / `application-prod.yml` 注释中移除。
- 迁移检查项：若测试/生产环境曾显式配过 `wanxiang.mcp.server-enabled` 或 `WANXIANG_MCP_CRYPTO_KEY`，需改名（P3 清单里逐项确认）。

### 5.5 数据归属：MCP 表建表脚本入 `zhishu-mcp`

- `zhishu-ai/src/main/resources/archive/migration_ai_mcp.sql` → `zhishu-mcp/src/main/resources/db/mcp_schema.sql`
  （落到 `db/` 后自动被 `DatabaseScriptSessionStateTest` 的 `classpath*:db/*.sql` 护栏覆盖）。
- `zhishu-core/src/main/resources/db/init_ai.sql` 仍保留全量快照（core 是 DBA 视角的单一初始化脚本），
  但**表 DDL 的事实来源改为 `zhishu-mcp`**，两处需保持一致 → 在 §7 加一条护栏：
  `mcp_schema.sql` 与 `init_ai.sql` 中的 `ai_mcp_%` 表列集合必须一致（字符串级校验即可，避免双源漂移）。

### 5.6 Bean 兜底与装配顺序（跨 jar 风险点）

| 风险 | 处理 |
| ---- | ---- |
| `EmptyTerminalOverviewPort`/`EmptyBizToolProviderPort` 用 `@ConditionalOnMissingBean` 且实现方跨 jar，扫描顺序不可控 | 把 stub 从 `@Component` 改为 `@Bean @ConditionalOnMissingBean` 的显式配置类（`ai.biztools.config.BizToolsFallbackConfiguration`），或改为 `zhishu-core` 里的兜底装配；同时给 `McpTerminalOverviewPort` 标 `@Primary` 以便「两个实现都在」时结果确定 |
| `ChatClientSupport`/`AgentDefinitionServiceImpl` 硬依赖 `ExternalToolPort`，若 `provide/consume` 关闭或 `zhishu-mcp` 缺席则启动失败 | 注入改 `ObjectProvider<ExternalToolPort>`（缺省视为空工具），或保留 ai 侧 `NoOpExternalToolPort` 兜底；推荐前者，语义显式 |
| `spring-ai-starter-mcp-server-webmvc` 从 `zhishu-ai` 移除 | 校验 `zhishu-core` 仍通过 `zhishu-mcp` 引入；`@ConditionalOnProperty(zhishu.mcp.enabled)` 决定 `ToolCallbackProvider` 是否注册，端点本身由框架开关 `spring.ai.mcp.server.enabled` 控制 |

---

## 6. 控制面与前端兼容

| 现路径 | 迁移后归属 | 变化 |
| ------ | ---------- | ---- |
| `GET /api/v1/mcp/overview` | `McpOverviewController` | 响应字段兼容；`serverEnabled` 改为 `zhishu.mcp.enabled && provide.enabled`，**修掉现状里误用 `wanxiang.mcp.server-enabled` 的语义错位**（前端「对外服务未启用」提示随之正确） |
| `GET /api/v1/mcp/catalog` | `McpProvideAdminController` | 返回 `PUBLIC_TOOLS` 与能力码字典（不再是空数组） |
| `/clients*`（含 rotate-key） | `McpProvideAdminController` | 路径/契约不变 |
| `/upstreams*`、`/upstreams/{id}/tools`、`/probe` | `McpConsumeAdminController` | 路径/契约不变 |
| `GET /calls` | `McpOverviewController` | `direction` 读侧兼容旧值 |

- 权限码沿用 `PermissionConstants.AI_MCP_EDIT`（`ai:mcp:edit`，菜单按钮 `sys_menu.id=10411`），无需新权限脚本。
- 前端 `zhishu-frontend/src/views/ai/McpHub.vue` 与 `src/api/ai.ts` **零改动**即可工作；
  若要显式区分两平面，二期可把 Tab「调用日志」的方向标签改为 `SERVE/CONSUME`。

---

## 7. 护栏与测试

新增 `zhishu-mcp/src/test/java/.../mcp/`：

1. `McpPlaneIsolationTest`（ArchUnit）
   - `provide..` ✕ `consume..` 双向禁止；`..mcp.common..` 不得依赖两平面；
   - **`..mcp..` 不得依赖 `cn.datafuturex.zhishu.ai..`**（选项 B 的硬约束，防回潮）；
   - `..mcp.provide..` / `..mcp.consume..` 不得依赖 `..mcp.web..`。
2. `McpEndpointAuthFilterTest`：`wxmcp_*` 通过、有效 JWT 通过、JWT 已拉黑 401、错误 Key 401、无凭据 401、
   OPTIONS 204、`finally` 清理 `McpCallerContext`/`UserContext`。
3. `McpProvidedToolCatalogTest`：`getSystemIntroduction` 始终公开；Client 能力交集；空能力回落公开目录；
   `PLATFORM_JWT` 不走 Client 交集。
4. `McpUpstreamConnectionManagerToolCacheTest`：`persistToolCache` 保留人工 `enabled`、超 40 截断、`exposed_name` 规则。
5. `McpCallLogDirectionTest`：`SERVE`/`CONSUME` 写入列正确（A 写 `client_id`、B 只写 `user_id`）、读侧旧值归一。
6. `McpSchemaSqlConsistencyTest`：`db/mcp_schema.sql` 与 `init_ai.sql` 的 `ai_mcp_*` 列集合一致。
7. `McpExceptionAdviceTest`：`BusinessException(404,…)` 仍返回与现 `AiGlobalExceptionHandler` 一致的 `code`/HTTP 状态（防止跳到 security 的 `GlobalExceptionHandler` 后变成 400）。

新增 `zhishu-ai/src/test/java/.../AiToMcpDependencyTest`（ArchUnit）：

- `cn.datafuturex.zhishu.ai..` 仅允许依赖 `..mcp.consume.api..` 与 `..mcp.provide.spi..`，
  其余 `..mcp..` 包一律禁止（与 `GatewayDependencyGuardTest` 同构）。

同步修改既有测试：

- `zhishu-ai/AiModulithStructureTest`：`ai.mcp` 包消失后仍应通过（无需改代码），但注释里「引用 `ai.mcp.support` 会失败」的举例要换成仍然存在的例子。
- `zhishu-model-gateway/GatewayDependencyGuardTest`：禁止清单 `cn.datafuturex.zhishu.ai.mcp..` → 换成 `cn.datafuturex.zhishu.mcp..`（网关也不得依赖 MCP 模块内部）。
- `zhishu-ai/shared/crypto/CredentialCryptoTest`：随 `CredentialCrypto` 下沉到 zhishu-security 一并移动（包名与放置位置变更）。

验收命令（遵循「单模块测试需 `-am`」的既有约定）：

```bash
mvn -q -pl zhishu-security -am test     # B1 下沉后先验证基座
mvn -q -pl zhishu-mcp -am test
mvn -q -pl zhishu-ai -am test           # 确认 ai 侧无残留 MCP 实现依赖
mvn -q clean verify                     # 含 core 的 failsafe 与结构护栏
```

---

## 8. 实施步骤（按选项 B，每阶段可独立验证与合入）

| 阶段 | 内容 | 完成判据 |
| ---- | ---- | -------- |
| B0 基线 | 快照当前 `/mcp` 工具列表、`/api/v1/mcp/overview` 响应、Client 鉴权与上游 probe 结果 | 有可比对的「迁移前」基线 |
| B1 下沉 | `UserContext`/`BusinessException`/`CredentialCrypto` → `zhishu-security.common.*`；约 50 个文件改 import；`AiGlobalExceptionHandler`、`UserContextInterceptor` 同步；javadoc 里的旧路径描述修正 | 全模块 `clean verify` 绿，**行为零变化**（单独提交） |
| B2 端口反转 | `ExternalToolPort` → `mcp.consume.api.McpAgentToolPort`；新增 `mcp.provide.spi.McpToolContributor` + ai 侧 `BizToolMcpContributor`；新增 `McpTerminalOverviewQuery` + ai 侧 `McpTerminalOverviewAdapter`；`ChatClientSupport`/`AgentDefinitionServiceImpl` 改 `ObjectProvider` | ai 与 mcp 双向依赖不成立（ArchUnit 绿），会话调用链行为不变 |
| B3 搬迁 | `ai/mcp/**` → `zhishu-mcp`，按 §4 分包（provide/consume/common/web）；删 `ai/mcp/package-info`；SQL 移至 `db/mcp_schema.sql`；pom：mcp 加 security/mybatis/validation/jdbc/springdoc/两个 mcp starter，ai 删两个 starter | 全模块编译通过，`ai.mcp` 目录不存在 |
| B4 合并与行为变更 | 单 Filter 双凭据（含 JWT）、目录合并 + 守卫全覆盖、审计 `SERVE/CONSUME` + UPDATE 补丁、`zhishu.mcp.*` 新树（旧键删除）、`McpExceptionAdvice`、`ProductionConfigGuard` 调整 | 本地实测：两种凭据各能 initialize + tools/list + tools/call；审计写入方向正确；上游 probe/绑定可用 |
| B5 护栏与文档 | §7 全部测试；更新 `mcp-service-guide.md`（鉴权改双凭据、无 X-API-Key）、`mcp-hub-design.md`、`deployment/README.md` 与 `.env.example` | `mvn -q clean verify` 全绿；文档与代码一致 |
| B6 回归 | 前端 MCP Hub 三 Tab、Client 签发/轮换、上游 probe/工具启停、`/calls` 过滤、控制台 JWT 直调 `/mcp` | 手工清单逐条打勾 |

> B1 与 B2 必须分两次提交：B1 仅类型搬家，B2 仅接口方向；B3 才是文件搬迁；B4 集中心为变更。
> 这样每一步都可单独回滚，且 diff 可审。

---

## 9. 决策结论与遗留确认项

**已确认（本设计的依据）**

| # | 决策 |
| - | ---- |
| 1 | 选项 **B**：`zhishu-mcp` 不依赖 AI 内核；`zhishu-ai → zhishu-mcp`，基础类型下沉至 `zhishu-security.common` |
| 2 | `/mcp` 只接受两种凭据：`Bearer wxmcp_*`（外部 Client）**或** `Bearer <平台登录 JWT>`；静态 `X-API-Key` 完全移除 |
| 3 | 审计 `direction` 改为 `SERVE`/`CONSUME`，并**附带人工 UPDATE 补丁**归一历史数据 |
| 4 | `wanxiang.mcp.*` 与 `zhishu.mcp.server.*` **一次性删除**，不留双读 |

**遗留项已定稿（2026-10-26）**

| # | 结论 |
| - | ---- |
| 1 | **不加** `X-API-Key` 灰度窗口：静态密钥一次性移除，不保留 `legacy-static-key` 配置项（已确认无仅带 `X-API-Key` 的调用方） |
| 2 | JWT 凭据本期 = 公开目录 `PUBLIC_TOOLS`，不按权限码 / Agent 能力进一步收敛 |
| 3 | `?token=` 查询串**仅对 `wxmcp_*` 保留**；平台登录 JWT 强制走 `Authorization` 头，否则 401 |

---

## 10. 变更影响清单（速查）

| 类型 | 内容 |
| ---- | ---- |
| 删除 | `zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/mcp/**`（41 文件）、`ai/shared/mcp/ExternalToolPort.java`（反转为 mcp 侧端口）、`ai/shared/UserContext.java`、`ai/shared/exception/BusinessException.java`、`ai/shared/crypto/CredentialCrypto.java`（下沉）、`zhishu-mcp/.../security/McpAuthFilter.java`、`zhishu-mcp/.../config/McpServerAutoConfiguration.java`、`zhishu-ai/src/main/resources/archive/migration_ai_mcp.sql` |
| 新增 | `zhishu-security/.../common/{context/UserContext, exception/BusinessException, crypto/CredentialCrypto}.java`；`zhishu-mcp/src/main/resources/db/{mcp_schema.sql, patch_mcp_call_log_direction.sql}`；`provide/**`、`consume/**`（含 `api`/`spi` 子包）、`web/**`（含 `McpExceptionAdvice`）；`ai/biztools/mcp/BizToolMcpContributor.java`、`ai/biztools/mcp/McpTerminalOverviewAdapter.java`；8 个测试类 |
| 移动 | 全部 `ai.mcp.*` 类 → `cn.datafuturex.zhishu.mcp.*`（含 5 实体 / 5 Mapper / 4 DTO / 6 VO） |
| 修改（pom） | `zhishu-mcp/pom.xml`（+security、+mybatis-plus、+validation、+jdbc、+springdoc-api、+两个 mcp starter；**不加 zhishu-ai**）、`zhishu-ai/pom.xml`（+zhishu-mcp、−两个 MCP starter）、根 `pom.xml` 模块描述 |
| 修改（ai 侧） | `ChatClientSupport`、`AgentDefinitionServiceImpl` 改依赖 `McpAgentToolPort`（`ObjectProvider`）；`AiGlobalExceptionHandler`、`UserContextInterceptor` 及 22/23/9 个引用文件改 import；`ai.biztools.stub` 兜底 Bean 由 `@Component` 改为显式 `@Bean @ConditionalOnMissingBean` |
| 修改（core） | `application.yml` 的 MCP 配置树与 `wanxiang.mcp` 移除、`ProductionConfigGuard` 去掉 `zhishu.mcp.server.auth-key` 校验、`db/init_ai.sql` 列注释 |
| 修改（部署/文档） | `.env.example` 删 `ZHISHU_MCP_AUTH_KEY`、`deployment/README.md` 必填项表、`mcp-service-guide.md` 鉴权章节、`mcp-hub-design.md` 指向新结构 |
| 不变 | `/mcp` 端点与 `wxmcp_*` 契约、`/api/v1/mcp/**` 路径与响应形状、`ai:mcp:edit` 权限、5 张表结构、前端页面（可选改日志方向标签）、Docker 部署产物 |

---

## 11. 实施状态（2026-10-27 已完成）

B0–B5 已全部落地，`mvn test` 全绿（security 41 / mcp 32 / biz 3 / ai 1 / gateway 18 / core 11）。

### 11.1 与各阶段设计的差异（以代码为准）

| 设计 | 实现 | 原因 |
| ---- | ---- | ---- |
| §5.3 两个平面各一个审计写入 Service | 保留**单一** `common.audit.McpCallLogService`，提供 `recordServe` / `recordConsume` 两个入口 | 两平面不交叉靠「各自只调自己那一侧的方法 + 归属列独立」保证；拆两个类会复制同样的 insert/截断逻辑 |
| §7 隔离测试放在 `zhishu-mcp` + `zhishu-ai` 两侧 | 全部护栏集中在 `zhishu-core/architecture/McpArchitectureGuardTest` | ArchUnit 不在 `zhishu-mcp` 的 test classpath；而 core 依赖所有模块，能一次看到 mcp 与 ai 两侧字节码，与已有 `DatabaseScriptSessionStateTest` 同位 |
| 入口拆分：`McpProvideAdminController` / `McpConsumeAdminController` / `McpOverviewController` | 已拆分（路径不变） | 前端零改动 |
| 兜底 Bean 改 `@Bean @ConditionalOnMissingBean` | 直接删除 `EmptyTerminalOverviewPort`，改由 `McpTerminalOverviewAdapter`（`ObjectProvider<McpTerminalOverviewQuery>`）在无查询 Bean 时返回提示文案 | 消除跳 jar 的 `@ConditionalOnMissingBean` 扫描顺序不确定问题，同时只保留一个实现供注入 |
| `McpExternalToolAdapter` 改名 | `McpAgentToolAdapter`；`InboundAuditingToolCallback` → `UpstreamAuditingToolCallback` | 旧的 Inbound/Outbound 术语与 `direction` 语义一起清除 |
| `McpSchemaInitializer` 调 consume 预热 | 拆为 `common.schema.McpSchemaInitializer`（只建表，`@Order(30)`）+ `consume.config.McpConsumeWarmupRunner`（预热，`@Order(40)`） | 避免共享内层 `common` 反向依赖 `consume`，不破坏隔离规则 3 |

### 11.2 实施细节补充

- 目录新增「贡献 Tool 准入集」`REGISTERED_TOOL_NAMES`（能力字典全部工具名 + 公开目录），
  他方经 `McpToolContributor` 贡献但未登记的 Tool 不会泄露到 `/mcp`。
- `McpClientAdminService.DEFAULT_RPM_FALLBACK = 60`：`rpm_limit` 缺失时的兼容值，
  与 `McpRateLimiter` 的 `rpmLimit <= 0` 回落保持一致。
- 新增 `consume.api.McpOnlineOverview`（`found/message/total/onlineCount/offlineCount/summary/markdown/items`）；
  `StationCompareResult` 的 `element` 由 ai 侧适配器按「有明细 → `online`，无明细 → `null`」推导，保持旧行为。
- `zhishu-mcp/src/main/resources/db/mcp_schema.sql` 的对象名已全部补 `public.` 前缀（进入 `classpath*:db/*.sql` 护栏覆盖范围）。
- 待人工执行：`db/patch_mcp_call_log_direction.sql`（将历史 `OUT/IN` 归一为 `SERVE/CONSUME`）。
- 待真实环境验收（B6）：`/mcp` 两种凭据各跑 initialize + tools/list + tools/call，并核对 `ai_mcp_call_log` 方向与归属列。
