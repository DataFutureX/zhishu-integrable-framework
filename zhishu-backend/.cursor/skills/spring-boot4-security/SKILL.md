---
name: spring-boot4-security
description: 知枢可集成框架（zhishu-backend）后端工程专属规范：Java 21 / Spring Boot 4.1 / Spring Security 7 + JWT / MyBatis-Plus / Spring Modulith / Spring AI / MCP。覆盖多模块边界、Security 过滤链与 RBAC、持久层与分层、Result 双实现。当在本仓库新增或修改后端功能、鉴权、JWT、MyBatis-Plus，或询问架构归属时使用。
---

# 知枢后端工程架构与开发规范

本文件按 zhishu-backend 当前源码整理；README 个别结构描述可能滞后（例如 zhishu-mcp 模块），冲突时以 `src` 实际实现为准。

## 0. 角色与输出约束

- 你是本工程资深后端工程师，只按仓库已落地技术栈与模块边界设计，不臆造新框架或目录。
- 除非用户明确要求，不输出测试代码、不包含运行指令、不写开发过程说明；若用户明确要求测试，遵循仓库现有 `@WebMvcTest` + `@MockitoBean` 与 H2 约定。
- 给出完整、可编译的文件内容；禁止 `// TODO` 或省略占位。
- 仓库保留 YunQi/Yqap/yunqi 等历史命名，不要大规模批量重命名；新增代码统一落在 `cn.datafuturex.zhishu` 下对应模块。

## 1. 技术栈与版本（pom 已核实）

| 领域 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 21、Spring Boot 4.1.0（`spring-boot-starter-webmvc`，勿用 `spring-boot-starter-web`） |
| 安全 | Spring Security 7.x（SecurityFilterChain、方法级鉴权）、jjwt 0.12.6 |
| 持久层 | MyBatis-Plus 3.5.17：`mybatis-plus-spring-boot4-starter` + `mybatis-plus-jsqlparser` |
| 模块化 | Spring Modulith 2.1.0（`@ApplicationModule`） |
| AI | Spring AI 2.0.0、Spring AI Alibaba Agent 2.0.0-M1.1、pgvector |
| 文档 | SpringDoc 3.1.0（`springdoc-openapi-starter-webmvc-api/ui`） |
| 工具 | Lombok 1.18.38、Hutool 5.8.23、BouncyCastle、MapStruct（ai 模块） |
| 存储 | PostgreSQL 14+（HikariCP、org.postgresql.Driver、可选 pgvector）、可选 Neo4j、测试 H2 |
| JSON | 主链路用 Jackson 3 `tools.jackson.*`（如注入 `JsonMapper`）；AI 某些 Agent/Tool 代码仍用 `com.fasterxml.jackson.databind.ObjectMapper`，跟随相邻文件 import，不混用同一类中不必要双写 |

## 2. 模块依赖与包地图

依赖方向保持单向，新增依赖不得制造循环：

```
zhishu-api(spi/dto)
   ↑
zhishu-security(认证/RBAC/登录安全/common)
   ↑
zhishu-biz、zhishu-ai、zhishu-mcp
   ↑
zhishu-core（唯一启动壳）
```

- `zhishu-api`：`cn.datafuturex.zhishu.api`，只放跨模块 SPI（如 `AuthAuditApi`、`LoginSecuritySettingsApi`）与纯 DTO（record）。
- `zhishu-security`：`captcha`、`common`（Result/PageResult/GlobalExceptionHandler/SecurityUtils）、`config/security`、`modules`（controller/dto/entity/mapper/service/vo/constant）、`security`（登录加密、Token 黑名单）、`security.sso`。
- `zhishu-biz`：公告 `announcement`、操作日志 `operationlog`、系统配置 `systemconfig`、系统监控 `systemmonitor`；每个业务域目录含 `package-info.java` + `@ApplicationModule`，并按 controller/dto/entity/mapper/service(+impl)/vo 分层；SPI 实现在域的 `spi` 子包。
- `zhishu-ai`：`agent`、`briefing`、`chat`、`knowledge`、`kg`、`mcp`（Hub）、`modelconfig`、`openapi`、`biztools`、`shared`、`platform`、`config`；与 core 同进程启动，不单独占端口。
- `zhishu-mcp`：`config`/`security`/`tool`，独立 Spring AI MCP Server 模块，对外暴露 `/mcp`；不要与 zhishu-ai 旧 MCP Server 装配重复注册同一端点（改动前先确认启用链路）。
- `zhishu-core`：`YqapApplication` 与基础设施装配（数据源、MyBatis-Plus、OpenAPI、异步、调度、静态资源、`common/logging`）。
- `zhishu-test-support`：仅测试依赖，不进入运行时类路径。

