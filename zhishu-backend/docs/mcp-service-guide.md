# MCP 服务接入指南

## 服务信息

| 项目 | 值 |
|------|-----|
| 服务名称 | `zhishu-integrable-framework` |
| 版本 | `1.0.0` |
| 协议 | MCP Streamable HTTP |
| 端点地址 | `http://{host}:{port}/mcp` |
| 鉴权方式 | `Authorization: Bearer <凭据>`（`wxmcp_*` API Key 或平台登录 JWT，二选一） |

## 鉴权说明

| 凭据 | 适用调用方 | 获取方式 | 可调用集合 |
| ---- | ---------- | -------- | ---------- |
| `wxmcp_*` API Key | 外部系统 / 外部 Agent | 管理员在「智能中心 → MCP Hub → 对外 Client」签发，**仅创建 / 轮换时明文返回一次** | 该 Client 能力码对应的 Tool + 公开目录 |
| 平台登录 JWT | 已登录的知枢用户 | 控制台登录 / SSO 换票得到的 `token` | 仅公开目录（当前为 `getSystemIntroduction`） |

```text
Authorization: Bearer wxmcp_xxxxxxxxxxxxxxxxxxxx…
```

要点：

- **静态 `X-API-Key` 已废弃**：旧版本需同时带 `X-API-Key` 与 `Bearer wxmcp_*`，现在只过一个过滤器，二选一即可。
- `wxmcp_*` 允许用 `?token=` 传递（兼容无法自定义头的客户端）；**JWT 不支持 `?token=`**，必须走 `Authorization` 头，避免 token 落进访问日志。
- 按 RPM 限流：Client Key 用该 Client 的 `rpm_limit`，JWT 用 `zhishu.mcp.provide.platform-jwt.default-rpm`。
- 每次工具调用写入 `ai_mcp_call_log`，`direction=SERVE`（对外提供平面被调用），控制面可查。
- 无凭据直接 `401 {"error":"missing_credential"}`；MCP 不再提供「不鉴权」模式。

## 当前可用 Tool 列表

| Tool 名称 | 描述 | 入参 |
|-----------|------|------|
| `getSystemIntroduction` | 获取知枢可集成框架的系统介绍，包括系统名称、版本、核心功能与架构说明 | 无参数（空对象 `{}`） |

> 对外目录由 `zhishu-mcp` 的 provide 平面装配：内置 Tool + 他方模块经 `McpToolContributor` 贡献、
> 且已登记在能力字典内的 Tool。监测 / 巡检类业务能力目前由「接入他方 MCP（consume 平面）」提供，
> 不直接对外透出。可通过 `tools/list` 动态发现当前可见的 Tool。

## 前端接入方式

推荐使用 `@modelcontextprotocol/sdk` 官方 SDK 连接：

```typescript
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

// 1. 创建传输层（凭据二选一：wxmcp_* 或平台登录 JWT）
const transport = new StreamableHTTPClientTransport(
  new URL("http://localhost:8080/mcp"),
  {
    requestInit: {
      headers: {
        Authorization: "Bearer wxmcp_your-api-key"
      }
    }
  }
);

// 2. 创建客户端并连接
const client = new Client({
  name: "my-frontend-app",
  version: "1.0.0"
});
await client.connect(transport);

// 3. 列出可用工具
const { tools } = await client.listTools();
console.log("可用工具:", tools);

// 4. 调用工具
const result = await client.callTool({
  name: "getSystemIntroduction",
  arguments: {}   // 该工具无需入参
});
console.log("系统介绍:", result.content);
```

## MCP 协议交互流程

```
客户端                              服务端
  │                                   │
  │── POST /mcp (initialize) ────────▶│  建立连接（先过 MCP 鉴权过滤器）
  │◀── capabilities + serverInfo ─────│
  │                                   │
  │── POST /mcp (tools/list) ────────▶│  查询工具列表
  │◀── tools[] ──────────────────────│
  │                                   │
  │── POST /mcp (tools/call) ────────▶│  调用工具（白名单 + RPM + 审计）
  │◀── content[] ────────────────────│
  │                                   │
```

## 响应格式示例

调用 `getSystemIntroduction` 返回：

```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"name\":\"知枢可集成框架\",\"englishName\":\"ZhiShu Integrable Framework (ZSIF)\",\"version\":\"1.0.0\",\"description\":\"企业级数字化应用后端框架...\",\"features\":[...],\"architecture\":\"...\",\"techStack\":[...]}"
    }
  ]
}
```

## 注意事项

1. **Content-Type**：请求体为 JSON，需设置 `Content-Type: application/json`
2. **MCP 协议版本**：遵循 MCP 2025-03-26 规范（Streamable HTTP 传输）
3. **工具扩展**：新增 Tool 可通过 `tools/list` 动态发现；对外可见性由能力字典与 Client 能力码决定
4. **错误处理**：
   - 未带凭据 `401 {"error":"missing_credential"}`
   - Client Key 无效 / 已停用 `401 {"error":"invalid_mcp_key"}`
   - JWT 无效或已注销 `401 {"error":"invalid_jwt"}`
   - 对应凭据模式被配置关闭 `401 {"error":"client_key_disabled"}` / `{"error":"platform_jwt_disabled"}`
   - 对外平面关闭 `503 {"error":"mcp_disabled"}`
   - 工具未授权或超过 RPM 时不抛协议错误，而是以 Tool 结果返回 `{"error":"..."}`
5. **模块归属**：`/mcp` 由 `zhishu-mcp` 的 provide 平面提供；接入他方 MCP 属同一模块的 consume 平面，
   两者代码级互不依赖。内部实现见 [mcp-hub-design.md](./mcp-hub-design.md)，
   迁移设计见 [mcp-module-migration-design.md](./mcp-module-migration-design.md)。
