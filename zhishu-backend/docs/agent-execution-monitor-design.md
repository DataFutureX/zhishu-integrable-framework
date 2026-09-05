# Agent 执行工作监控 —— 设计方案

## 1. 概述

### 1.1 目标

为知枢平台提供 Agent 执行工作的完整监控能力，包括：

- **执行历史**：分页查询所有 Agent 执行记录，支持按智能体、状态、类型、关键词筛选
- **执行详情**：查看单次执行的完整轨迹时间线（节点流转、工具调用、耗时分布）
- **LLM 性能指标**：采集 TTFT（首 Token 时间）和 TPOT（单 Token 生成耗时），衡量模型响应速度
- **统计概览**：总执行数、成功率、平均耗时、运行中数量等关键指标
- **按智能体聚合**：各智能体的执行次数、成功率、平均耗时对比

### 1.2 现有基础

| 组件 | 现状 |
|------|------|
| `ai_agent_run` 表 | 已有 agent_id / conversation_id / status / current_node / state_json / create_time / update_time |
| `AgentRunService` | 已有 start() / complete() / recent() 方法 |
| `AgentTraceEvent` | 已捕获 NODE_START / NODE_END / TOOL_CALL / TOOL_RESULT / ROUTE 事件 |
| `TracingToolCallback` | 已包装工具调用，记录调用入参和返回结果 |
| `AgentRuntimeServiceImpl` | 已在执行前后调用 start/complete |

**不足**：表字段缺少用户输入、响应摘要、执行耗时、失败原因、模型名、工作流类型、触发人等监控必需字段；缺少 LLM 性能指标（TTFT / TPOT）；无分页查询 API；无统计聚合接口；无前端监控页面。

### 1.3 设计原则：监控不影响执行

监控功能是“旁路采集”，绝不能影响 Agent 的正常执行流程和响应速度。

| 原则 | 保障措施 |
|------|----------|
| **采集零阻塞** | `doOnNext` 拦截器仅做内存赋值（AtomicLong/AtomicInteger），不做 I/O、不加锁、不阻塞线程 |
| **异常全隔离** | 所有监控采集代码包裹在 `try-catch` 中，异常仅记录日志，不向上抛出 |
| **ThreadLocal 必清理** | `drainTokenTimings()` 读取后立即 `remove()`，防止内存泄漏；同时在 Agent 执行结束时 `finally` 块兜底清理 |
| **写入尽力而为** | 监控字段的 DB 写入与 Agent 响应返回解耦，监控写入失败不影响响应返回给前端 |
| **无额外网络开销** | 不引入额外的 HTTP 调用或消息队列，所有采集在本地内存完成 |
| **无额外锁竞争** | 使用 Atomic 变量而非 synchronized，不引入锁等待 |

#### 异常隔离示例

```java
// LoggingChatModel.stream() 中的采集 —— 任何异常不影响 Flux 传递
.doOnNext(resp -> {
    try {
        long now = System.currentTimeMillis();
        lastToken.set(now);
        if (count.incrementAndGet() == 1) {
            firstToken.set(now);
        }
    } catch (Exception e) {
        log.warn("[监控采集] Token 计时异常，已忽略", e);
    }
})
.doFinally(signal -> {
    try {
        TOKEN_TIMINGS.get().add(new TokenTiming(
            start, firstToken.get(), lastToken.get(), count.get()));
    } catch (Exception e) {
        log.warn("[监控采集] TokenTiming 写入异常，已忽略", e);
    }
})
```

```java
// AgentRuntimeServiceImpl.complete() 中的监控写入 —— 包裹在 try-catch
try {
    List<TokenTiming> timings = ChatClientSupport.drainTokenTimings();
    run.setTtftMs(/* 首次 TTFT */);
    run.setTpotMs(/* 加权平均 TPOT */);
    run.setTokenCount(/* token 总数 */);
} catch (Exception e) {
    log.warn("[监控采集] Token 计时汇总异常，已忽略", e);
} finally {
    ChatClientSupport.clearTokenTimings(); // 兆底清理
}
```