## 3. 启动与装配事实（zhishu-core）

- 主类：`cn.datafuturex.zhishu.YqapApplication`；`@SpringBootApplication(scanBasePackages = cn.datafuturex.zhishu)`、`@MapperScan(basePackages = cn.datafuturex.zhishu, annotationClass = Mapper.class)`、`@EnableCaching`。
- 开发端口 8180（`application-dev.yml`）；springdoc UI 位于 `/swagger-ui.html`；console/源码统一 UTF-8（`CONSOLE_LOG_CHARSET=UTF-8`）。
- 主数据源：`yunqi.datasource.*` 条件装配 HikariCP；`@ConditionalOnProperty` 保证单测走 `spring.datasource`（H2）。
- 写库方法使用 `@Transactional(rollbackFor = Exception.class)`。

## 4. Spring Security 7 安全链路（与代码保持一致）

- `SecurityConfig`：`csrf.disable()`、`SessionCreationPolicy.STATELESS`、`@EnableMethodSecurity`、自定义 `RestAuthenticationEntryPoint`（401）与 `RestAccessDeniedHandler`（403）。
- 过滤顺序：`CommittedResponseSecurityExceptionFilter` 在 `ExceptionTranslationFilter` 前；`JwtAuthenticationFilter` 在 `UsernamePasswordAuthenticationFilter` 前；`SwaggerFrameOptionsFilter` 在 `HeaderWriterFilter` 前；两个挂在链上的自定义 Filter 同时用 `FilterRegistrationBean` 关闭 Servlet 级重复注册。
- 匿名放行：`/api/v1/auth/**`、`GET /api/v1/system-config`、`/api/v1/system/health`、`/error`、`/open/v1/**`、`/mcp`、`/mcp/**`、`/uploads/**`；Swagger 路径仅在 `yunqi.security.api-docs-permit-all=true`（dev）放行。
- `JwtAuthenticationFilter`：从 `Authorization: Bearer` 取 token（`token` query 仅用于 SSE/WS 类场景）；`/open/`、`/mcp` 路径直接跳过（由各自专用 Filter/Controller 处理）；校验签名/过期、黑名单；用 `PermissionService.listPermissionCodesByUsername` 实时加载权限（返回 null 表示用户不存在/禁用，不建立认证上下文）。
- 401/403 统一用 `Result.error(401/403, ...)` 经 Jackson 3 `JsonMapper` 输出 JSON；已提交响应直接返回。
- 方法级鉴权统一 `@PreAuthorize(hasAuthority(' + PermissionConstants.XXX + '))`，权限码集中在 `modules/constant/PermissionConstants`，禁止散落字符串常量。
- 登录安全已实现：RSA 登录加密（`/api/v1/auth/public-key` + keyId）、滑动验证码、失败锁定 `LoginAttemptService`、审计 `AuthAuditApi`；登出/改密/禁用会黑名单或吊销 Token；SSO Ticket 换票在 `security.sso`（RS256/SM2、jti 去重、限流）。新增认证流程不得绕过这些既有防线。

## 5. MyBatis-Plus 持久层规范（与代码保持一致）

- Mapper 接口继承 `BaseMapper<T>` 并标注 `@Mapper`。
- Entity 用 `@Data` + `@TableName`；主键多用 `@TableId(type = IdType.ASSIGN_ID)`（部分菜单等固定主键用 `INPUT`）；字段驼峰，时间用 `java.time.LocalDateTime`。
- 全局配置：`map-underscore-to-camel-case=true`、日志 `Slf4jImpl`、逻辑删除字段 `deleted`（1/0）；`mapper-locations: classpath*:/mapper/**/*.xml`。
- 分页拦截器：`MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`，`maxLimit=500`、`overflow=false`；不要重复注册拦截器。
- 查询优先 `LambdaQueryWrapper`；分页用 `Page<T>`，Controller 返回统一由 `PageResult.of(IPage)` 转换。

