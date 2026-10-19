# 访问门禁、账号与模型一体化定制设计

> 状态：已实施（P1–P4 完成，P5 收尾中）。本文保留为设计与升级维护基线；实现细节见第 5 节改动点清单。
> 本文把 `login-gate-analysis-design.md` 的门禁分析，与桌面客户端设置页「账号 / 模型」定制方案合并为一份整合设计；后者的结论在本文发布后以此为准。

## Summary

本文设计 DeepSeek Harness 桌面客户端与 Web 客户端的三个相邻改造：用 admin/admin123 登录门禁替换启动令牌门禁；把设置页「账号与余额」替换为只显示自定义账号信息；把默认模型从 DeepSeek 账号路由替换为内置自定义模型，保留官方 DeepSeek API Key 路由与第三方模型接入，并移除 DeepSeek 账号计费路由。本文同时给出每个改动点，作为后续 deepseek-harness 版本升级后重新同步的依据。全部改造先以可回滚的 profile patch 停用，不删除现有代码。

## 目录

- [1. 目标与范围](#1-目标与范围)
- [2. 现状实现与依赖](#2-现状实现与依赖)
- [3. 目标态设计](#3-目标态设计)
- [4. 安全设计](#4-安全设计)
- [5. 改动点清单与升级维护](#5-改动点清单与升级维护)
- [6. 实施阶段](#6-实施阶段)
- [7. 验收标准](#7-验收标准)
- [8. 待办与后续](#8-待办与后续)
- [9. 升级迁移 runbook](#9-升级迁移-runbook)
- [Dev Note](#dev-note)

## 1. 目标与范围

本设计覆盖三块能力，顺序为「先进门、再定账号、再定模型」：

1. **访问门禁（L1）**：用本地账号密码登录页替换 `?token=` 启动令牌自动登录，并让 Electron 桌面主窗口和欢迎窗口都通过该门禁完成鉴权。
2. **账号模块**：设置页只显示自定义账号信息，移除 DeepSeek 官方账号的资料、余额、赠金、充值、额度提示与登录/登出。第一阶段只显示一个写死的名称，后续切换为真实 HTTP 登录接口与用户信息接口。
3. **模型模块**：内置一个自定义模型作为默认模型，保留自由接入第三方模型的能力，移除 DeepSeek 账号计费路由，保留官方 DeepSeek API Key 路由。

明确边界：

- 移除 `deepseek-account`（账号计费路由），保留 `deepseek-official`（官方 API Key 路由）。
- 第三方模型保留「目录 provider 采纳」与「自定义路由」两种入口。
- 先做可回滚停用，不删除代码；删除与收敛留到后续独立阶段。

## 2. 现状实现与依赖

### 2.1 访问门禁（L1）

原生门禁由 `packages/client/connection` 实现：

- `src/browser-auth.ts` 的 `BrowserAuth` 在进程启动时生成一次性 `launchToken`，拼进 `http://127.0.0.1:<port>/?token=…`。`authorizeIndex` 在根路径带正确 token 时签发 `dsh-auth-<sha256(authority)>` Cookie 并 `303 → ./`，带有效 Cookie 则放行 index，否则 `401`。
- `src/rpc-host.ts` 的 `requestRejection` / `admit` 先做 Host/Origin 栅栏（403），再验 Cookie（401）；`/api`、WebSocket mux（`/api/remote.mux`）、`open-in-app` 路由都经过它。

当前插件 `packages/bundle/dsh-plugin-account` 接管 `connection` 服务的两个方法：

- `index.js` 的 `installGate` 保存并替换 `connection.authorizeIndex` 与 `connection.requestRejection`，dispose 时用 `delete` 恢复原型方法，避免热重载叠加包装。
- 注册路由 `/account/login`（GET/POST）、`/account/logout`、`/account/api/session`。
- 会话 Cookie 为 HMAC-SHA256 签名的 `v1.<body>.<hmac>`（`dsh-account`），绑定请求 `Host`，`HttpOnly/SameSite=Strict/Path=/`，失败退避与 16 KiB body 上限。
- 配置集中在 `cordis.patch.yml`，默认 `admin/admin123`、`allowLaunchToken: false`。

已知缺口：`allowLaunchToken: false` 时，桌面壳 `authenticateWebHost` 期待的「303 + set-cookie」不再出现，桌面端启动直接失败。

### 2.2 DeepSeek 账号链路（L2，目标态移除）

官方账号体系由以下部分组成：

- `packages/credentials/deepseek-account`（服务定义）与 `packages/credentials/deepseek-account-platform`（OAuth 实现）：PKCE、`/oauth/callback`、grant 落盘、profile/balance/bonus 查询、logout。
- `packages/api/account-controller`：`account/*` RPC，包括 `watch`、`getProfile`、`getBalance`、`getUnnotifiedBonuses`、`startSignIn`、`cancelSignIn`、`hasRunningAccountTasks`、`signOut`。
- `packages/llm/llm-deepseek-account`：`deepseek-account` 账号计费路由。
- `packages/client/ui-settings-account`（仅桌面渲染进程）：`AccountSection`（资料、余额、赠金、用量、充值入口）、`AccountMenu`（顶栏账号菜单）、`AccountQuotaNotice`、`AccountOnboarding`（注入 `settings.models.sign-in`）、`DesktopOnboarding`。
- `packages/web/web-search-deepseek` 的账号鉴权分支，以及 `product-analytics` 对账号设备身份的读取。

账号分区当前只在 `AccountView.status === 'credential-stored'` 时注册；未登录显示 SignIn 引导卡片。因此「仅显示自定义账号信息」必须替换 `AccountView` 状态机和整个 `remote.account` 依赖，不能只改文案。

### 2.3 模型与 provider 注册表

Host 侧唯一事实源是 `ctx.llm` 注册表（`packages/llm/llm/src/types.ts`）：

- `listProviders()` 返回已注册路由；`listConfigurableProviders()` 返回可配置 provider 目录；`session.modelCatalog()`（`packages/api/session-controller/src/catalog.ts`）把注册表投影为浏览器模型目录，模型下拉按 provider 分组显示。
- `packages/llm/llm-deepseek-api-key` 注册 `deepseek-official`（官方 API Key 路由，展示名 DeepSeek）。
- `packages/llm/llm-deepseek-account` 注册 `deepseek-account`（账号计费路由，展示名 DeepSeek Account）。
- `packages/llm/llm-pi-ai` 是多 provider 引擎：支持已内置目录 provider，也支持手写自定义路由（`baseURL`、`api` 协议、`apiKeyEnv`、`models` 列表）。

默认模型由 `packages/bundle/base/cordis.patch.yml` 的 `agent-default-model` 指定为 `provider: deepseek-official, model: deepseek-flash`。

模型 UI 对 DeepSeek 有硬编码特判：

- `packages/client/ui-settings-models/src/client/store.ts`：`joinProviderDirectory` 把 `deepseek-account` 排第一、`deepseek-official` 排第二，并单独查询 `deepseek-account` 分组；`onboardingReadiness` 以 `deepseek-official` 为提示目标。
- `packages/client/ui-settings-models/src/client/ModelsSection.tsx`：把 `deepseek-account` 行展示名改为本地化文案。
- `packages/client/ui-model-selection/src/client/provider-order.ts`：同样把两条 DeepSeek 路由置顶。

### 2.4 桌面壳的两条鉴权通道

- 主窗口：`apps/desktop/src/web-document.ts` 的 `authenticateWebHost(url)` 用令牌 URL 换 Cookie 存为 `hostCookie`，`forwardWebRequest` 把该 Cookie 附加到转发请求；`apps/desktop/src/main.ts` 在 WebSocket 拦截器里做同样附加。
- 欢迎窗口：`apps/desktop/src/welcome-backend.ts` 的 `connectDesktopWelcome` 用 Electron `net.fetch`（默认会话 Cookie 罐）先访问带 token 的 URL，再走 RPC。

两条通道都依赖「启动令牌 URL 本身能鉴权」，所以 `allowLaunchToken: false` 会同时打断它们。

## 3. 目标态设计

### 3.1 访问门禁（admin/admin123 + 桌面闭环）

沿用当前插件的 P0 monkey-patch 方案，把「保存/恢复原方法」保持幂等、可重入，并补齐桌面端缺口：

- Web 端：裸访问 `/` 被 `303` 到登录页，admin/admin123 登录后拿到 `dsh-account` Cookie，`/api` 与 WebSocket 全通；登出后会话失效；`?token=` 不再自动放行。
- 桌面主窗口：`authenticateWebHost` 失败后改走本地登录握手，把 admin/admin123 POST 到宿主 `/account/login`，从 303 响应的 `set-cookie` 截取 `dsh-account` Cookie 存入 `hostCookie`，供 `forwardWebRequest` 与 WebSocket 转发继续使用。涉及 `apps/desktop/src/web-document.ts`、`apps/desktop/src/main.ts` 和一个本地登录页面/preload。
- 桌面欢迎窗口：`connectDesktopWelcome` 改为显式携带该 Cookie，不再依赖启动令牌自动登录。涉及 `apps/desktop/src/welcome-backend.ts`。

回滚方式：卸载该 bundle 或恢复 profile patch 后，原生 `dsh-auth-*` 流程恢复。

### 3.2 账号模块（自定义账号信息）

数据源统一为门禁会话接口 `/account/api/session`，把它的 `user` 字段从纯用户名字符串扩展为 `{ username, displayName? }` 对象（这是一次接口字段变更，登录门禁与账号 UI 在同一改动内同步）：

- `packages/client/ui-settings-account` 改为配置驱动的自定义账号卡片：第一阶段只显示一个写死的 `displayName`，移除资料、余额、赠金、充值、额度提示、登录/登出。账号卡片与菜单通过请求 `/account/api/session` 读取该字段；桌面端请求经主进程 `forwardWebRequest` 注入门禁 Cookie，Web 端由浏览器同源 Cookie 携带。
- 顶栏 `AccountMenu` 与设置页账号卡片读取同一个会话用户字段，显示一致的 `displayName`。
- 停用 `account-controller`、`deepseek-account` / `deepseek-account-platform`、`shell.quota-notice` 的账号实现、`settings.models.sign-in` 的账号注入、桌面 onboarding 的账号分支。
- 后续真实 HTTP 登录：门禁插件增加 `auth.mode: local | http` 与 `http.loginUrl`、`http.userInfoUrl` 配置；会话载荷扩展 `displayName`，账号页与菜单持续读同一字段，切换时只改数据来源不改 UI 契约。

### 3.3 模型模块（内置自定义模型 + 第三方 + 保留官方 API Key）

- 停用 `llm-deepseek-account`，移除 `deepseek-account` 路由。
- 保留 `deepseek-official`（`llm-deepseek-api-key`）。
- 内置自定义模型复用 `llm-pi-ai` 的手写路由能力，在 `packages/bundle/base/cordis.patch.yml` 给 `llm-pi-ai` 增加一个品牌化 provider，例如：

```yaml
- id: llm-pi-ai
  name: '@deepseek-ai/dsh-llm-pi-ai'
  config:
    providers:
      your-company:
        displayName: Your Company Model
        apiKeyEnv: YOUR_COMPANY_API_KEY
        api: openai-completions
        baseURL: https://your-gateway.example/v1
        models:
          - id: your-model
            name: Your Model
            contextWindow: 131072
            maxTokens: 8192
```

- 把 `agent-default-model` 的 `provider` / `model` 指向该内置路由。
- 第三方模型保留「目录 provider 采纳」与「自定义路由」两种入口，不裁剪 `llm-pi-ai` 能力。
- 清理 UI 特判：`ui-settings-models` 的排序与 `deepseek-account` 查询、`ModelsSection` 的展示名覆盖、`ui-model-selection` 的排序、`session-controller` 里 `hasProviderApiKey` 对 `deepseek-account` 的跳过。

### 3.4 后续 HTTP 登录与用户信息接口预留

第一阶段账号信息写死，但把数据来源收敛到 `/account/api/session`，为后续 HTTP 登录预留扩展点：

- 门禁插件新增 `auth` 配置块，`local` 模式沿用 admin/admin123；`http` 模式把 `/account/login` 的凭据校验替换为调用 `http.loginUrl`，并把 `http.userInfoUrl` 返回的 `displayName` 写入会话载荷。
- 账号页与账号菜单只依赖会话载荷，不感知校验是本地还是 HTTP。

### 3.5 桌面启动鲁棒性与失败路径

P0 桌面闭环必须满足以下顺序与失败处理，否则会在启动阶段卡死或白屏。

启动时序不变量：

1. `host.start()` 成功后才可用 `ready.url`。
2. 先取得 `hostCookie`（原生令牌交换或登录窗口），再 `connectDesktopWelcome`；两者共用 `session.defaultSession` 的 Cookie 罐。
3. 登录窗口关闭但未登录视为启动失败：`backend.start()` 拒绝并走既有 `reportFatal` / 启动失败提示，不允许静默进入主窗口。
4. 主窗口 `DESKTOP_IPC.boot` 已 `await startup`，所以登录必须在 `startup` resolve 前完成；登录页由宿主渲染，不依赖应用已启动。

登录窗口与 Cookie 捕获：

- 登录窗口直接加载 `new URL('/account/login', hostUrl)`，提交后宿主的 Set-Cookie 写入 `session.defaultSession`。
- 在 `webRequest.onHeadersReceived` 或 `cookies.on('changed')` 捕获 `dsh-account`，或提交后 `cookies.get({ url: hostUrl, name: 'dsh-account' })` 读取；捕获成功即关闭窗口，避免登录后的 303 把完整应用加载进登录窗口。
- `hostCookie` 取 `name=value` 形式，与 `forwardWebRequest` / WebSocket 拦截器现有格式一致。

会话过期与主机重启：

- `forwardWebRequest` 与 WebSocket 拦截器在发现宿主返回 401 时触发一次重登录窗口，而不是让应用静默失效；重登录成功刷新 `hostCookie` 后继续。
- 宿主热重载/重启导致会话密钥变化（进程内降级密钥）时，同样走重登录。

`allowLaunchToken` 与 `publicBasePath`：

- `allowLaunchToken: true` 时原生令牌交换仍成功，桌面端跳过登录窗口，保持现有免登录路径。
- 桌面直连 loopback 时 `publicBasePath` 应为空；若部署设置了挂载前缀，登录 URL 必须带前缀，否则登录窗口 404。

账号链路停用的原子性（最容易把桌面拖死的一处）：

- `packages/client/product-analytics` 硬注入 `deepseekAccount` 并调用 `getDeviceIdentity()`：停用账号服务前必须先改为不注入该服务、回退 `anonymous-user-id`，否则产品埋点插件加载失败。
- `apps/desktop/src/welcome-backend.ts` 的 `read()` 调用 `account.state()`，`account-backend.ts` 走 `account/*` RPC：必须先移除/重写这些调用，再停用 `account-controller`。
- `ui-settings-account` 注入 `remote.account`：先重写为读 `/account/api/session`，再停用 `account-controller`。
- 上述三者与账号链路停用必须同一发布内原子落地（或 UI / 欢迎后端改造在前）。

模型默认模型的原子性：

- 内置 provider 与 `agent-default-model` 的 provider/model 必须同时生效；先改默认模型、后注册 provider（或反之）都会让「初始化默认模型」指向不存在的路由，造成启动或首轮对话失败。

## 4. 安全设计

### 4.1 门禁会话安全

门禁沿用当前插件的安全属性：HMAC-SHA256 会话 Cookie、`HttpOnly/SameSite=Strict/Path=/`、Host 绑定防跨 authority 重放、登录/登出同源校验防 CSRF、常量时间凭据比较、失败退避、16 KiB body 上限、签名密钥独占创建持久化（不可写时降级进程内密钥并告警）。

桌面握手不把 `dsh-account` Cookie 交给渲染进程：主进程持有 `hostCookie` 并注入转发头，与现有 `forwardWebRequest` 的 `set-cookie` 拦截策略一致。

### 4.2 API Key 生命周期与泄露面

内置自定义模型走 `llm-pi-ai` 不引入新的密钥泄露面，生命周期与官方 DeepSeek API Key 完全一致：

- 密钥只通过 `credentials.set(ref, value)` 写入本地凭据库（`credentials-local`，受管文档），设置文档只保存 `apiKeyEnv` 引用名，不保存明文。
- 设置镜像以 `redactSecrets: true` 读取，模型页只拿到「是否已配置」状态，不拿值。
- `llm-pi-ai` 每请求通过 `credentials.resolve(ref)` 解析密钥，只发送给该 provider 配置的 endpoint；密钥不进入模型上下文、不进会话日志、不进 settings 描述。
- 模型页密钥输入框为 password 类型，提交走 `credentials` RPC。

两条必须遵守的约束：

1. 内置 provider 必须显式配置 `apiKeyEnv`，绝不能省略。省略会让 `llm-pi-ai` 回退到 pi-ai 自身的环境发现，可能捡到 `OPENAI_API_KEY` 等环境变量，造成串租户；已命名但缺失时 `llm-pi-ai` 会 fail loud（`MISSING_CREDENTIAL`），这是期望行为。
2. 内置 provider 的 `baseURL` 必须是可信的 HTTPS 网关。密钥会发送到该网关，与「官方 API Key 发送到 api.deepseek.com」是同类风险，只是接收方换成部署方指定的服务。

剩余风险是既有产品姿态：密钥以明文保存在本地凭据库中，与官方 DeepSeek API Key 同库同权限。后续可选 OS keychain，但不在本设计范围。

## 5. 改动点清单与升级维护

下表是升级 deepseek-harness 后需要逐个复核的改动点。升级流程：先 `git fetch` 对比这些文件，再按「升级时重验」列跑对应门禁。

| 层 | 文件 / 包 | 现状依赖 | 目标改动 | 升级时重验 | 回滚 |
| --- | --- | --- | --- | --- | --- |
| 门禁 patch 点 | `packages/client/connection/src/browser-auth.ts`、`src/rpc-host.ts` | `connection.authorizeIndex` / `requestRejection` 原型方法 | 插件 monkey-patch 保持幂等、可重入 | 检查方法签名未变；跑 `packages/client/connection` 测试 | 卸载插件恢复原型 |
| 门禁路由与会话 | `packages/bundle/dsh-plugin-account/index.js`、`lib/*` | `webServer.register` exact 路由、HMAC Cookie | 保留并扩展 `/account/api/session` 用户载荷 | 跑 `test/smoke.mjs`、`test/mount-path.mjs` | 卸载 bundle |
| 桌面主窗口握手 | `apps/desktop/src/web-document.ts`、`src/main.ts` | `authenticateWebHost` 期待 303 + set-cookie | 失败后改走登录 POST 握手，Cookie 仍由主进程持有 | 跑桌面登录/转发相关测试与冒烟 | 恢复原生令牌流程 |
| 运行时重登录 | `apps/desktop/src/web-document.ts` 的 `forwardWebRequest`、`src/main.ts` | 转发请求携带 `hostCookie` | 宿主返回 401 时触发重登录并刷新主窗口 | 跑 `web-document` / `main-startup` 测试 | 移除 401 回调 |
| 桌面欢迎窗口握手 | `apps/desktop/src/welcome-backend.ts` | `connectDesktopWelcome` 走 Cookie 罐 | 显式携带门禁 Cookie | 跑 `desktop-onboarding.e2e.ts` | 恢复原生令牌流程 |
| 账号 RPC | `packages/api/account-controller` | `account/*` 远程 | profile patch 停用 | 无 | 恢复 bundle 行 |
| 账号服务 | `packages/credentials/deepseek-account`、`deepseek-account-platform` | OAuth / grant / profile / balance | profile patch 停用 | 无 | 恢复 bundle 行 |
| 账号计费路由 | `packages/llm/llm-deepseek-account` | `deepseek-account` provider | profile patch 停用 | 无 | 恢复 bundle 行 |
| 账号 UI | `packages/client/ui-settings-account` | `AccountSection` 等 | 重写为自定义账号卡片，菜单同步 | 跑 `ui-settings-account` 测试与快照 | 恢复旧版 |
| 网页搜索账号分支 | `packages/web/web-search-deepseek` | `ctx.get('deepseekAccount')?.resolveToken` 与 `deepseek-account` provider 分支 | 移除账号分支，只走 API Key | 跑 `web-search-deepseek` 测试 | 恢复分支 |
| 欢迎窗口与账号后端 | `apps/desktop/src/welcome-backend.ts`、`src/account-backend.ts`、`preload-platform-account`、`src/main.ts` 的 `platformView` | `account/*` RPC、OAuth 欢迎流、Platform 内嵌页 | 移除账号 RPC/OAuth 分支，欢迎页只留 API Key/语言/分析 | 跑桌面 onboarding 相关测试与启动冒烟 | 恢复旧版 |
| 默认模型 | `packages/bundle/base/cordis.patch.yml` 的 `agent-default-model` | `deepseek-official/deepseek-flash` | 指向内置 provider/model | 跑模型目录与默认模型快照 | 改回 |
| 内置 provider | `packages/bundle/base/cordis.patch.yml` 的 `llm-pi-ai` config | 空 providers | 增加品牌化路由 | 跑 `llm-pi-ai` 测试 + 模型页冒烟 | 删除 config |
| 模型 UI 特判 | `ui-settings-models`、`ui-model-selection`、`session-controller` | `deepseek-account` / `deepseek-official` 排序与 readiness | 清理 `deepseek-account` 特判，保留官方路由 | 跑对应测试与快照 | 恢复特判 |
| 凭据 | `packages/credentials/credentials`、`credentials-local`、`remote.credentials` | `set/describe/unset` | 不改，复用 | 无 | - |
| 产品埋点身份 | `packages/client/product-analytics` | 硬注入 `deepseekAccount` 并读 `getDeviceIdentity()` | 改为不注入该服务，回退 `anonymous-user-id` | 跑 `product-analytics` 测试 | 恢复旧版 |

升级后固定回归集合（按本次改动面选择，不跑全量）：`pnpm run typecheck`、`pnpm run lint`、`pnpm run test:docs`，以及上述受影响包的聚焦测试与快照；涉及产品可见 UI 的改动要补真实 GUI 冒烟或 GIF 证据。

## 6. 实施阶段

按可回滚顺序推进：

> 术语说明：本节 P0–P5 是实施阶段编号；门禁接缝选型里的「P0 = monkey-patch、P1 = 正式扩展点」沿用 `login-gate-analysis-design.md` 的命名。本阶段「P1 门禁」即接缝选型 P0（monkey-patch）。

1. **P0 基线**：落地本设计文档，冻结范围。
2. **P1 门禁**：重构 `dsh-plugin-account` 并补齐桌面主窗口 / 欢迎窗口握手；先 Web 端闭环，再桌面端闭环。全程可用 profile patch 卸载回滚。
3. **P3 账号模块（先做）**：把 `ui-settings-account` 改为自定义账号卡片，账号菜单同步；移除 `welcome-backend` / `account-backend` 对 `account/*` 与 OAuth 的调用。
4. **P2 停用账号链路（后做）**：确认无消费者后，profile patch 停用 `deepseek-account`、`deepseek-account-platform`、`account-controller`、`llm-deepseek-account` 相关行，保留代码。
5. **P4 模型模块**：给 `llm-pi-ai` 加内置自定义 provider，改 `agent-default-model`，清理 UI 对 `deepseek-account` 的特判，保留 `deepseek-official` 与第三方两种入口。
6. **P5 收敛**：更新测试、快照、README 与升级预案，核对所有改动点已在上表登记。

P2/P3 必须同一发布内原子落地（或 P3 在前）；任何中间态都会让桌面欢迎窗口或设置页调用不存在的 `account/*`，导致启动或渲染失败。

## 7. 验收标准

- 门禁：未登录访问根路径被 303 到登录页；错误密码被拒；admin/admin123 登录成功；登录后 `/api` 与 WebSocket 正常；登出后会话失效；`?token=` 不再自动放行；桌面主窗口与欢迎窗口均通过门禁完成鉴权。
- 账号：设置页账号分区只显示自定义账号名称，不再出现 DeepSeek 账号登录、资料、余额、赠金、充值、额度提示；顶栏账号菜单与设置页显示同一名称。
- 模型：内置自定义模型出现在模型页与模型下拉且为默认；第三方「目录 provider + 自定义路由」均可新增并完成推理；`deepseek-account` 路由从模型页与模型下拉消失；`deepseek-official` 官方 API Key 路由仍可录入并推理。
- 安全：跨 authority Cookie 重放被拒；CSRF 被拒；连续失败触发退避；API Key 不进入模型上下文、会话日志与 settings 描述；内置 provider 的密钥只保存在本地凭据库。
- 回滚：卸载插件或恢复 profile patch 后，门禁、账号、模型恢复为改造前行为；无残留路由或包装。

## 8. 待办与后续

- 明确 HTTP 登录接口契约：`http.loginUrl` 的请求/响应格式、`http.userInfoUrl` 返回的用户字段（`displayName` 至少，后续 `org`、`avatar` 等），以及失败与超时策略。
- 门禁接缝正式化（原设计文档的 P1 方案）列为后续待办：先跑通 P0 monkey-patch，业务稳定后再单独 PR 落地。届时在 A（新增 `connection/authorize-index` 与 `connection/request-rejection` 两个同步 waterfall 事件，推荐）和 B（独立 `ConnectionAuthenticator` Service）之间选型。评估结论：改动面是 `client-connection` 核心包、`docs/event-producer-consumer.md` 事件表和 host 测试，不涉及 SessionEventMap 与 TS/Python SDK 快照。
- 可选：把 API Key 存储升级为 OS keychain。

## 9. 升级迁移 runbook

本地定制分三类：本地新增（`packages/bundle/dsh-plugin-account`）、profile 装配（`packages/bundle/base` / `web-app` 的 cordis.patch.yml、桌面 profile）、对上游源码的侵入修改（`apps/desktop`、`ui-settings-*` / `ui-model-selection` / `session-controller` 等）。升级 = 在新版本上重放这些侵入修改并回归，不是从头重写。

迁移步骤：

1. 取新版：`git fetch origin`，对比第 5 节表格里每个上游文件的 diff，先看四个隐性契约是否变化。
2. 重放本地补丁：本地新增直接保留；profile 装配按新 bundle 结构合并；侵入修改逐个 rebase/重应用，冲突按语义解决。
3. 按从小到大顺序回归：`pnpm run typecheck` → `pnpm run lint` → `pnpm run test:docs` → 插件 `node test/smoke.mjs`、`node test/mount-path.mjs` → `client-connection` host 测试（若做 P1 或确认 monkey-patch 目标未变）→ `llm-pi-ai` / `llm-deepseek-api-key` / `llm-deepseek-account` 相关测试 → `ui-settings-account` / `ui-settings-models` / `ui-model-selection` 测试与快照 → 桌面 `authenticateWebHost` / `forwardWebRequest` / `connectDesktopWelcome` 相关测试与启动冒烟 → `dsh web` 真实端到端冒烟与相关 e2e（`desktop-onboarding`、`bonus-notice`、`cot-translation-paid` 等）。
4. 核验四个隐性契约：`connection.authorizeIndex` / `requestRejection` 签名与 403 栅栏位置；`authenticateWebHost` / `forwardWebRequest` / `connectDesktopWelcome` 签名与 Cookie 契约；`llm-pi-ai` 的 `providers` schema、`registerConfigurableProviders` 与 `agent-default-model` 配置；`settings.section` / `settings.models.*` 槽位、`session.modelCatalog()` 分组结构、`remote.credentials` / `remote.llm` / `remote.account` 命名空间。
5. 更新受影响快照与文档，跑 `pnpm run test:docs`、`pnpm run doc-sync`，再按第 7 节验收。
6. 回滚：每步保留可恢复基线；任何一步红即回退到上一版本并记录 diff。

降低迁移成本的长期手段：把 P0 monkey-patch 升级为 P1 正式扩展点（减少对 `client-connection` 内部方法签名的依赖）；本地侵入修改保持最小化并在第 5 节登记；每个上游文件改动旁留注释指向本设计文档。

## Dev Note

本节是非权威的临时备注，不构成实现约束。内置 provider 的 `api` 协议取值以 `llm-pi-ai` 当前 `supportedProtocols()` 为准，落地前需按目标网关实际协议确认；`agent-default-model` 的 model id 必须与内置 provider 的 `models[].id` 一致，否则默认模型解析失败。