---

## 2. 数据库设计

### 2.1 增强 ai_agent_run 表

在现有表基础上新增以下列（通过 patch SQL 迁移）：

```sql
ALTER TABLE ai_agent_run
  ADD COLUMN IF NOT EXISTS user_message         TEXT,            -- 用户输入原文
  ADD COLUMN IF NOT EXISTS response_summary     TEXT,            -- 响应摘要（前 500 字）
  ADD COLUMN IF NOT EXISTS duration_ms          BIGINT,          -- 执行耗时（毫秒）
  ADD COLUMN IF NOT EXISTS error_message        VARCHAR(1000),   -- 失败原因
  ADD COLUMN IF NOT EXISTS model_name           VARCHAR(64),     -- 使用的模型名
  ADD COLUMN IF NOT EXISTS workflow_type        VARCHAR(32),     -- 工作流类型（SEQUENTIAL/GRAPH/REACT/ROUTING）
  ADD COLUMN IF NOT EXISTS user_id              BIGINT,          -- 触发人 ID
  ADD COLUMN IF NOT EXISTS run_type             VARCHAR(16) NOT NULL DEFAULT 'CHAT',  -- CHAT | TRIAL
  ADD COLUMN IF NOT EXISTS ttft_ms              BIGINT,          -- TTFT（Time To First Token），首 Token 到达时间（毫秒）
  ADD COLUMN IF NOT EXISTS tpot_ms              BIGINT,          -- TPOT（Time Per Output Token），每个输出 Token 平均耗时（毫秒）
  ADD COLUMN IF NOT EXISTS token_count           INT;             -- 响应 Token 总数（所有 LLM 调用累计）
```

### 2.2 新增索引

```sql
CREATE INDEX IF NOT EXISTS idx_agent_run_status ON ai_agent_run (status);
CREATE INDEX IF NOT EXISTS idx_agent_run_time ON ai_agent_run (create_time DESC);
CREATE INDEX IF NOT EXISTS idx_agent_run_user ON ai_agent_run (user_id);
```

### 2.3 字段说明

| 字段 | 类型 | 写入时机 | 说明 |
|------|------|---------|------|
| user_message | TEXT | start() | 用户原始输入 |
| response_summary | TEXT | complete() | 成功时截取响应前 500 字 |
| duration_ms | BIGINT | complete() | `System.currentTimeMillis() - startTime` |
| error_message | VARCHAR(1000) | complete() | 失败时记录异常消息 |
| model_name | VARCHAR(64) | start() | 实际使用的模型名 |
| workflow_type | VARCHAR(32) | start() | Agent 的工作流类型 |
| user_id | BIGINT | start() | 从 UserContext 获取当前用户 |
| run_type | VARCHAR(16) | start() | CHAT（正式对话）或 TRIAL（试运行） |
| ttft_ms | BIGINT | complete() | TTFT — 从发起 LLM 调用到收到第一个 token 的毫秒数 |
| tpot_ms | BIGINT | complete() | TPOT — 每个输出 Token 的平均生成耗时（毫秒） |
| token_count | INT | complete() | 响应 Token 总数（所有 LLM 调用累计输出） |

### 2.4 Token 计时采集方案

#### 指标定义

| 指标 | 全称 | 含义 | 计算方式 |
|------|------|------|----------|
| **TTFT** | Time To First Token | 首 Token 到达时间，反映请求响应速度 | `firstTokenTimestamp - requestStartTimestamp` |
| **TPOT** | Time Per Output Token | 每个输出 Token 的平均生成耗时，反映模型生成吞吐 | `(lastTokenTimestamp - firstTokenTimestamp) / (tokenCount - 1)` |
| **token_count** | Output Token Count | 响应输出的 Token 总数 | 每次 LLM 调用的 token 数累加 |

#### 耗时估算公式

```
总生成耗时 ≈ TTFT + TPOT × (tokenCount - 1)
```

