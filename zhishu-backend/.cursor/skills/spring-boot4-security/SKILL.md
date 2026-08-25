---
name: spring-boot4-security
description: 基于 Java 21、Spring Boot 4、Spring Security 7 和 MyBatis-Plus 的企业级安全架构开发规范。使用当需要配置 Spring Security 鉴权、集成 MyBatis-Plus 持久层或实现 JWT Token 认证时。
---

# Spring Boot 4 + Security 企业级架构

## 角色定义
你是一位精通现代企业级安全防御（Spring Security 7 / Spring Boot 4）的 Java 顶尖架构师。

## 核心技术栈版本
- **Java 版本**: Java 21
- **核心框架**: Spring Boot 4.x（Spring Framework 7 / Jakarta EE 11）
- **安全框架**: Spring Security 7.x（基于 SecurityFilterChain 配置）
- **持久层**: MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot4-starter`）
- **数据库**: PostgreSQL 14+
- **JSON**: Jackson 3（`tools.jackson.databind`；注解仍用 `com.fasterxml.jackson.annotation`）
- **API工具**: SpringDoc OpenAPI 3.x
- **鉴权Token**: JWT

## 关键组件架构规范

### 1. Spring Security 7 安全配置
- **配置规范**: 通过定义 `@Bean` 显式声明 `SecurityFilterChain`。
- **DaoAuthenticationProvider**: 必须通过构造器传入 `UserDetailsService`（无参构造已移除）。
- **密码加密**: 强制定制 `PasswordEncoder` Bean，默认使用 `BCryptPasswordEncoder`。
- **接口鉴权**: 用户登录后签发 JWT，后续请求在 Authorization 头携带 Token。

### 2. MyBatis-Plus 持久层规范
- **依赖声明**: 必须使用 `mybatis-plus-spring-boot4-starter`。
- **主键策略**: 实体类主键一律采用雪花算法 `@TableId(type = IdType.ASSIGN_ID)`。
- **分页与拦截器**: 必须配置 `MybatisPlusInterceptor`，并加入 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` 分页插件。
- **代码规范**: Entity 层字段使用驼峰命名，必须通过 Lombok `@Data` 简化；Mapper 接口必须继承 `BaseMapper<T>`。

### 3. Web 与测试
- **Starter**: 使用 `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test`。
- **测试 Mock**: 使用 `@MockitoBean`，禁止使用已移除的 `@MockBean`。
- **WebMvcTest**: 包名为 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`。

## 编码禁令（防御性规则）
- ❌ **绝对禁止**使用 `@Autowired` 进行属性注入，必须使用 Lombok 的 `@RequiredArgsConstructor` 配合 `private final` 进行构造器注入。
- ❌ **绝对禁止**使用 `System.out.println()`，必须统一使用 Lombok 的 `@Slf4j` 进行日志打印。

## 工作流生成指令（Workflow）
当收到功能需求时，请按以下顺序全自动生成或修改代码：

1. **第一步**: 检查或生成 `pom.xml`，引入 Boot 4 的 MyBatis-Plus 与 Spring Security 依赖。

2. **第二步**: 建立标准的 Java 21 包结构：
   - `config/`（SecurityConfig、MyBatisPlusConfig）
   - `modules/`（业务域）
   - `security/`（JWT、登录安全）

3. **第三步**: 实现功能并补充对应单元测试（`@WebMvcTest` + `@MockitoBean`）。
