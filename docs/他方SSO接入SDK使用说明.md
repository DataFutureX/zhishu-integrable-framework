# 知枢可集成智能体框架 · 他方 SSO 接入 SDK 使用说明

**SDK 坐标：** `cn.datafuturex.yunqi:yunqi-sso-partner-sdk:1.0.0`  
**源码目录：** [`sdk/yunqi-sso-partner-sdk`](../sdk/yunqi-sso-partner-sdk)  
**协议全文：** [单点登录对接说明.md](./单点登录对接说明.md)  
**适用对象：** 万象、数智 IoT 等需「一键进入知枢」的伙伴后端

本 SDK **只在他方（伙伴）侧使用**：用伙伴私钥签发短期 Ticket，拼装知枢前端回调 URL。  
知枢验签、换票、签发业务 JWT 仍由知枢完成；**不要**把本 SDK 部署到知枢服务器，也**不要**把私钥交给知枢。

---

## 1. 能做什么 / 不能做什么

| 能力 | 说明 |
|------|------|
| 生成 RSA / SM2 密钥对 | 导出 PEM；公钥交知枢登记，私钥自持 |
| 签发 Ticket | `alg=RS256` 或 `alg=SM2`（SM3withSM2） |
| 拼装回调 URL | `{YUNQI_WEB}/sso/callback?ticket=...&redirect=...` |
| ~~换票~~ | 不调用知枢 API；由浏览器进回调页，或伙伴自行 `POST /api/v1/auth/sso/exchange` |
| ~~业务 JWT~~ | 不签发、不解析知枢业务 Token |

---

## 2. 环境要求

- JDK **17+**（与知枢后端 21 兼容；伙伴侧 17 即可）
- Maven 3.8+
- 依赖：BouncyCastle（国密 SM2；RS256 亦可仅用 JDK，但 SDK 统一引入 BC 便于双算法）

---

## 3. 安装

### 3.1 源码安装（推荐联调）

在知枢仓库根目录：

```bash
cd sdk/yunqi-sso-partner-sdk
mvn clean install
```

伙伴工程 `pom.xml`：

```xml
<dependency>
  <groupId>cn.datafuturex.yunqi</groupId>
  <artifactId>yunqi-sso-partner-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 3.2 仅拷贝模块

可将整个 `sdk/yunqi-sso-partner-sdk` 目录复制到伙伴代码库，作为子模块或独立工程引用。

---

## 4. 快速开始（5 分钟）

### 4.1 生成密钥并把公钥交给知枢

```java
import cn.datafuturex.yunqi.sso.sdk.YunqiSsoKeys;
import java.nio.file.Path;

// RSA（默认）
var rsa = YunqiSsoKeys.generateRsa(2048);
rsa.writePrivateKeyPem(Path.of("wanxiang-private.pem")); // 仅伙伴机房
rsa.writePublicKeyPem(Path.of("wanxiang-public.pem"));   // 交给知枢

// 或国密 SM2
var sm2 = YunqiSsoKeys.generateSm2();
sm2.writePrivateKeyPem(Path.of("wanxiang-sm2-private.pem"));
sm2.writePublicKeyPem(Path.of("wanxiang-sm2-public.pem"));
```

同时书面确认：

| 项 | 示例 |
|----|------|
| `iss` | `wanxiang` |
| `kid` | `wanxiang-2026`（SM2 建议另起，如 `wanxiang-sm2-2026`） |
| `aud` | 固定 `zhishu-integrable-framework` |
| 用户名 | 伙伴登录名 = 知枢 `sys_user.username`（第一期无 JIT） |

### 4.2 已登录用户点击「进入知枢」

```java
import cn.datafuturex.yunqi.sso.sdk.*;

YunqiSsoClient client = YunqiSsoClient.builder()
        .issuer("wanxiang")                 // 与知枢配置一致
        .kid("wanxiang-2026")
        .algorithm(SsoAlgorithm.RS256)      // 或 SsoAlgorithm.SM2
        .privateKeyFile(Path.of("wanxiang-private.pem"))
        .yunqiWebBase("https://yunqi.example.com")  // 知枢前端
        .defaultRedirect("/home/dashboard")
        .defaultTtlSeconds(60)
        .build();

// 当前已登录用户
SsoTicketResult result = client.issueTicket(
        SsoTicketRequest.builder("zhangsan")
                .displayName("张三")          // 可选
                .redirect("/home/dashboard") // 可选
                .build());