- **TTFT**：排队 + 预填充时间（不可并行）
- **TPOT × (tokenCount - 1)**：逐 Token 解码时间（扣除首 Token 后的剩余 Token）
- 该公式可用于校验 `duration_ms` 是否合理，或在 `duration_ms` 缺失时估算总耗时

#### 采集层级

Agent 一次执行可能包含多次 LLM 调用（意图澄清、工具执行、结果润色等），需采集**每次 LLM 调用**的 Token 计时，最终汇总写入 `ai_agent_run`。

采集点位于 `ChatClientSupport.LoggingChatModel` 的 `stream(Prompt)` 方法：

```
LoggingChatModel.stream(Prompt prompt)
  │
  ├─ 记录 requestStart = System.currentTimeMillis()
  ├─ delegate.stream(prompt)  →  Flux<ChatResponse>
  │
  └─ 对 Flux 做 doOnNext 拦截：
       ├─ 第一个 ChatResponse 到达 → 记录 firstTokenTimestamp
       ├─ 每个后续 ChatResponse   → 累加 tokenCount
       └─ Flux 完成               → 计算 TPOT
```

#### 实现方式

在 `ChatClientSupport` 中新增 `TokenTiming` 数据载体和 `ThreadLocal` 收集器：

```java
/** 单次 LLM 调用的 Token 计时数据 */
public record TokenTiming(
    long requestStartMs,     // 请求发起时间
    long firstTokenMs,       // 首 Token 到达时间（0 = 未收到）
    long lastTokenMs,        // 最后一个 Token 到达时间
    int tokenCount           // Token 数量
) {
    public long ttft() {
        return firstTokenMs > 0 ? firstTokenMs - requestStartMs : -1;
    }
    public long tpot() {
        if (tokenCount <= 1) return -1;
        return (lastTokenMs - firstTokenMs) / (tokenCount - 1);
    }
}

/** 收集当前线程所有 LLM 调用的 Token 计时 */
private static final ThreadLocal<List<TokenTiming>> TOKEN_TIMINGS =
    ThreadLocal.withInitial(ArrayList::new);
```

在 `LoggingChatModel.stream()` 中包装 Flux：

```java
@Override
public Flux<ChatResponse> stream(Prompt prompt) {
    logPrompt(prompt);
    long start = System.currentTimeMillis();
    AtomicLong firstToken = new AtomicLong(0);
    AtomicLong lastToken = new AtomicLong(0);
    AtomicInteger count = new AtomicInteger(0);

    return delegate.stream(prompt)
        .doOnNext(resp -> {
            try {  // 异常隔离：采集失败不影响 Flux 传递
                long now = System.currentTimeMillis();
                lastToken.set(now);
                if (count.incrementAndGet() == 1) {
                    firstToken.set(now);
                }
            } catch (Exception e) {
                log.warn("[监控采集] Token 计时异常，已忽略", e);
            }
        })
        .doFinally(signal -> {
            try {  // 异常隔离：ThreadLocal 写入失败不影响后续流程
                TOKEN_TIMINGS.get().add(new TokenTiming(
                    start, firstToken.get(), lastToken.get(), count.get()));
            } catch (Exception e) {
                log.warn("[监控采集] TokenTiming 写入异常，已忽略", e);
            }
        });
}
```

在 `AgentRuntimeServiceImpl.run()` 的 `complete()` 阶段，读取并汇总：

```java
List<TokenTiming> timings = ChatClientSupport.drainTokenTimings();
// 取所有 LLM 调用中第一次的 TTFT 作为首 Token 时间
long ttft = timings.stream()
    .filter(t -> t.ttft() >= 0)
    .mapToLong(TokenTiming::ttft)
    .findFirst().orElse(-1);
// 取所有 LLM 调用的 TPOT 加权平均（按 token 数加权）
long tpot = /* 加权平均计算 */;
run.setTtftMs(ttft);
run.setTpotMs(tpot);
```

#### 数据流

