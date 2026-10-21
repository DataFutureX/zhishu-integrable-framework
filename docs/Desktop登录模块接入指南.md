# 知枢 Desktop 登录模块接入指南

> 面向桌面客户端开发者的端到端登录对接手册

## 1. 概述

知枢平台为桌面客户端（Desktop Client）提供了专用的登录 API 通道，接口路径统一以 `/desktop/v1` 为前缀。本文档覆盖从服务端配置、加密方案、接口调用到 Token 管理、异常处理与登出的完整流程。

与控制台 `/api/v1/auth` 相比，桌面端登录的唯一区别是**不要求滑动验证码**，其余 RSA 加密方案、JWT 签发与吊销逻辑完全一致。

### 1.1 适用场景

- Windows / macOS / Linux 桌面客户端软件
- Electron / WPF / JavaFX / Qt / Flutter 等跨平台框架
- 移动端 App 亦可复用（本接口不依赖浏览器环境）

### 1.2 服务端配置前提

服务端 `application.yml` 桌面模块相关默认配置：

```yaml
zhishu:
  desktop:
    enabled: true                 # 桌面模块总开关
    auth-key: ${ZHISHU_DESKTOP_AUTH_KEY:}   # 服务信息接口 X-Desktop-Key 密钥（不影响登录）
    auth-header: X-Desktop-Key
    api-version: 1.0.0

yunqi:
  login-crypto:
    enabled: true                 # 是否启用 RSA 加密登录凭证
    key-size: 2048
    key-expire-seconds: 300       # 公钥有效期 5 分钟

jwt:
  secret: zhishu-secret-key-for-jwt-token-generation-2024
  expiration: 86400000            # 24 小时
```

- 若 `yunqi.login-crypto.enabled=false`，可跳过加密步骤直接明文传递（**仅推荐本地调试**）
- 若 `zhishu.desktop.enabled=false`，桌面登录接口不可用

---

## 2. 加密方案

### 2.1 算法参数

| 参数 | 值 |
|------|----|
| 算法 | RSA |
| 密钥长度 | 2048 bit（可通过 `yunqi.login-crypto.key-size` 调整） |
| 填充模式 | `RSA/ECB/PKCS1Padding`（Hutool RSA 默认，兼容 JSEncrypt） |
| 公钥编码 | X.509 / SPKI，PEM 格式（`-----BEGIN PUBLIC KEY-----` … `-----END PUBLIC KEY-----`） |
| 密文编码 | Base64（标准字母表） |
| 加密内容 | 分别对 `username` 与 `password` 单独加密（不是打包 JSON） |
| 一次性使用 | 每份 `keyId` 只能用于一次登录，成功或失败均立即失效 |

> 若服务端返回的 `algorithm` 字段包含 `OAEP` 字样，客户端需切换到 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`。默认场景为 PKCS1v15。

### 2.2 通用流程图

```
┌───────────────┐                                     ┌────────────────┐
│ 桌面客户端     │                                     │ zhishu-desktop │
└──────┬────────┘                                     └───────┬────────┘
       │  ① GET /desktop/v1/auth/public-key                   │
       │─────────────────────────────────────────────────────▶│
       │      ◀── keyId + PEM 公钥 + algorithm + expireSeconds │
       │                                                       │
       │  ② 客户端本地 RSA 加密                                  │
       │  encrypt(username) → base64Username                   │
       │  encrypt(password) → base64Password                   │
       │                                                       │
       │  ③ POST /desktop/v1/auth/login                        │
       │     { username, password, keyId }                     │
       │─────────────────────────────────────────────────────▶│
       │      ◀── JWT token + expiration + 用户基本信息         │
       │                                                       │
       │  ④ 客户端本地保存 token（安全存储）                    │
       │                                                       │
       │  ⑤ POST /desktop/v1/auth/logout                       │
       │     Authorization: Bearer <token>                     │
       │─────────────────────────────────────────────────────▶│
       │      ◀── 200                                          │
