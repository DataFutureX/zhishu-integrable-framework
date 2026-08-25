# YunQi SSO Partner SDK

他方（万象、数智 IoT 等）接入**知枢可集成智能体框架**单点登录的 Java SDK。

- 生成 RSA / SM2 密钥对（公钥交知枢，私钥自持）
- 签发短期 SSO Ticket（`RS256` 或国密 `SM2`）
- 拼装知枢前端回调 URL：`/sso/callback?ticket=...`

**完整使用说明（中文）：**  
[docs/他方SSO接入SDK使用说明.md](../../docs/他方SSO接入SDK使用说明.md)

**协议规范：**  
[docs/单点登录对接说明.md](../../docs/单点登录对接说明.md)

## 构建

```bash
cd sdk/yunqi-sso-partner-sdk
mvn clean test install
```

## 最小示例

```java
YunqiSsoClient client = YunqiSsoClient.builder()
    .issuer("wanxiang")
    .kid("wanxiang-2026")
    .algorithm(SsoAlgorithm.RS256)
    .privateKeyFile(Path.of("wanxiang-private.pem"))
    .yunqiWebBase("https://yunqi.example.com")
    .build();

SsoTicketResult result = client.issueTicket("zhangsan");
// redirect browser to result.callbackUrl()
```

## Maven 坐标

```xml
<dependency>
  <groupId>cn.datafuturex.yunqi</groupId>
  <artifactId>yunqi-sso-partner-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

（需先对本模块执行 `mvn install`，或发布到贵司私服。）