```
LLM 调用开始
  │ LoggingChatModel.stream() 记录 requestStart
  ▼
模型推理中...
  │
  ▼ 第一个 ChatResponse 到达
  │ 记录 firstTokenMs → 计算 TTFT
  ▼
后续 Token 持续到达
  │ 每个 Token 更新 lastTokenMs + tokenCount
  ▼
Flux 完成 (doFinally)
  │ 生成 TokenTiming 写入 ThreadLocal 列表
  ▼
Agent 执行结束
  │ AgentRuntimeServiceImpl 读取所有 TokenTiming
  │ 汇总 TTFT + TPOT + tokenCount → 写入 ai_agent_run
  ▼
前端展示
  │ TTFT 1.2s | TPOT 45ms | Tokens 420 | 总耗时 18.9s
```

---

## 3. 后端 API 设计

### 3.1 Controller：AgentMonitorController

路径前缀：`/api/v1/agent-monitor`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/executions` | 执行历史列表（分页 + 筛选） |
| GET | `/executions/{id}` | 执行详情（含完整轨迹） |
| GET | `/stats` | 统计概览 |
| GET | `/stats/agents` | 按智能体聚合统计 |
| GET | `/running` | 当前运行中的执行列表 |

### 3.2 接口详细设计

#### 3.2.1 执行历史列表

```
GET /api/v1/agent-monitor/executions
  ?agentId=85          -- 可选，按智能体筛选
  &status=SUCCESS      -- 可选：SUCCESS / FAILED / RUNNING
  &runType=CHAT        -- 可选：CHAT / TRIAL
  &keyword=水位        -- 可选，模糊匹配 user_message
  &startTime=...       -- 可选，时间范围起始
  &endTime=...         -- 可选，时间范围截止
  &page=1&size=20      -- 分页
```

响应：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1024,
        "agentId": 85,
        "agentName": "巡检智能体",
        "userMessage": "查询站点0000000006的水位情况",
        "responseSummary": "## 站点 0000000006 水位报告\n\n当前水位：12.35m...",
        "status": "SUCCESS",
        "durationMs": 18923,
        "modelName": "qwen3:1.7b",
        "workflowType": "SEQUENTIAL",
        "runType": "CHAT",
        "ttftMs": 1230,
        "tpotMs": 45,
        "tokenCount": 420,
        "userId": 10001,
        "createTime": "2026-09-05 10:30:00"
      }
    ],
    "total": 156,
    "page": 1,
    "size": 20
  }
}
```

#### 3.2.2 执行详情

```
GET /api/v1/agent-monitor/executions/{id}
```

响应：
```json
{
  "code": 200,
  "data": {
    "id": 1024,
    "agentId": 85,
    "agentName": "巡检智能体",
    "userMessage": "查询站点0000000006的水位情况",
    "responseSummary": "...",
    "status": "SUCCESS",
    "durationMs": 18923,
    "modelName": "qwen3:1.7b",
    "workflowType": "SEQUENTIAL",
    "runType": "CHAT",
    "ttftMs": 1230,
    "tpotMs": 45,
    "tokenCount": 420,
    "traces": [
      {
        "type": "NODE_START",
        "name": "意图澄清",
        "detail": "LLM",
        "durationMs": null,
        "timestamp": 1725512400000
      },
      {
        "type": "NODE_END",
        "name": "意图澄清",
        "detail": "用户需要查询站点0000000006的水位数据...",
        "durationMs": 1200,
        "timestamp": 1725512401200
      },
      {
        "type": "TOOL_CALL",
        "name": "getTerminalOnlineOverview",
        "detail": "{}",
        "durationMs": null,
        "timestamp": 1725512402000
      },
      {
        "type": "TOOL_RESULT",
        "name": "getTerminalOnlineOverview",
        "detail": "{\"terminalCode\":\"0000000006\",\"waterLevel\":12.35...}",
        "durationMs": 800,
        "timestamp": 1725512402800
      }
    ],
    "createTime": "2026-09-05 10:30:00",
    "updateTime": "2026-09-05 10:30:19"
  }
}
```

