# ZhiShu Integrable Framework · Desktop API

> 桌面客户端专属接口层 — 知枢框架面向桌面端软件的 API 能力通道

## 模块定位

`zhishu-desktop` 是知枢可集成框架的桌面客户端 API 模块，以 **Spring Modulith 应用模块** 形式挂载，由 `zhishu-core` 同进程启动（不单独暴露端口）。模块为桌面端软件提供专属的接口通道，与控制台（`/api/v1`）、开放 API（`/open/v1`）、MCP（`/mcp`）三条已有入口平行，互不干扰。

**技术栈**：Java 21 · Spring Boot 4.1 · SpringDoc OpenAPI · Lombok

---

## 接口路径规范

所有桌面端接口统一以 `/desktop/v1` 为前缀：

| 方法 | 路径 | 鉴权方式 | 说明 |
|------|------|------|------|
| GET | `/desktop/v1/health` | 无 | 健康检查，桌面端心跳探测 |
| GET | `/desktop/v1/auth/public-key` | 无 | 获取 RSA 登录公钥 |
| POST | `/desktop/v1/auth/login` | 无 | 提交登录凭证，返回 JWT Token |
| POST | `/desktop/v1/auth/logout` | `Authorization: Bearer` | 退出登录，吊销当前 JWT |
| GET | `/desktop/v1/info` | `X-Desktop-Key` | 服务能力清单与版本信息 |

> 后续新功能接口（如设备注册、消息推送、离线同步等）继续在 `/desktop/v1` 命名空间下扩展。

---

## 登录认证流程

与控制台 `/api/v1/auth` 的唯一区别：**不要求滑动验证码**。

```
桌面客户端                                        zhishu-desktop
    │                                               │
    │  1. GET /desktop/v1/auth/public-key           │
    │ ───────────────────────────────────────────▶ │
    │  ◀── { keyId, publicKey(PEM), algorithm }    │
    │                                               │
    │  2. RSA 加密 username / password              │
    │     POST /desktop/v1/auth/login               │
    │     { username(密文Base64),                    │
    │       password(密文Base64), keyId }            │
    │ ───────────────────────────────────────────▶ │
    │  ◀── { token, expiration, userId, ... }       │
    │                                               │
    │  3. 携带 Authorization: Bearer <token>         │
    │     POST /desktop/v1/auth/logout              │
    │ ───────────────────────────────────────────▶ │
    │  ◀── { code: 200 }                            │
```

---

## 鉴权机制

`DesktopAuthFilter` 采用与 MCP 模块一致的轻量 API Key 方案：

```
桌面客户端                          zhishu-desktop
    │                                    │
    │  GET /desktop/v1/info              │
    │  X-Desktop-Key: <your-secret>      │
    │ ─────────────────────────────────▶ │
    │                                    │
    │                    DesktopAuthFilter 校验
    │                    authKey 非空？──── 比对 Header
    │                                    │
    │  200 OK  /  401 Unauthorized       │
    │ ◀───────────────────────────────── │
```

- `auth-key` 为空 → 直接放行（**开发模式**）
- `auth-key` 非空 → 请求头 `X-Desktop-Key` 必须携带正确密钥，否则返回 `401`
- **豁免路径**（不检查 `X-Desktop-Key`）：
  - `/desktop/v1/health` — 健康检查
  - `/desktop/v1/auth/**` — 登录相关全部接口（公钥/登录/退出）

---

### 代码结构

```
cn.datafuturex.zhishu.desktop
├── package-info.java                        # 模块说明
├── config/
│   ├── DesktopApiProperties.java            # 配置属性绑定（zhishu.desktop.*）
│   └── DesktopApiAutoConfiguration.java     # 自动装配，注册鉴权过滤器
├── security/
│   └── DesktopAuthFilter.java               # X-Desktop-Key 鉴权过滤器
├── controller/
│   ├── DesktopInfoController.java           # /desktop/v1 基础信息接口
│   └── DesktopAuthController.java           # /desktop/v1/auth 登录认证接口
└── dto/
    ├── DesktopResult.java                   # 通用响应封装（record）
    ├── DesktopInfoDTO.java                  # 服务信息响应体（record）
    ├── DesktopLoginRequestDTO.java          # 登录请求（无 captchaToken）
    └── DesktopLoginResultDTO.java           # 登录成功响应（含用户信息）
```

---

## 配置参考

`application.yml` 中通过 `zhishu.desktop.*` 控制模块行为：

