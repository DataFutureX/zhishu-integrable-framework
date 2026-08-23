# 知枢 Open API 接入 SDK 使用说明

## 1. 概述

知枢平台提供 Open API 供外部系统调用 AI 对话、知识问答、知识图谱等能力。  
调用方通过 AK/SK 签名鉴权，SDK 封装了签名算法与 HTTP 调用逻辑，帮助快速接入。

### 1.1 鉴权原理

| 要素 | 说明 |
|------|------|
| Access Key (AK) | 公开标识，由知枢分配，格式 `zsak_` + 32 位 hex |
| Secret Key (SK) | 密钥明文，由知枢分配，**仅生成时展示一次**，格式 `zssk_` + 64 位 hex |
| Token 格式 | `{ak}:{timestamp_ms}:{signature}` |
| 签名算法 | `signature = Base64URL(HMAC-SHA256(sk, ak + timestamp_ms))` |
| 有效期 | 服务端容忍 5 分钟时间偏差 |
| 传递方式 | HTTP Header: `Authorization: Bearer {token}` |

### 1.2 调用范围（Scope）

| Scope | 说明 | 接口前缀 |
|-------|------|----------|
| `chat` | AI 智能对话 | `/open/v1/chat`, `/open/v1/agents` |
| `knowledges` | 知识问答 | `/open/v1/knowledges` |
| `kg` | 知识图谱 | `/open/v1/kg` |

---

## 2. 快速开始

### 2.1 获取 AK/SK

1. 登录知枢管理后台 → **开放能力** → **Open API 接入凭证管理**
2. 点击 **新增应用**，填写编码、名称、调用范围
3. 点击 **生成 AK/SK**，**立即复制保存 Secret Key**（关闭后无法再次查看）

### 2.2 Maven 依赖

```xml
<dependency>
    <groupId>cn.datafuturex.zhishu</groupId>
    <artifactId>zhishu-openapi-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 需先对 SDK 模块执行 `mvn install`，或发布到贵司 Maven 私服。

### 2.3 初始化客户端

```java
import cn.datafuturex.zhishu.openapi.sdk.ZhishuOpenApiClient;