#### 3.2.3 统计概览

```
GET /api/v1/agent-monitor/stats
  ?period=TODAY   -- TODAY / WEEK / MONTH
```

响应：
```json
{
  "code": 200,
  "data": {
    "totalCount": 156,
    "successCount": 142,
    "failedCount": 10,
    "runningCount": 4,
    "successRate": 91.03,
    "avgDurationMs": 15230,
    "todayCount": 43
  }
}
```

#### 3.2.4 按智能体聚合统计

```
GET /api/v1/agent-monitor/stats/agents
```

响应：
```json
{
  "code": 200,
  "data": [
    {
      "agentId": 85,
      "agentName": "巡检智能体",
      "totalCount": 78,
      "successCount": 72,
      "successRate": 92.31,
      "avgDurationMs": 18500
    },
    {
      "agentId": 1,
      "agentName": "监测智能体",
      "totalCount": 65,
      "successCount": 58,
      "successRate": 89.23,
      "avgDurationMs": 12300
    }
  ]
}
```

#### 3.2.5 当前运行中的执行

```
GET /api/v1/agent-monitor/running
```

返回 status = 'RUNNING' 的记录列表，含 agentName、userMessage、createTime、currentNode。

---

## 4. 后端代码结构

### 4.1 文件变更清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `zhishu-core/.../db/patch_ai_agent_run_monitor.sql` | 表增强 DDL + 索引 |
| 修改 | `zhishu-ai/.../agent/domain/entity/AiAgentRunEntity.java` | 新增 11 个字段（含 ttftMs / tpotMs / tokenCount） |
| 修改 | `zhishu-ai/.../agent/service/AgentRunService.java` | 新增 listExecutions / getStats / getPerAgentStats |
| 修改 | `zhishu-ai/.../agent/service/impl/AgentRunServiceImpl.java` | 实现分页查询 + 统计聚合 |
| 修改 | `zhishu-ai/.../agent/service/impl/AgentRuntimeServiceImpl.java` | start/complete 时补写监控字段 |
| 新建 | `zhishu-ai/.../agent/controller/AgentMonitorController.java` | 监控 API 控制器 |
| 新建 | `zhishu-ai/.../agent/domain/vo/AgentExecutionDetailVO.java` | 执行详情 VO |
| 新建 | `zhishu-ai/.../agent/domain/vo/AgentExecutionStatsVO.java` | 统计概览 VO |

### 4.2 核心改动说明

#### AgentRunEntity 增强
```java
// 新增字段
private String userMessage;
private String responseSummary;
private Long durationMs;
private String errorMessage;
private String modelName;
private String workflowType;
private Long userId;
private String runType;  // CHAT | TRIAL
private Long ttftMs;     // TTFT（Time To First Token）
private Long tpotMs;     // TPOT（Time Per Output Token）
private Integer tokenCount; // 响应 Token 总数
```

#### AgentRuntimeServiceImpl 增强
```java
// start() 时写入
run.setUserMessage(message);
run.setModelName(chatClientSupport.resolveModelName(agent));
run.setWorkflowType(agent.getWorkflowType());
run.setUserId(UserContext.getUserId());
run.setRunType("CHAT");  // trial 时为 "TRIAL"

// complete() 时写入
run.setDurationMs(System.currentTimeMillis() - startTime);
run.setResponseSummary(truncate(result.content(), 500));
// Token 计时：从 ThreadLocal 收集器读取 TTFT / TPOT / Token 总数
// 异常隔离：监控采集失败不影响 Agent 响应
try {
    List<TokenTiming> timings = ChatClientSupport.drainTokenTimings();
    run.setTtftMs(/* 首次 TTFT */);
    run.setTpotMs(/* 加权平均 TPOT */);
    run.setTokenCount(/* 所有 LLM 调用的 tokenCount 累加 */);
} catch (Exception e) {
    log.warn("[监控采集] Token 计时汇总异常，已忽略", e);
} finally {
    ChatClientSupport.clearTokenTimings(); // 兆底清理，防止 ThreadLocal 泄漏
}
// 失败时
run.setErrorMessage(e.getMessage());
```

