# ZhiShu Open API SDK

知枢平台 Open API 对接 Java SDK，使用 AK/SK 签名鉴权。

- HMAC-SHA256 签名，Token 有效期 5 分钟
- 内置 HTTP 客户端（Java 17 `HttpClient`）与 JSON 序列化（Jackson）
- 提供同步对话、智能体查询、知识图谱推送等便捷方法
- 支持通用 GET / POST / PUT / DELETE 调用任意接口

**完整使用说明（中文）：**  
[docs/知枢OpenAPI接入SDK使用说明.md](../../docs/知枢OpenAPI接入SDK使用说明.md)

## 构建

```bash
cd sdk/zhishu-openapi-sdk
mvn clean test install
```

## 最小示例

```java
ZhishuOpenApiClient client = ZhishuOpenApiClient.builder()
    .baseUrl("https://zhishu.example.com")
    .accessKey("zsak_xxxx")
    .secretKey("zssk_xxxx")
    .build();

// 同步对话
ChatResponse resp = client.chat(ChatRequest.of("你好"));
System.out.println(resp.content());

// 查询智能体列表
List<AgentInfo> agents = client.listAgents();
```

## 仅签名（不依赖 SDK 客户端）

```java
String token = ZhishuOpenApiSigner.sign(ak, sk);
// Authorization: Bearer {token}
```

## Maven 坐标

```xml
<dependency>
  <groupId>cn.datafuturex.zhishu</groupId>
  <artifactId>zhishu-openapi-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

（需先对本模块执行 `mvn install`，或发布到贵司私服。）