## 6. Web API 与 Result 约定

- 路径：控制台 REST 在 `/api/v1/...`（小写连字符）；开放面在 `/open/v1/**`；MCP 在 `/mcp`。
- Controller 标注 `@RestController` + `@Tag`/`@Operation`；写操作入参使用 `@Valid @RequestBody`。
- Result 存在两个实现，按模块使用、不要混用：
  - security/biz：`cn.datafuturex.zhishu.common.Result`（code/message/data，静态 `success/error`）+ `common.PageResult.of(IPage)`。
  - zhishu-ai Controller：`cn.datafuturex.zhishu.ai.shared.Result`（含 timestamp、`success/fail`、`isSuccess`）；AI 侧流式/多线程上下文用 `shared.UserContext` 的 snapshot/restore。
- 已实现中有历史 Controller 使用 try/catch + `Result.error(...)`；但仓库同时存在 `GlobalExceptionHandler`（security/common）与 AI `AiGlobalExceptionHandler`。新增代码倾向：Service 抛 `BusinessException`/`IllegalArgumentException` 等，由全局处理器统一映射 400/500；避免在新 Controller 里整段 catch 后吞异常。
- SSE/流式端点（`Flux<ServerSentEvent>`，可参考 `ai/shared/sse/ChatSseSupport`）不包 `Result`。
- 入参 DTO 优先 record；对外优先返回 VO/DTO，不要把 Entity 直接暴露（旧接口有直接返回 Entity 的历史，不做大规模回改）。

## 7. AI / MCP / 开放平面注意点

- Agent/知识库/图谱/MCP Hub/开放 API 均在 `zhishu-ai`；MCP Server 对外模块在 `zhishu-mcp`（工具：`SystemIntroductionTool`，鉴权：`McpAuthFilter`，`zhishu.mcp.server.auth-key` 非空才启用 X-API-Key 校验，Security 链已放行 `/mcp`）。
- MCP Hub 控制面为 `/api/v1/mcp`；上游协议仅 Streamable HTTP/SSE，生产禁止 STDIO/COMMAND（仓库约束）。
- 开放 REST `/open/v1/**` 走 AK/SK 鉴权平面；不要在 `/api/v1` JWT 语义下假设它已认证。

## 8. 新增/修改功能工作流

1. 判断归属：RBAC/登录/JWT → `zhishu-security`；公告/操作日志/系统配置/监控 → `zhishu-biz` 对应域；Agent/Chat/知识库/图谱/MCP Hub/模型/开放 API/简报 → `zhishu-ai`；对外 MCP Tool → `zhishu-mcp`；纯契约/公共 DTO → `zhishu-api`。
2. 依赖：先查父 `pom.xml` 与模块 `pom.xml`，新依赖版本走父 dependencyManagement（模块级属性如 Spring AI 版本放模块 properties）。
3. 建新业务域时：添加 `package-info.java` + `@ApplicationModule`；按 controller/dto/entity/mapper/service(+impl)/vo 建包；需要被其他模块消费的 SPI 接口放 `zhishu-api`，实现放业务域 `spi` 子包。
4. 权限：新增管理功能时在 `PermissionConstants` 增加常量，并在 `sys_menu`（menu_type=BUTTON）登记；Controller 用 `@PreAuthorize` 引用常量。
5. 实现：Controller 使用对应模块的 Result；Service 接口/实现分离；写操作 `@Transactional(rollbackFor = Exception.class)`；MyBatis-Plus 分页按第 5 节。
6. 收尾：默认不补测试、不输出运行指令；只在用户明确要求测试时，按仓库测试栈补 `@WebMvcTest`/`@MockitoBean`（H2/MockMvc）或真实 HTTP `*ApiIT`。

## 9. 通用禁令（与仓库 rules 一致）

- 禁止 `@Autowired` 字段注入，统一 `@RequiredArgsConstructor` + `private final`。
- 禁止 `System.out.println`，统一 `@Slf4j`。
- 禁止 `java.util.Date`/`Calendar`，统一 `java.time`。
- 禁止 `// TODO`、省略号、不可编译的伪代码。
- Controller 不直接返回裸实体/基础类型作为新接口的响应契约。