#### AgentRunServiceImpl 统计实现
```java
// 使用 MyBatis-Plus 的 selectMaps + 聚合函数
// SELECT count(*), avg(duration_ms), status FROM ai_agent_run WHERE ...
// 按 period 过滤 create_time 范围
```

---

## 5. 前端设计

> 前端工程路径：`D:\DataFutureX-Code\zhishu-integrable-framework\zhishu-frontend`
>
> 遵循现有规范：
> - 页面组件放 `src/views/ai/`，使用 `ListPageShell` 布局壳
> - 类型定义放 `src/types/`，使用 `interface` 定义 VO/DTO
> - API 函数统一在 `src/api/ai.ts` 中追加，使用 `aiService` axios 实例
> - 路由通过后端菜单动态注册（`dynamicRouteViews.ts` 自动映射 `views/**/*.vue`）

### 5.1 页面结构

新建页面 `src/views/ai/AgentMonitor.vue`，菜单 component 字段：`views/ai/AgentMonitor.vue`

#### 整体布局（复用 ListPageShell 组件体系）

```vue
<ListPageShell
  :loading="loading"
  hero-title="执行监控"
  hero-eyebrow="智能中心"
  :hero-eyebrow-icon="Monitor"
  :hero-metrics="heroMetrics"
>
  <template #heroDescription>
    共 <strong>{{ stats.totalCount }}</strong> 次执行，成功率 <strong>{{ stats.successRate }}%</strong>
  </template>
  <template #heroActions>
    <el-button size="small" :icon="Refresh" :loading="loading" @click="fetchData">刷新</el-button>
  </template>

  <template #strip>
    <StatusFilterStrip :model-value="statusFilter" :options="statusOptions" @update:model-value="..." />
  </template>

  <template #toolbar>
    <ListToolbar title="执行记录">
      <template #hint>Agent 执行历史与性能指标</template>
      <template #extra><!-- 筛选控件：智能体下拉 + 时间范围 --></template>
    </ListToolbar>
  </template>

  <el-table :data="records" class="modern-table" @row-click="openDetail">
    <!-- 列定义见下方 -->
  </el-table>

  <template #pagination>
    <el-pagination ... />
  </template>

  <template #extra>
    <el-drawer v-model="drawerVisible" class="agent-monitor-drawer" ...>
      <!-- 详情内容 -->
    </el-drawer>
  </template>
</ListPageShell>
```

#### Hero 指标卡片（复用 `heroMetrics` 模式）

```typescript
const heroMetrics = computed(() => [
  { key: 'total', label: '总执行', value: stats.totalCount, icon: DataLine, accent: 'primary' },
  { key: 'success', label: '成功率', value: `${stats.successRate}%`, icon: CircleCheck, accent: 'success' },
  { key: 'duration', label: '平均耗时', value: formatDuration(stats.avgDurationMs), icon: Timer, accent: 'primary' },
  { key: 'running', label: '运行中', value: stats.runningCount, icon: Loading, accent: 'warning' },
])
```

#### 表格列定义（使用 `modern-table` 样式）

| 列 | prop | 宽度 | 特殊渲染 |
|----|------|------|----------|
| 智能体 | agentName | 140 | `font-weight: 500` |
| 用户输入 | userMessage | — | `show-overflow-tooltip`，截取前 60 字 |
| 状态 | status | 90 | `el-tag`：SUCCESS=success / FAILED=danger / RUNNING=primary |
| 耗时 | durationMs | 100 | 格式化为 `18.9s` / `1,203ms` |
| TTFT | ttftMs | 90 | 格式化为 `1.2s`，`—` 表示无数据 |
| TPOT | tpotMs | 90 | 格式化为 `45ms`，`—` 表示无数据 |
| Tokens | tokenCount | 80 | 千分位格式 `420` |
| 模型 | modelName | 120 | `show-overflow-tooltip` |
| 时间 | createTime | 150 | 格式 `09-05 10:30` |

