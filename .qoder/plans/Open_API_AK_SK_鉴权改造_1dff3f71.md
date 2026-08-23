
# Open API AK/SK Token 鉴权改造

## 鉴权流程设计

```
调用方                                    知枢
  │                                        │
  │  1. 知枢分配 AK + SK                    │
  │◄──────────────────────────────────────│
  │                                        │
  │  2. 调用方用 SK 签名生成 Token           │
  │     token = ak + ":" + ts + ":" + sig  │
  │     sig = HMAC-SHA256(sk, ak + ts)     │
  │                                        │
  │  3. Authorization: Bearer <token>      │
  │───────────────────────────────────────►│
  │                                        │
  │  4. 知枢解析 ak → 查 SK → 验签 → 检查时间 │
  │                                        │
  │  5. 放行 / 拒绝                         │
  │◄──────────────────────────────────────│
```

**Token 格式**: `{ak}:{timestamp_ms}:{signature}`
- `ak`: Access Key（明文，用于查找对应 SK）
- `timestamp_ms`: 毫秒级时间戳
- `signature`: Base64(HMAC-SHA256(sk, ak + timestamp_ms))

**安全策略**: 时间戳容差 5 分钟，防重放。

---

## 1. 数据库变更

**文件**: `backend/zhishu-ai/src/main/resources/db/patch_open_app_aksk.sql`（新建）

`open_app` 表新增列:
```sql
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS access_key VARCHAR(64);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS secret_key_hash VARCHAR(128);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS aksk_generated_at TIMESTAMP;
```

- `access_key`: 以 `zsak_` 前缀 + 16 字节随机 hex，全局唯一
- `secret_key_hash`: 明文 SK 仅在生成时返回一次，库中只存 SHA-256 哈希

为已有数据（如 `wanxiang-monitor`）生成 AK/SK 的迁移脚本也在此 SQL 中。

---

## 2. 后端改动

### 2.1 实体类更新

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/domain/entity/OpenAppEntity.java`

新增字段: `accessKey`, `secretKeyHash`, `akskGeneratedAt`

### 2.2 Token 签名/验签工具类

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/support/OpenApiCrypto.java`（新建）

核心方法:
- `generateAccessKey()` → 生成 `zsak_` + 32 hex 字符
- `generateSecretKey()` → 生成 `zssk_` + 48 hex 字符
- `signToken(ak, sk, timestampMs)` → 生成 `{ak}:{ts}:{Base64(HMAC-SHA256(sk, ak+ts))}`
- `verifyToken(token, sk)` → 解析、验签、检查时间戳
- `parseToken(token)` → 返回 `(ak, timestampMs, signature)` 结构

### 2.3 鉴权服务改造

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/service/OpenApiAuthService.java`

改造 `authenticate()` 方法:
1. 从 Bearer token 解析出 `ak:ts:sig`
2. 通过 `access_key` 查找 `open_app`（而非原来的 key_prefix 查 credential 表）
3. 检查应用状态为 ENABLED
4. 用存储的 `secret_key_hash` 对比 — 注意：此处需改为用 SK 原文验签，但库中只存哈希。

**关键设计决策**: 由于 HMAC 验签需要 SK 原文，但安全要求不能存明文，有两种方案：
- **方案 A（推荐）**: `secret_key_hash` 列存储的是 AES 加密后的 SK（而非 SHA-256），用服务端主密钥加解密。验签时解密得到 SK 原文进行 HMAC 验证。
- **方案 B**: `secret_key_hash` 仍存 SHA-256 哈希，但额外增加 `secret_key_enc` 列存 AES 加密的 SK 原文，专供验签使用。

采用 **方案 B**（与现有 MCP 加密模式一致，不改变 `secret_key_hash` 的语义）：
- 新增列 `secret_key_enc TEXT`，存 AES-GCM 加密的 SK 原文
- 加密密钥复用 `wanxiang.mcp.crypto-key` 配置项（或新增 `open.api.crypto-key`）

改造后 `authenticate()` 流程:
1. 解析 token → `(ak, ts, sig)`
2. 查 `open_app` by `access_key = ak`
3. 解密 `secret_key_enc` 得到 SK 原文
4. `HMAC-SHA256(sk, ak + ts)` 与 `sig` 比对
5. 检查 `ts` 与当前时间差 < 5 分钟
6. 更新 `last_used_at`（复用现有 `open_app_credential` 或直接在 `open_app` 上记录）
7. 返回 `AuthenticatedOpenApp`

### 2.4 Filter 适配

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/OpenApiAuthFilter.java`

无需大改，`extractBearer()` 取出的 token 格式变了，`authenticate()` 内部处理新格式即可。保留对旧 API Key 方式的兼容（可选，过渡期）。

