# ZhiShu Integrable Framework · AI 内核

> Agent / RAG / KG / MCP Hub / 开放 API — 知枢框架的 AI 能力中枢

## 模块定位

`zhishu-ai` 是知枢可集成框架的 AI 内核模块，以 **Spring Modulith 应用模块** 形式挂载，由 `zhishu-core` 同进程启动（不单独暴露端口）。模块整合了智能体编排、知识库检索、知识图谱、MCP 工具中枢以及面向第三方的开放 API 能力。

**技术栈**：Java 21 · Spring Boot 4.1 · Spring AI 2.0 · Spring AI Alibaba 2.0 · MyBatis-Plus 3.5 · PostgreSQL 14+ (PGVector) · Neo4j · SpringDoc OpenAPI

---

## 子模块一览

```
cn.datafuturex.zhishu.ai
├── agent/          # 智能体管理：定义、能力编排、Graph 工作流、运行时引擎
├── chat/           # 对话服务：同步/流式对话、会话记忆、问答历史
├── knowledge/      # 知识库：文档管理、解析、Embedding、Hybrid 检索、文档 QA
├── mcp/            # MCP 中枢：对外 MCP Server、接入他方 MCP、工具管理、审计
├── modelconfig/    # 模型配置：动态 LLM 模型管理（运行时热切换）
├── openapi/        # 开放 API：面向第三方系统的 Bearer Token 认证接口
├── platform/       # 平台支撑：全局异常处理、Web 配置、用户上下文
└── shared/         # 公共组件：Result 封装、DTO/VO、SSE 工具、Trace 事件
```

### 核心能力

| 能力 | 说明 |
|------|------|
| **智能体 (Agent)** | 多智能体管理、系统提示词编排、Graph 可视化工作流、试运行与执行轨迹 |
| **RAG 知识库** | 文档上传解析（PDF/Office）、PGVector 向量检索 + Hybrid 关键词补充 |
| **知识图谱 (KG)** | Neo4j 图引擎集成、拓扑检索、同步水位管理 |
| **MCP 中枢** | 对外暴露 MCP Server（`/mcp`）、接入他方 MCP 上游、工具启用/停用、调用审计、限流与熔断 |
| **动态模型** | 运行时切换 LLM 模型/参数，无需重启 |
| **开放 API** | Bearer Token 认证、Scope 权限控制、代调用户上下文 |

---

## 开放 API 详细设计

### 架构概览

```
┌─────────────────┐     Bearer Token      ┌──────────────────────┐
│  第三方系统      │ ───────────────────▶  │  OpenApiAuthFilter   │
│ (万象 BFF 等)    │     X-On-Behalf-Of    │  (OncePerRequest)    │
└─────────────────┘                        └──────────┬───────────┘
                                                      │
                                         ┌────────────▼───────────┐
                                         │  OpenApiAuthService    │
                                         │  · 凭证校验 (SHA-256)  │
                                         │  · Scope 鉴权          │
                                         │  · 代调用户解析         │
                                         │  · last_used_at 更新   │
                                         └────────────┬───────────┘
                                                      │
                              ┌────────────────────────┼────────────────────────┐
                              ▼                        ▼                        ▼
                   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────────┐
                   │ OpenAgentCtrl    │   │ OpenChatCtrl     │   │ OpenKnowledgeQaCtrl  │
                   │ GET /agents      │   │ POST /chat       │   │ POST /knowledges/    │
                   │                  │   │ POST /chat/stream│   │      qa/stream       │
                   └──────────────────┘   └──────────────────┘   └──────────────────────┘
```

### 认证机制

所有开放 API 路径均以 `/open/` 为前缀，由 `OpenApiAuthFilter` 统一拦截：

1. **凭证格式**：`Authorization: Bearer <token>`，Token 由系统生成（前缀 `wxmcp_` + 48 位 HEX）
2. **校验流程**：
   - 提取 Token 前 14 位作为 `key_prefix`，查找 `open_app_credential` 表
   - 校验凭证状态（`ENABLED`）与过期时间（`expires_at`）
   - SHA-256 全量比对 `secret_hash`
   - 关联查询 `open_app` 表确认应用状态
3. **权限控制**：基于 Scope 模型，路径与 Scope 映射关系：