### 5.2 前端文件清单

| 操作 | 文件（zhishu-frontend） | 说明 |
|------|------|------|
| 新建 | `src/views/ai/AgentMonitor.vue` | 监控面板主页面，使用 `ListPageShell` + `el-table` + `el-drawer` |
| 新建 | `src/types/agentMonitor.ts` | 类型定义（`AgentExecutionVO` / `AgentExecutionDetailVO` / `AgentMonitorStatsVO` / `AgentMonitorQueryDTO`） |
| 修改 | `src/api/ai.ts` | 追加 5 个监控 API 函数（使用 `aiService` 实例） |
| 新建 | `zhishu-core/.../db/patch_menu_agent_monitor.sql` | 菜单注册 SQL（component = `views/ai/AgentMonitor.vue`） |

#### 5.2.1 类型定义（`src/types/agentMonitor.ts`）

```typescript
/** Agent 执行监控类型定义 */

export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'RUNNING'
export type RunType = 'CHAT' | 'TRIAL'

export interface AgentExecutionVO {
  id: number
  agentId: number
  agentName: string
  userMessage?: string | null
  responseSummary?: string | null
  status: ExecutionStatus | string
  durationMs?: number | null
  modelName?: string | null
  workflowType?: string | null
  runType: RunType | string
  ttftMs?: number | null
  tpotMs?: number | null
  tokenCount?: number | null
  userId?: number | null
  createTime?: string | null
}

export interface AgentExecutionDetailVO extends AgentExecutionVO {
  traces: AgentTraceEvent[]
  updateTime?: string | null
}

export interface AgentMonitorStatsVO {
  totalCount: number
  successCount: number
  failedCount: number
  runningCount: number
  successRate: number
  avgDurationMs: number
  todayCount: number
}

export interface AgentMonitorQueryDTO {
  agentId?: number
  status?: string
  runType?: string
  keyword?: string
  startTime?: string
  endTime?: string
  page: number
  size: number
}
```

#### 5.2.2 API 函数（追加到 `src/api/ai.ts`）

```typescript
import type {
  AgentExecutionVO,
  AgentExecutionDetailVO,
  AgentMonitorStatsVO,
  AgentMonitorQueryDTO,
} from '@/types/agentMonitor'

/** 执行历史列表（分页 + 筛选） */
export function getAgentExecutions(params: AgentMonitorQueryDTO): Promise<PageResult<AgentExecutionVO>> {
  return aiService.get('/agent-monitor/executions', { params })
}

/** 执行详情（含完整轨迹） */
export function getAgentExecutionDetail(id: number): Promise<AgentExecutionDetailVO> {
  return aiService.get(`/agent-monitor/executions/${id}`)
}

/** 统计概览 */
export function getAgentMonitorStats(period: string = 'TODAY'): Promise<AgentMonitorStatsVO> {
  return aiService.get('/agent-monitor/stats', { params: { period } })
}

/** 按智能体聚合统计 */
export function getAgentMonitorAgentStats(): Promise<AgentMonitorAgentStatsVO[]> {
  return aiService.get('/agent-monitor/stats/agents')
}

/** 当前运行中的执行列表 */
export function getAgentMonitorRunning(): Promise<AgentExecutionVO[]> {
  return aiService.get('/agent-monitor/running')
}
```

### 5.3 交互说明

1. **统计卡片**：页面加载时请求 `/agent-monitor/stats`，支持切换时间范围（今日/本周/本月）
2. **执行列表**：默认显示最近 20 条，支持筛选条件组合；状态列用 `el-tag` 颜色区分（成功=success、失败=danger、运行中=primary）
3. **执行详情**：点击行打开右侧 `el-drawer`，展示基本信息 + 轨迹时间线 + 响应内容
4. **轨迹时间线**：使用 `el-timeline` 组件，复用已有 `AgentTraceEvent` 类型（`src/types/aiAgent.ts`），不同类型事件用不同颜色（使用项目 CSS 变量）：
   - NODE_START / NODE_END：`$primary-color`（#0969da 蓝）
   - TOOL_CALL：`$warning-color`（#9a6700 橙）
   - TOOL_RESULT：`$success-color`（#1a7f37 绿）
   - ROUTE：`#8250df`（紫，与项目主题一致）
   - 每个节点显示名称 + 耗时 + 详情摘要