```yaml
zhishu:
  desktop:
    enabled: ${ZHISHU_DESKTOP_ENABLED:true}   # 模块总开关，false 则完全禁用
    auth-key: ${ZHISHU_DESKTOP_AUTH_KEY:}     # 鉴权密钥；生产环境必须配置，空 = 开发模式
    auth-header: X-Desktop-Key                # 鉴权请求头名称（可自定义）
    api-version: 1.0.0                        # 桌面 API 版本标识
```

环境变量覆盖示例（`.env` 或启动参数）：

```bash
ZHISHU_DESKTOP_ENABLED=true
ZHISHU_DESKTOP_AUTH_KEY=your-production-secret-key
```

---

## 调用示例

### 登录流程完整示例

```bash
# Step 1: 获取 RSA 公钥
curl http://localhost:8180/desktop/v1/auth/public-key
# 响应: { "code": 200, "data": { "keyId": "abc123", "publicKey": "-----BEGIN PUBLIC KEY...", "algorithm": "RSA", "expireSeconds": 300 } }

# Step 2: 用公钥加密用户名和密码（伪代码，实际在桌面端本地用 PublicKey 运行 RSA 加密）
# 密文 Base64 形式传入登录接口
curl -X POST http://localhost:8180/desktop/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "username": "<RSA加密后的 Base64>",
       "password": "<RSA加密后的 Base64>",
       "keyId": "abc123"
     }'
# 成功响应: { "code": 200, "data": { "token": "eyJ...", "expiration": 1761033600000, "userId": 1, "username": "admin", "realName": "系统管理员", "role": "ROLE_ADMIN" } }
# 失败响应: { "code": 401, "message": "用户名或密码错误", "data": null }

# Step 3: 退出登录
curl -X POST http://localhost:8180/desktop/v1/auth/logout \
     -H "Authorization: Bearer eyJ..."
# 响应: { "code": 200, "message": "success", "data": null }
```

### 其他接口

### cURL

```bash
# 健康检查（无需鉴权）
curl http://localhost:8180/desktop/v1/health

# 获取服务信息（需鉴权，开发模式可不带 Header）
curl -H "X-Desktop-Key: your-production-secret-key" \
     http://localhost:8180/desktop/v1/info
```

### 响应示例

**GET /desktop/v1/health**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "timestamp": "2026-10-21T10:30:00"
  }
}
```

**GET /desktop/v1/info**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "name": "知枢桌面 API",
    "apiVersion": "1.0.0",
    "features": [
      "健康检查 (GET /desktop/v1/health)",
      "服务信息 (GET /desktop/v1/info)"
    ],
    "endpoints": {
      "/desktop/v1/health": "服务健康检查（无需鉴权）",
      "/desktop/v1/info": "桌面端服务能力清单"
    }
  }
}
```

**401 鉴权失败**
```json
{
  "code": 401,
  "message": "Unauthorized: invalid or missing Desktop Key"
}
```

---

## Spring Security 集成

模块在 `SecurityConfig` 中已对 `/desktop/v1/**` 配置 `permitAll`：

```java
.requestMatchers("/desktop/v1/**").permitAll()
```

> 与 `/open/v1/**` 和 `/mcp/**` 策略一致：Spring Security 层放行，真实鉴权由 `DesktopAuthFilter` 独立完成，两条链路解耦。

---

## 如何扩展新接口

1. 在 `controller/` 包下新建 `XxxController.java`，`@RequestMapping("/desktop/v1/xxx")`，继承 `DesktopResult` 封装响应
2. 若接口需要鉴权，无需额外处理 — `DesktopAuthFilter` 自动覆盖 `/desktop/v1/**`
3. 若新接口需豁免鉴权（类似 `/health`），在 `DesktopAuthFilter.shouldNotFilter()` 中添加路径判断
4. 涉及业务逻辑时，通过注入 `zhishu-biz` 或 `zhishu-ai` 模块的 Service 实现，避免直接访问数据库

---

## 模块依赖

```
zhishu-desktop
├── spring-boot-starter-webmvc    # HTTP 基础设施
├── springdoc-openapi             # Swagger 注解支持
└── lombok                        # 代码简化（可选编译期依赖）
```

本模块无任何数据库依赖，保持轻量。需要访问持久层时，通过依赖 `zhishu-biz` / `zhishu-security` 等已有模块的 Service 实现，或由 `zhishu-core` 的自动装配层注入。