| 路径前缀 | 所需 Scope | 说明 |
|----------|-----------|------|
| `/open/v1/chat/**` | `chat` | 对话能力 |
| `/open/v1/agents/**` | `chat` | 智能体列表（对话前置选择） |
| `/open/v1/knowledges/**` | `knowledges` | 知识库问答 |
| `/open/v1/kg/**` | `kg` | 知识图谱 |

- Scope 存储在 `open_app.allowed_scopes` 字段（JSON 数组），支持 `"*"` 通配全部
- 空数组 `[]` 表示不限制 Scope

4. **代调用户**：通过请求头 `X-On-Behalf-Of: <username>` 指定实际操作用户
   - 在 `sys_user` 表中查找对应用户，设置 `UserContext`
   - 若未找到，以用户名本身作为上下文（兼容外部系统用户）

### API 端点

#### 1. 智能体列表

```
GET /open/v1/agents?status={status}
```

**Scope**：`chat`

返回可用智能体列表，外部系统可先选择 Agent 再调用对话接口。

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "code": "monitor_default",
      "name": "默认智能体",
      "description": "...",
      "model": "qwen-plus",
      "capabilities": ["RAG", "MEMORY"],
      "status": "ENABLED",
      "builtin": true,
      "defaultAgent": true
    }
  ],
  "timestamp": 1724400000000
}
```

#### 2. 同步对话

```
POST /open/v1/chat
Content-Type: application/json
```

**Scope**：`chat`

同步调用对话接口，适用于后台任务、批量处理等场景。

**请求体** (`ChatRequestDTO`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | String | ✅ | 用户消息 |
| `agentId` | Long | - | 智能体 ID；为空使用默认 |
| `conversationId` | String | - | 多轮会话 ID；为空新建 |
| `enableRag` | Boolean | - | 是否启用知识库增强 |
| `maxTokens` | Integer | - | 最大 Token 数量 |
| `temperature` | Double | - | 温度参数 |
| `enableMemory` | Boolean | - | 是否写入会话记忆 |

**响应体** (`ChatResponseVO`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | String | AI 回复文本 |
| `model` | String | 使用模型名 |
| `conversationId` | String | 会话 ID（后续回传） |
| `agentId` | Long | 智能体 ID |
| `structured` | Object | 结构化结果（可选） |
| `traces` | List | 执行轨迹事件 |
| `timestamp` | LocalDateTime | 响应时间 |

#### 3. 流式对话

```
POST /open/v1/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

**Scope**：`chat`

SSE 流式返回对话结果，事件类型：

| 事件 | 数据 | 说明 |
|------|------|------|
| `message` | 文本片段 | AI 回复的增量内容 |
| `done` | conversationId | 流结束，返回会话 ID |

#### 4. 知识问答（流式）

```
POST /open/v1/knowledges/qa/stream
Content-Type: application/json
Accept: text/event-stream
```

**Scope**：`knowledges`

基于知识库的文档智能问答（RAG），SSE 流式返回。

**请求体** (`DocumentQueryDTO`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | String | ✅ | 用户问题 |
| `documentId` | Long | - | 指定文档 ID |
| `categoryId` | Long | - | 知识库分类 ID |
| `topK` | Integer | - | 返回片段数（1-10） |
| `conversationId` | String | - | 多轮会话 ID |

### 统一响应格式

所有接口返回标准 `Result<T>` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1724400000000
}
```

错误响应：

```json
{
  "code": 401,
  "message": "开放应用凭证无效",
  "data": null,
  "timestamp": 1724400000000
}
```

---

## 数据模型

### open_app — 开放应用

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGSERIAL PK | 主键 |
| `code` | VARCHAR(64) UNIQUE | 应用编码（如 `wanxiang-monitor`） |
| `name` | VARCHAR(128) | 应用名称 |
| `status` | VARCHAR(16) | 状态：`ENABLED` / `DISABLED` |
| `allowed_scopes` | TEXT | 权限范围 JSON 数组，如 `["chat","knowledges","kg"]` |
| `remark` | VARCHAR(500) | 备注 |
| `created_by` | VARCHAR(64) | 创建人 |
| `create_time` / `update_time` | TIMESTAMP | 时间戳 |

### open_app_credential — 应用凭证

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGSERIAL PK | 主键 |
| `app_id` | BIGINT FK | 关联 `open_app.id` |
| `key_prefix` | VARCHAR(32) UNIQUE | Token 前 14 位（索引查找） |
| `secret_hash` | VARCHAR(128) | Token 全量 SHA-256 哈希 |
| `status` | VARCHAR(16) | 状态：`ENABLED` / `DISABLED` |
| `expires_at` | TIMESTAMP | 过期时间（NULL 表示永不过期） |
| `last_used_at` | TIMESTAMP | 最后使用时间（自动更新） |
| `created_by` | VARCHAR(64) | 创建人 |
| `create_time` / `update_time` | TIMESTAMP | 时间戳 |

---

## 调用示例

### cURL

```bash
# 1. 获取智能体列表
curl -H "Authorization: Bearer wxmcp_xxxxxxxx" \
     -H "X-On-Behalf-Of: admin" \
     http://localhost:8180/open/v1/agents