```

---

## 3. 接口清单

### 3.1 获取登录公钥

```
GET /desktop/v1/auth/public-key
```

**请求**：无参数、无鉴权。

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "keyId": "8f3c1e2a5b4d4f6a9e0c1d2b3a4f5e6d",
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----",
    "algorithm": "RSA/ECB/PKCS1Padding",
    "expireSeconds": 300
  }
}
```

| 字段 | 说明 |
|------|------|
| `keyId` | 服务端密钥标识，登录时必须回传 |
| `publicKey` | PEM 格式公钥，客户端用它加密 username 和 password |
| `algorithm` | 加密算法描述，若包含 `OAEP` 需按 OAEP 填充 |
| `expireSeconds` | 公钥有效期（秒），超时后 keyId 作废 |

> **务必在每次登录前重新获取公钥**，不要缓存复用 —— 每个 keyId 只能消费一次。

### 3.2 桌面端登录

```
POST /desktop/v1/auth/login
Content-Type: application/json
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | ✅ | RSA 加密后的 Base64 密文（`login-crypto.enabled=true` 时）；否则为明文 |
| `password` | String | ✅ | RSA 加密后的 Base64 密文；否则为明文 |
| `keyId` | String | ✅（加密模式） | 由 3.1 返回，登录一次即失效 |

**成功响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6......",
    "expiration": 1761033600000,
    "userId": 1,
    "username": "admin",
    "realName": "系统管理员",
    "role": "ROLE_ADMIN"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | String | JWT Token，后续请求通过 `Authorization: Bearer <token>` 携带 |
| `expiration` | Long | Token 过期时间戳（毫秒），到期后需重新登录 |
| `userId` | Long | 用户 ID |
| `username` | String | 用户名 |
| `realName` | String | 真实姓名 |
| `role` | String | 角色编码 |

**失败响应**：

| HTTP | code | message | 客户端建议 |
|------|------|---------|-----------|
| 200 | 400 | 登录凭证无效或已过期 | 重新获取公钥后再登录 |
| 200 | 401 | 用户名或密码错误 | 提示用户重输 |
| 200 | 403 | 账号已被禁用，请联系管理员 | 阻断登录 |
| 200 | 423 | 登录失败次数过多，请 N 分钟后再试 | 提示锁定倒计时 |

> 业务错误统一在 `code` 字段返回，HTTP 状态码保持 200。客户端解析时先看 `code`，非 200 即视为失败。

### 3.3 退出登录

```
POST /desktop/v1/auth/logout
Authorization: Bearer <token>
```

**请求**：无请求体，仅需 `Authorization` 头。

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

服务端将当前 Token 加入黑名单，即便 Token 尚未自然过期也会立即失效。**客户端应同时清除本地保存的 Token**。

---

## 4. 各语言接入示例

### 4.1 Java（推荐 Java 11+，无第三方依赖）

```java
import javax.crypto.Cipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class DesktopLoginClient {

    private static final String BASE = "http://localhost:8180";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        // 1) 获取公钥
        String publicKeyResp = send(HttpRequest.newBuilder(URI.create(BASE + "/desktop/v1/auth/public-key")).GET().build());
        String keyId      = extract(publicKeyResp, "keyId");
        String pemKey     = extract(publicKeyResp, "publicKey").replace("\\n", "\n");
        String algorithm  = extract(publicKeyResp, "algorithm");

        // 2) RSA 加密用户名与密码
        String encUser = encrypt(pemKey, "admin");
        String encPwd  = encrypt(pemKey, "admin123");

        // 3) 登录
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"keyId\":\"%s\"}",
                encUser, encPwd, keyId);
        String loginResp = send(HttpRequest.newBuilder(URI.create(BASE + "/desktop/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        String token = extract(loginResp, "token");
        System.out.println("登录成功，token = " + token);

        // 4) 退出
        send(HttpRequest.newBuilder(URI.create(BASE + "/desktop/v1/auth/logout"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    private static String encrypt(String pemPublicKey, String plainText) throws Exception {
        String base64 = pemPublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
        Cipher cipher = Cipher.getInstance(algorithm(pemPublicKey));
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private static String algorithm(String pem) {
        // 服务端返回的 algorithm 需从响应单独取；此处按 PKCS1v15 处理即可
        return "RSA/ECB/PKCS1Padding";
    }

    private static String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    /** 简易 JSON 字段提取（生产建议使用 Jackson/Gson） */
    private static String extract(String json, String field) {
        int i = json.indexOf("\"" + field + "\":");
        int s = json.indexOf('"', i + field.length() + 3) + 1;
        int e = json.indexOf('"', s);
        return json.substring(s, e);
    }
}
```

### 4.2 Python（`requests` + `pycryptodome`）

```python
import base64
import json
import requests
from Crypto.PublicKey import RSA
from Crypto.Cipher import PKCS1_OAEP, PKCS1_v1_5

BASE = "http://localhost:8180"


def encrypt_with_public_key(pem: str, plaintext: str, use_oaep: bool = False) -> str:
    key = RSA.import_key(pem)
    cipher = PKCS1_OAEP.new(key) if use_oaep else PKCS1_v1_5.new(key)
    ciphertext = cipher.encrypt(plaintext.encode("utf-8"))
    return base64.b64encode(ciphertext).decode("ascii")


def login(username: str, password: str) -> str:
    # 1) 获取公钥
    resp = requests.get(f"{BASE}/desktop/v1/auth/public-key").json()
    data = resp["data"]
    use_oaep = "OAEP" in data["algorithm"]

    # 2) RSA 加密
    enc_user = encrypt_with_public_key(data["publicKey"], username, use_oaep)
    enc_pwd  = encrypt_with_public_key(data["publicKey"], password, use_oaep)

    # 3) 登录
    login_body = {"username": enc_user, "password": enc_pwd, "keyId": data["keyId"]}
    resp = requests.post(f"{BASE}/desktop/v1/auth/login", json=login_body).json()
    if resp["code"] != 200:
        raise RuntimeError(f"登录失败: {resp['message']}")
    return resp["data"]["token"]


def logout(token: str):
    requests.post(f"{BASE}/desktop/v1/auth/logout",
                  headers={"Authorization": f"Bearer {token}"})


if __name__ == "__main__":
    token = login("admin", "admin123")
    print("登录成功:", token[:32], "...")
    logout(token)
```

### 4.3 Node.js / Electron（内置 `crypto` 模块）

```javascript
const crypto = require('node:crypto');

const BASE = 'http://localhost:8180';

function rsaEncrypt(plain, publicKeyPem, useOaep = false) {
  const buffer = Buffer.from(plain, 'utf-8');
  const opts = useOaep
    ? { key: publicKeyPem, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' }
    : { key: publicKeyPem, padding: crypto.constants.RSA_PKCS1_PADDING };
  return crypto.publicEncrypt(opts, buffer).toString('base64');
}

async function login(username, password) {
  // 1) 获取公钥
  const keyResp = await fetch(`${BASE}/desktop/v1/auth/public-key`).then(r => r.json());
  const { keyId, publicKey, algorithm } = keyResp.data;
  const useOaep = algorithm && algorithm.includes('OAEP');

  // 2) 加密
  const encUser = rsaEncrypt(username, publicKey, useOaep);
  const encPwd  = rsaEncrypt(password, publicKey, useOaep);

  // 3) 登录
  const loginResp = await fetch(`${BASE}/desktop/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: encUser, password: encPwd, keyId }),
  }).then(r => r.json());
  if (loginResp.code !== 200) throw new Error(loginResp.message);
  return loginResp.data;
}

async function logout(token) {
  await fetch(`${BASE}/desktop/v1/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

// 用法
(async () => {
  const user = await login('admin', 'admin123');
  console.log('登录成功', user.username, 'token expires at', new Date(user.expiration));
  await logout(user.token);
})();
```

> Electron 场景下推荐在主进程执行登录与 Token 持久化，通过 IPC 传给渲染进程，避免 Token 泄漏到 DevTools。

### 4.4 C# / .NET（WPF、WinForms、MAUI 均可）

```csharp
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;

public class DesktopLoginClient
{
    private static readonly HttpClient _http = new();
    private const string Base = "http://localhost:8180";

    public record PublicKeyResp(string keyId, string publicKey, string algorithm, int expireSeconds);
    public record LoginReq(string username, string password, string keyId);
    public record LoginResp(string token, long expiration, long userId, string username, string realName, string role);

    public async Task<LoginResp> LoginAsync(string username, string password)
    {
        // 1) 获取公钥
        var keyResp = await _http.GetFromJsonAsync<ApiResp<PublicKeyResp>>(
            $"{Base}/desktop/v1/auth/public-key");
        var (keyId, pem, algorithm, _) = keyResp!.data;

        // 2) RSA 加密（.NET 8+ 支持从 PEM 导入）
        using var rsa = RSA.Create();
        rsa.ImportFromPem(pem);
        string encUser = Convert.ToBase64String(
            rsa.Encrypt(Encoding.UTF8.GetBytes(username), RSAEncryptionPadding.Pkcs1));
        string encPwd = Convert.ToBase64String(
            rsa.Encrypt(Encoding.UTF8.GetBytes(password), RSAEncryptionPadding.Pkcs1));

        // 3) 登录
        var resp = await _http.PostAsJsonAsync($"{Base}/desktop/v1/auth/login",
            new LoginReq(encUser, encPwd, keyId));
        var body = await resp.Content.ReadFromJsonAsync<ApiResp<LoginResp>>();
        if (body!.code != 200) throw new Exception(body.message);
        return body.data;
    }

    public async Task LogoutAsync(string token)
    {
        using var req = new HttpRequestMessage(HttpMethod.Post, $"{Base}/desktop/v1/auth/logout");
        req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
        await _http.SendAsync(req);
    }

    private record ApiResp<T>(int code, string message, T data);
}
```

> 若目标是 .NET Framework / .NET 6/7，`RSA.ImportFromPem` 不可用，需先将 PEM 转成 `RSACryptoServiceProvider` 兼容的 XML 或 DER。

---

## 5. Token 生命周期与存储

### 5.1 有效期

- 默认 24 小时（`jwt.expiration=86400000`）
- 服务端不主动续期；`expiration` 字段返回具体到期时间戳（毫秒）
- 客户端应在到期前 5 分钟触发"过期"逻辑，避免边界问题

### 5.2 存储建议

| 客户端类型 | 推荐方案 |
|-----------|---------|
| Windows WPF/WinForms | `Windows Credential Manager`（`CredentialManager` API） |
| Electron | `keytar`（走各平台原生密钥链），避免明文写文件 |
| macOS | `Keychain Services` |
| Linux | `libsecret` / `Secret Storage` |
| JavaFX | `java.util.prefs.Preferences` 加密层，或调用本机密钥链 |
| 移动端 | iOS Keychain / Android EncryptedSharedPreferences |

**不要**将 Token 保存在明文配置文件、LocalStorage、URL Query 参数中。

### 5.3 请求携带

后续访问需要用户身份的服务端接口时，统一携带：

```
Authorization: Bearer <token>
```

知枢现有 `/api/v1/**`（控制台接口，如获取当前用户信息、菜单等）在 JWT 有效的前提下亦可由桌面客户端调用，与浏览器控制台共用同一套鉴权体系。

---

## 6. 常见错误与排查

| 现象 | 根因 | 处置 |
|------|------|------|
| `code:400` 登录凭证无效或已过期 | 公钥超时 / keyId 已使用过 | 每次登录前重新调 `/public-key` |
| `code:401` 用户名或密码错误 | 密码错误、账号不存在 | 提示用户重输 |
| `code:403` 账号已被禁用 | 管理员禁用了账号 | 阻断，联系管理员 |
| `code:423` 登录失败次数过多 | 触发限流锁定 | 提示倒计时等待，或改用其他账号 |
| RSA 加密失败 | PEM 换行未还原、填充模式不匹配 | 检查 `algorithm` 是否为 OAEP；PEM 需保留换行 |
| Base64 解码报错 | 客户端使用了 URL-Safe Base64 | 服务端要求标准 Base64（含 `+` `/`） |
| 401 后请求仍放行 | Token 已被拉黑但仍缓存 | 客户端应识别 401 触发重新登录 |
| 桌面模块接口返回 404 | `zhishu.desktop.enabled=false` | 联系管理员开启模块 |

---

## 7. 完整时序 Demo（Node.js）

```javascript
const crypto = require('node:crypto');
const BASE = 'http://localhost:8180';

async function main() {
  // ── 登录 ──
  const { data: pk } = await fetch(`${BASE}/desktop/v1/auth/public-key`).then(r => r.json());
  const useOaep = pk.algorithm?.includes('OAEP');
  const enc = s => crypto.publicEncrypt({
    key: pk.publicKey,
    padding: useOaep ? crypto.constants.RSA_PKCS1_OAEP_PADDING : crypto.constants.RSA_PKCS1_PADDING,
  }, Buffer.from(s, 'utf-8')).toString('base64');

  const res = await fetch(`${BASE}/desktop/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: enc('admin'), password: enc('admin123'), keyId: pk.keyId }),
  }).then(r => r.json());
  if (res.code !== 200) throw new Error(res.message);

  const { token, expiration, username, realName } = res.data;
  console.log(`欢迎 ${realName}(${username})，Token 将于 ${new Date(expiration).toLocaleString()} 过期`);

  // ── 携带 Token 调用其他接口 ──
  const me = await fetch(`${BASE}/api/v1/users/current`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then(r => r.json());
  console.log('当前用户:', me.data);

  // ── 退出登录 ──
  await fetch(`${BASE}/desktop/v1/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  console.log('已安全退出');
}

main().catch(e => { console.error(e); process.exit(1); });
```

---

## 8. 安全注意事项

1. **传输安全**：生产环境部署 HTTPS，防止 MITM 中间人替换公钥。
2. **公钥一次性**：一个 `keyId` 只能消费一次；失败登录也要重新申请公钥。
3. **密码长度**：RSA-2048 + PKCS1v15 理论最大明文 245 字节，足以覆盖任何合理密码；若密码策略允许超长字符串，需分块或改用 OAEP。
4. **Token 泄露处置**：一旦怀疑 Token 外泄，客户端应主动调用 `/logout`；管理端还可通过"重置密码 / 禁用账号"批量失效该用户所有 Token。
5. **审计渠道**：桌面端登录日志的渠道字段固定为 `DESKTOP`，运维侧可通过 `sys_operation_log.requestParams.channel` 区分登录入口。

---

## 9. 附录：接口快速对照表

| 场景 | 请求 | 鉴权 |
|------|------|------|
| 获取登录公钥 | `GET /desktop/v1/auth/public-key` | 无 |
| 桌面登录 | `POST /desktop/v1/auth/login` | 无（不校验 X-Desktop-Key，不含验证码） |
| 桌面退出 | `POST /desktop/v1/auth/logout` | `Authorization: Bearer <JWT>` |
| 服务健康检查 | `GET /desktop/v1/health` | 无 |
| 服务能力清单 | `GET /desktop/v1/info` | `X-Desktop-Key: <auth-key>` |

> 后续如新增用户资料 / 消息推送等桌面专属接口，均以 `/desktop/v1/**` 为前缀扩展，登录鉴权方式与本文档保持一致。
