# MCP 服务接入指南

## 服务信息

| 项目 | 值 |
|------|-----|
| 服务名称 | `zhishu-integrable-framework` |
| 版本 | `1.0.0` |
| 协议 | MCP Streamable HTTP |
| 端点地址 | `http://{host}:{port}/mcp` |
| 鉴权方式 | 请求头 `X-API-Key` |

## 鉴权说明

所有 MCP 请求需在 HTTP Header 中携带 API Key：

```
X-API-Key: {your-api-key}
```

- API Key 由管理员在「系统设置 → MCP 中枢」中配置
- 配置项：`zhishu.mcp.server.auth-key`（环境变量 `ZHISHU_MCP_AUTH_KEY`）
- 开发环境默认不鉴权（auth-key 为空时放行）

## 当前可用 Tool 列表

| Tool 名称 | 描述 | 入参 |
|-----------|------|------|
| `getSystemIntroduction` | 获取知枢可集成框架的系统介绍，包括系统名称、版本、核心功能与架构说明 | 无参数（空对象 `{}`） |

## 前端接入方式

推荐使用 `@modelcontextprotocol/sdk` 官方 SDK 连接：

```typescript
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

// 1. 创建传输层
const transport = new StreamableHTTPClientTransport(
  new URL("http://localhost:8080/mcp"),
  {
    requestInit: {
      headers: {
        "X-API-Key": "your-api-key"
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
  │── POST /mcp (initialize) ────────▶│  建立连接
  │◀── capabilities + serverInfo ─────│
  │                                   │
  │── POST /mcp (tools/list) ────────▶│  查询工具列表
  │◀── tools[] ──────────────────────│
  │                                   │
  │── POST /mcp (tools/call) ────────▶│  调用工具
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
3. **工具扩展**：后续将新增更多 Tool（监测数据查询、巡检任务、告警查询等），可通过 `tools/list` 动态发现
4. **错误处理**：鉴权失败返回 HTTP 401 `{"error":"Unauthorized","message":"Invalid or missing API Key"}`