ZhishuOpenApiClient client = ZhishuOpenApiClient.builder()
    .baseUrl("https://zhishu.example.com")  // 知枢平台地址
    .accessKey("zsak_0123456789abcdef0123456789abcdef")
    .secretKey("zssk_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    .build();
```

---

## 3. 使用示例

### 3.1 同步对话

```java
import cn.datafuturex.zhishu.openapi.sdk.model.ChatRequest;
import cn.datafuturex.zhishu.openapi.sdk.model.ChatResponse;

// 简单对话
ChatResponse resp = client.chat(ChatRequest.of("你好，请介绍一下自己"));
System.out.println(resp.content());

// 带上下文的对话
ChatResponse resp = ChatRequest.builder("请分析这段数据")
    .conversationId("conv-xxx")  // 续接会话
    .agentId(1L)                 // 指定智能体
    .enableMemory(true)          // 保存问答历史
    .build();
ChatResponse result = client.chat(request);
```

### 3.2 查询智能体列表

```java
import cn.datafuturex.zhishu.openapi.sdk.model.AgentInfo;
import java.util.List;

List<AgentInfo> agents = client.listAgents();
agents.forEach(a -> System.out.println(a.id() + ": " + a.name()));
```

### 3.3 知识图谱推送

```java
import cn.datafuturex.zhishu.openapi.sdk.model.KgUpsertRequest;
import cn.datafuturex.zhishu.openapi.sdk.model.KgSyncResult;
import java.util.List;
import java.util.Map;

KgUpsertRequest request = new KgUpsertRequest(
    false,  // dryRun
    List.of(
        Map.of("id", "station-001", "label", "遥测站", "type", "station"),
        Map.of("id", "sensor-001", "label", "水位计", "type", "sensor")
    ),
    List.of(
        Map.of("from", "station-001", "to", "sensor-001", "label", "配备")
    ),
    null
);

KgSyncResult result = client.kgUpsert(request);
System.out.println("新增节点: " + result.nodesCreated());
```

### 3.4 通用 HTTP 调用

SDK 提供通用 GET / POST / PUT / DELETE 方法，可调用任意 Open API：

```java
// GET 请求
Map<String, Object> stats = client.get("/open/v1/kg/stats", Map.class);

// POST 请求（自定义类型）
MyResponse resp = client.post("/open/v1/knowledges/qa/stream", queryBody, MyResponse.class);

// DELETE 请求
client.delete("/open/v1/some-resource/123");
```

### 3.5 仅使用签名（不依赖 HTTP 客户端）

如果已有 HTTP 框架（如 Spring RestTemplate、OkHttp），可仅使用签名器：

```java
import cn.datafuturex.zhishu.openapi.sdk.ZhishuOpenApiSigner;

String token = ZhishuOpenApiSigner.sign(ak, sk);
String authHeader = "Bearer " + token;

// 使用你喜欢的 HTTP 客户端发送请求
// GET https://zhishu.example.com/open/v1/agents
// Header: Authorization: Bearer {token}
```

---

## 4. 非 Java 接入指南

如果使用其他语言，按以下协议自行实现签名即可：

### 4.1 签名步骤

1. 获取当前毫秒时间戳 `ts`
2. 拼接签名原文：`signingInput = ak + ts`（字符串直接拼接）
3. 计算 HMAC-SHA256：`hash = HMAC-SHA256(sk, signingInput)`
4. Base64URL 编码：`signature = Base64URL(hash)`（无填充 `=`）
5. 组装 Token：`token = ak + ":" + ts + ":" + signature`
6. 设置请求头：`Authorization: Bearer {token}`

### 4.2 Python 示例

```python
import hmac, hashlib, base64, time, requests

def sign(ak: str, sk: str) -> str:
    ts = str(int(time.time() * 1000))
    signing_input = ak + ts
    signature = base64.urlsafe_b64encode(
        hmac.new(sk.encode(), signing_input.encode(), hashlib.sha256).digest()
    ).decode().rstrip("=")
    return f"{ak}:{ts}:{signature}"

ak = "zsak_xxxx"
sk = "zssk_xxxx"
headers = {"Authorization": f"Bearer {sign(ak, sk)}"}
resp = requests.get("https://zhishu.example.com/open/v1/agents", headers=headers)
print(resp.json())
```

### 4.3 cURL 示例

```bash
#!/bin/bash
AK="zsak_xxxx"
SK="zssk_xxxx"
TS=$(date +%s%3N)
SIGNING_INPUT="${AK}${TS}"
SIG=$(echo -n "$SIGNING_INPUT" | openssl dgst -sha256 -hmac "$SK" -binary | base64 | tr '+/' '-_' | tr -d '=')
TOKEN="${AK}:${TS}:${SIG}"

curl -H "Authorization: Bearer $TOKEN" \
     https://zhishu.example.com/open/v1/agents
```

---

## 5. 错误处理

| HTTP 状态码 | 含义 | 可能原因 |
|------------|------|---------|
| 401 | 认证失败 | AK/SK 无效、Token 过期、签名错误 |
| 403 | 权限不足 | 应用已停用、无对应 Scope 权限 |
| 400 | 请求参数错误 | 缺少必填字段、格式不正确 |
| 500 | 服务端异常 | 联系知枢运维 |

SDK 抛出 `OpenApiException`，可通过 `getStatusCode()` 和 `getBody()` 获取详情：

```java
try {
    client.chat(ChatRequest.of("你好"));
} catch (OpenApiException e) {
    System.err.println("HTTP " + e.getStatusCode());
    System.err.println("响应: " + e.getBody());
}
```

---

## 6. 安全建议

1. **SK 仅展示一次**：生成后立即安全存储（如密钥管理系统），不要硬编码在代码中
2. **使用环境变量**：生产环境通过环境变量或配置中心传入 AK/SK
3. **最小权限原则**：仅勾选必需的调用范围（Scope）
4. **定期轮换 SK**：通过"轮换 SK"功能重新生成，旧 SK 立即失效
5. **HTTPS**：始终使用 HTTPS 传输，防止 Token 被窃听

---

## 7. SDK 模块结构

```
sdk/zhishu-openapi-sdk/
├── pom.xml
├── README.md
└── src/
    ├── main/java/cn/datafuturex/zhishu/openapi/sdk/
    │   ├── ZhishuOpenApiClient.java     # 主客户端（Builder 模式）
    │   ├── ZhishuOpenApiSigner.java     # HMAC-SHA256 签名器
    │   ├── OpenApiException.java        # 异常类型
    │   ├── package-info.java
    │   └── model/
    │       ├── ChatRequest.java         # 对话请求
    │       ├── ChatResponse.java        # 对话响应
    │       ├── AgentInfo.java           # 智能体信息
    │       ├── KgUpsertRequest.java     # 知识图谱推送请求
    │       └── KgSyncResult.java        # 知识图谱同步结果
    └── test/java/.../
        └── ZhishuOpenApiSignerTest.java # 签名器单元测试
```