# 2. 同步对话
curl -X POST http://localhost:8180/open/v1/chat \
     -H "Authorization: Bearer wxmcp_xxxxxxxx" \
     -H "X-On-Behalf-Of: admin" \
     -H "Content-Type: application/json" \
     -d '{
       "message": "今日监测数据概览",
       "agentId": 1,
       "enableRag": true
     }'

# 3. 流式对话
curl -X POST http://localhost:8180/open/v1/chat/stream \
     -H "Authorization: Bearer wxmcp_xxxxxxxx" \
     -H "X-On-Behalf-Of: admin" \
     -H "Content-Type: application/json" \
     -H "Accept: text/event-stream" \
     -d '{"message": "分析近期水位趋势"}'

# 4. 知识库问答
curl -X POST http://localhost:8180/open/v1/knowledges/qa/stream \
     -H "Authorization: Bearer wxmcp_xxxxxxxx" \
     -H "X-On-Behalf-Of: admin" \
     -H "Content-Type: application/json" \
     -H "Accept: text/event-stream" \
     -d '{
       "question": "水位监测标准是什么？",
       "categoryId": 1,
       "topK": 5
     }'
```

---

## 内部 API 概览

除开放 API 外，模块还提供面向知枢管理控制台的内部 API（需 JWT 登录）：

| 路径前缀 | 说明 |
|----------|------|
| `/api/v1/agents/**` | 智能体 CRUD、Graph 编排、试运行 |
| `/api/v1/chat/**` | 控制台对话（同步/流式） |
| `/api/v1/knowledges/**` | 知识库分类、文档管理、Embedding |
| `/api/v1/mcp/**` | MCP 中枢管理（Client/Upstream/审计） |
| `/api/v1/model-config/**` | 模型配置管理 |
| `/mcp` | MCP Server 端点（SSE/Streamable HTTP） |

---

## 配置参考

```yaml
# application.yml 关键配置
wanxiang:
  mcp:
    enabled: true              # MCP 总开关
    server-enabled: true       # MCP Server 端点
    client-enabled: true       # MCP Client 连接
    crypto-key: ""             # AES 加密密钥（上游凭证加密存储）
    default-rpm: 60            # 默认每分钟请求限制
    max-upstreams-per-agent: 5 # 单 Agent 最大上游数
    max-tools-per-upstream: 40 # 单上游最大工具数

spring:
  ai:
    openai:
      api-key: ${AI_API_KEY}
      base-url: ${AI_BASE_URL}
      chat:
        options:
          model: ${AI_CHAT_MODEL:qwen-plus}
```

---

## 数据库初始化

```bash
# 新库初始化（含 open_app 表）
psql -U postgres -d zhishu -f backend/zhishu-ai/src/main/resources/init_postgresql.sql
```

启动时 `OpenAppSchemaInitializer` 会幂等执行 `migration_open_app.sql`，确保 `open_app` / `open_app_credential` 表及种子数据就绪。

---

## 模块依赖

```
zhishu-ai
├── zhishu-api        # SPI 端口定义
├── zhishu-security   # 安全基础设施（JWT / Spring Security）
└── Spring Modulith   # 模块化运行时
```