5. **TTFT / TPOT 展示**：在详情抽屉的基本信息区域显示，格式：`TTFT：1.2s | TPOT：45ms | Tokens：420`

### 5.4 样式规范（与现有页面统一）

> 项目采用 GitHub Primer 蓝色主题，所有样式必须复用现有变量和组件体系，禁止自定义色值或硬编码。

#### 样式变量引用

```scss
// 所有 scoped 样式必须引入
@use '@/styles/variables.scss' as *;

// 颜色全部使用变量，禁止硬编码
$primary-color: #0969da;    // 主色
$success-color: #1a7f37;    // 成功
$warning-color: #9a6700;    // 警告
$danger-color: #cf222e;     // 失败
$text-primary: #1f2328;     // 主文字
$text-secondary: #656d76;   // 次要文字
$border-color: #d0d7de;     // 边框
$bg-color: #f6f8fa;         // 背景
```

#### 组件样式复用清单

| UI 元素 | 复用方式 | 参考页面 |
|---------|----------|----------|
| 页面布局 | `ListPageShell` + `PageHero` + `StatusFilterStrip` + `ListToolbar` | AgentManage / ModelSettings |
| 表格 | `el-table class="modern-table"` | ModelSettings / UserList |
| 状态标签 | `el-tag size="small" type="success/danger/primary" effect="light"` | ModelSettings |
| 空状态 | `el-empty :image-size="88"` | AgentManage / McpHub |
| 分页 | `el-pagination` 放在 `#pagination` 插槽 | UserList / MenuList |
| 抽屉 | `el-drawer` 放在 `#extra` 插槽，自定义 class | AgentManage（工具抽屉） |
| 按钮 | `el-button size="small"`，主操作 `type="primary"` | 所有页面 |
| 搜索框 | `el-input clearable :prefix-icon="Search"` | AgentManage / McpHub |
| 筛选组 | `el-radio-group` + `el-radio-button` | AgentManage / McpHub |

#### 抽屉样式参考（与 AgentManage 工具抽屉一致）

```scss
// 非 scoped，与现有 drawer 样式模式一致
.agent-monitor-drawer {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 18px 20px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
    position: relative;

    &::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0;
      height: 3px;
      background: linear-gradient(90deg, #409eff 0%, #66b1ff 50%, #409eff 100%);
    }
  }

  .el-drawer__body {
    padding: 0;
    background: $bg-color;
    overflow: auto;
  }
}
```

#### 时间线样式

```scss
// scoped 内
.monitor-timeline {
  padding: 16px 20px;

  :deep(.el-timeline-item__wrapper) {
    padding-left: 20px;
  }

  :deep(.el-timeline-item__node) {
    width: 10px;
    height: 10px;
  }

  &__event {
    font-size: 13px;
    color: $text-primary;
  }

  &__duration {
    font-size: 12px;
    color: $text-secondary;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }

  &__detail {
    margin-top: 4px;
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.5;
    max-height: 60px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
```

#### 指标展示样式

```scss
// 抽屉内基本信息区的指标展示
.monitor-metrics {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  padding: 12px 20px;
  border-bottom: 1px solid $border-lighter;

  &__item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__value {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
}
```

---

## 6. 实施顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| 1 | 数据库 patch SQL + Entity 字段增强 | 无 |
| 2 | AgentRunService 增强（分页/统计） + RuntimeService 补写监控字段 | 阶段 1 |
| 3 | AgentMonitorController + VO | 阶段 2 |
| 4 | 前端页面 + API 封装 + 菜单注册 | 阶段 3 |
| 5 | 联调验证 | 阶段 4 |