// 浏览器 302 / 打开
String url = result.callbackUrl();
// 或自行拿 result.ticket() 做服务端换票
```

Spring MVC 示例：

```java
@GetMapping("/goto-yunqi")
public void gotoYunqi(HttpServletResponse response, Principal principal) throws IOException {
    SsoTicketResult result = yunqiSsoClient.issueTicket(principal.getName());
    response.sendRedirect(result.callbackUrl());
}
```

---

## 5. API 一览

### 5.1 `YunqiSsoClient`

| 方法 | 说明 |
|------|------|
| `builder()` | 配置 issuer / kid / algorithm / 私钥 / 知枢前端地址 |
| `issueTicket(username)` | 快捷签发 |
| `issueTicket(SsoTicketRequest)` | 可带显示名、redirect、TTL |

Builder 要点：

- `privateKeyPem` / `privateKeyFile` / `privateKey` 三选一（在 `build()` 时按最终 `algorithm` 解析 PEM）
- 未配置 `yunqiWebBase` 时，`callbackUrl()` 为 `null`，仍可取 `ticket()`

### 5.2 `SsoTicketRequest`

| 字段 | 默认 | 说明 |
|------|------|------|
| `username` | 必填 | 写入 claim `username`，映射知枢用户 |
| `subject` | = username | claim `sub` |
| `displayName` | 空 | claim `name`，可选 |
| `redirect` | 客户端默认 | 站内相对路径 |
| `ttlSeconds` | 60 | 1～120；知枢侧通常上限 120 |

### 5.3 `SsoTicketResult`

| 字段 | 说明 |
|------|------|
| `ticket()` | 完整 JWT |
| `callbackUrl()` | 前端回调完整 URL |
| `jti()` / `iat()` / `exp()` | 便于日志与排障 |
| `algorithm()` | RS256 或 SM2 |

### 5.4 `YunqiSsoKeys` / `SsoCallbackUrlBuilder`

- `generateRsa` / `generateSm2`：生成并导出 PEM  
- `SsoCallbackUrlBuilder.build(webBase, ticket, redirect)`：仅拼 URL  
- `sanitizeRedirect`：非法外域路径回落 `/home/dashboard`

---

## 6. Ticket 与知枢约定（SDK 已内置）

Header：

```json
{ "alg": "RS256", "typ": "JWT", "kid": "<你的 kid>" }
```

或：

```json
{ "alg": "SM2", "typ": "JWT", "kid": "<你的 sm2 kid>" }
```

Payload（SDK 自动填写）：

```json
{
  "iss": "<builder.issuer>",
  "aud": "zhishu-integrable-framework",
  "sub": "<subject>",
  "username": "<username>",
  "name": "<可选>",
  "iat": <unix秒>,
  "nbf": <同 iat>,
  "exp": <iat+ttl>,
  "jti": "<UUID>"
}
```

签名：

- RS256：`SHA256withRSA`，Base64URL  
- SM2：`SM3withSM2`（BouncyCastle），签名为 **DER**，再 Base64URL  

回调：

```text
{YUNQI_WEB}/sso/callback?ticket={urlencode(jwt)}&redirect={urlencode(path)}
```

换票（一般由知枢前端完成，伙伴无需调用）：

```http
POST {YUNQI_API}/api/v1/auth/sso/exchange
Content-Type: application/json

{ "ticket": "<jwt>", "redirect": "/home/dashboard" }
```

---

## 7. 安全清单（必读）

1. **私钥不出伙伴机房**：禁止提交 git、禁止发聊天/工单附件、禁止放到知枢机器。  
2. Ticket **TTL 建议 60 秒**，禁止做成会话级长 Token。  
3. Ticket **只能换票一次**（`jti` 防重放）；不要把同一 Ticket 缓存给多人用。  
4. `redirect` 只能是知枢站内相对路径（以 `/` 开头）。  
5. 生产必须用 **HTTPS** 传 Ticket。  
6. 用户名必须与知枢已开通账号一致；知枢第一期**不会**自动建号。  
7. 禁止使用知枢登录页 RSA 公钥或知枢 `jwt.secret` 签发 Ticket。

---

## 8. 联调检查表

| # | 检查项 | 通过标准 |
|---|--------|----------|
| 1 | 公钥已登记到知枢 | `yunqi.sso.partners.<iss>` + `kid` |
| 2 | 知枢 SSO 开关 | `yunqi.sso.enabled=true` |
| 3 | 测试账号已开通且启用 | `sys_user.username` 一致、`status=1` |
| 4 | SDK issuer / kid / alg 与知枢一致 | 换票不再报「签名无效 / 未开通来源」 |
| 5 | 浏览器打开 callbackUrl | 进入知枢业务页，地址栏无残留 ticket |
| 6 | 同一 ticket 再打开 | 返回「票据已使用」 |

本地也可用知枢仓库脚本对照：

```powershell
.\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin
.\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin -Alg SM2
```

---

## 9. 常见问题

**Q: 报「票据签名无效」？**  
A: 核对公私钥是否一对、`alg` 与公钥类型是否匹配、知枢 `kid` 是否指向正确 PEM。SM2 与 RSA 公钥不可混用。

**Q: 报「账号未开通」？**  
A: 在知枢预建与 Ticket `username`/`sub` 相同的用户。

**Q: 只想拿 ticket，不要 URL？**  
A: Builder 不设 `yunqiWebBase`，只使用 `result.ticket()`。

**Q: 非 Java 伙伴？**  
A: 按 [单点登录对接说明.md](./单点登录对接说明.md) 第 5、16 节自行签发；算法与字段必须一致。本仓库优先提供 Java SDK。

---

## 10. 版本

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-08-19 | 首版：RS256 / SM2 签发、密钥工具、回调 URL |