### 2.5 AK/SK 管理服务

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/service/OpenAppAdminService.java`（新建）

方法:
- `listApps()` → 返回应用列表（含 AK，不含 SK）
- `generateAkSk(appId)` → 生成 AK/SK 对，存入 DB，返回明文（SK 仅返回一次）
- `regenerateSk(appId)` → 重新生成 SK（旧 SK 立即失效）
- `getApp(appId)` → 返回应用详情

### 2.6 管理接口

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/controller/OpenAppAdminController.java`（新建）

路径前缀: `/api/v1/open-apps`（需认证，管理员操作）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/open-apps` | 应用列表 |
| POST | `/api/v1/open-apps/{id}/generate-aksk` | 生成/重新生成 AK/SK |
| POST | `/api/v1/open-apps/{id}/regenerate-sk` | 仅重新生成 SK |
| PUT | `/api/v1/open-apps/{id}/status` | 启用/停用应用 |

### 2.7 VO/DTO

**文件（新建）**:
- `OpenAppVO.java`: id, code, name, status, accessKey, allowedScopes, remark, akskGeneratedAt, lastUsedAt
- `GenerateAkSkResult.java`: accessKey, secretKey（明文，仅生成时返回）

### 2.8 Schema 初始化更新

**文件**: `backend/zhishu-ai/src/main/java/cn/datafuturex/zhishu/ai/openapi/config/OpenAppSchemaInitializer.java`

在 `run()` 中追加执行 `patch_open_app_aksk.sql`。

---

## 3. 前端改动

### 3.1 API 层

**文件**: `frontend/src/api/openApp.ts`（新建）

```typescript
// GET /api/v1/open-apps
export const listOpenAppsApi = () => get<OpenAppVO[]>('/open-apps')

// POST /api/v1/open-apps/{id}/generate-aksk
export const generateAkSkApi = (id: number) => post<GenerateAkSkResult>(`/open-apps/${id}/generate-aksk`)

// POST /api/v1/open-apps/{id}/regenerate-sk
export const regenerateSkApi = (id: number) => post<GenerateAkSkResult>(`/open-apps/${id}/regenerate-sk`)

// PUT /api/v1/open-apps/{id}/status
export const updateOpenAppStatusApi = (id: number, status: string) => put(`/open-apps/${id}/status`, { status })
```

### 3.2 开放能力页面增加 AK/SK 管理

**文件**: `frontend/src/views/system/OpenApiCapabilities.vue`

在现有"接入说明"和"能力分类"之间，新增 **"接入凭证管理"** 区块:

- 表格展示应用列表: 应用名称、编码、AK（可复制）、状态、生成时间、操作
- 操作按钮:
  - "生成 AK/SK" — 弹窗显示 AK 和 SK（SK 仅显示一次，提示用户复制保存）
  - "重新生成 SK" — 确认后旧 SK 失效，显示新 SK
  - "启用/停用" — 切换应用状态
- 接入说明卡片中 "Open API 认证" 部分更新为新的 Token 生成方式说明:
  ```
  Authorization: Bearer {ak}:{timestamp}:{signature}
  signature = Base64(HMAC-SHA256(sk, ak + timestamp))
  ```

### 3.3 类型定义

**文件**: `frontend/src/types/openApp.ts`（新建）

```typescript
export interface OpenAppVO {
  id: number
  code: string
  name: string
  status: string
  accessKey: string | null
  allowedScopes: string
  remark: string
  akskGeneratedAt: string | null
}

export interface GenerateAkSkResult {
  accessKey: string
  secretKey: string
}
```

---

## 4. 改动文件清单

| 操作 | 文件路径 |
|------|----------|
| 新建 | `backend/.../openapi/support/OpenApiCrypto.java` |
| 新建 | `backend/.../openapi/service/OpenAppAdminService.java` |
| 新建 | `backend/.../openapi/controller/OpenAppAdminController.java` |
| 新建 | `backend/.../openapi/dto/OpenAppVO.java` |
| 新建 | `backend/.../openapi/dto/GenerateAkSkResult.java` |
| 新建 | `backend/.../resources/db/patch_open_app_aksk.sql` |
| 修改 | `backend/.../openapi/domain/entity/OpenAppEntity.java` — 加字段 |
| 修改 | `backend/.../openapi/service/OpenApiAuthService.java` — 改 authenticate() |
| 修改 | `backend/.../openapi/config/OpenAppSchemaInitializer.java` — 加 SQL 脚本 |
| 新建 | `frontend/src/api/openApp.ts` |
| 新建 | `frontend/src/types/openApp.ts` |
| 修改 | `frontend/src/views/system/OpenApiCapabilities.vue` — 加 AK/SK 管理区块 |

---

## 5. 测试计划

- **单元测试**: `OpenApiCrypto` 的签名/验签/过期检查
- **单元测试**: `OpenApiAuthService.authenticate()` 的新流程（合法 token、过期 token、无效签名、停用应用）
- **手动验证**: 前端生成 AK/SK → 用 SK 签名生成 token → 调用 `/open/v1/agents` 验证通过
