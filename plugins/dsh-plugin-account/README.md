# dsh-plugin-account

把 **DeepSeek Harness Web 的访问门禁**从「启动打印的 `?token=` URL 换 Cookie」改成**账号密码登录页**。
第一版按需求把凭据写死为 `admin` / `admin123`，改账号密码只需改配置。

- 插件类型：**宿主半（node 端）插件**，零依赖、纯 ESM，不需要客户端 bundle，**不需要重构建 web 产物**。
- 登录页由宿主直接渲染，浏览器在未登录时拿到的是登录表单而不是原来那句纯文本 `401`。

## 行为对照

| 场景 | 原生 DSH | 装本插件后（`allowLaunchToken: false`） |
|---|---|---|
| 裸访问 `http://127.0.0.1:<port>/` | 纯文本 `401` | `303` → `/account/login` 登录页 |
| 带 `?token=<启动令牌>` | 自动换 Cookie 并进入 | **仍跳登录页**（令牌不再自动登录） |
| 未登录访问 `/api/*` | `401` | `401` |
| 登录后访问 `/` 与 `/api/*` | 依赖 DSH 自己的 Cookie | 依赖本插件的签名会话 Cookie |
| 跨站/非信任 Host | 403 | 403（Host 栅栏优先，不会被会话覆盖） |

## 目录结构

```
dsh-plugin-account/
├── index.js                      # 宿主插件：路由 + 接管 authorizeIndex / requestRejection
├── lib/session.js                # HMAC-SHA256 签名会话 Cookie 与密钥持久化
├── lib/login-page.js             # 登录页 HTML（内联样式，无外部资源）
├── test/smoke.mjs                # 19 项宿主级冒烟断言
├── test/mount-path.mjs           # 5 项挂载前缀断言
├── scripts/install-profile.ps1   # 链接进 profile + 写入 bundles（可 -Remove 回滚）
├── cordis.patch.yml              # bundle patch：注册 account-login 行（含写死的账号密码）
└── package.json                  # dsh.bundle.patch 元数据
```

## 路由

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET`/`HEAD` | `/account/login` | 登录页；已登录时 303 到 `next` |
| `POST` | `/account/login` | 校验账号密码，成功签发会话 Cookie 并 303 |
| `GET`/`POST` | `/account/logout` | 清除会话 Cookie 并回登录页 |
| `GET`/`HEAD` | `/account/api/session` | 返回 `{ authenticated, user, launchTokenAllowed }`；登录时 `user` 为 `{ username, displayName }`，未登录为 `null` |

## 安装

> 本仓库已把该插件内置进 `@deepseek-ai/dsh-web-app` 组合包：Web 与 Desktop profile 会自动挂载 `account-login` 行，无需手动安装。不含 Web 界面的 headless / acp / sdk 组合不加载它。下面的方式保留给把插件当作独立 bundle 使用的场景。

### 方式一：作为 bundle（推荐）

```powershell
# 1. 放进 profile 的依赖（本地目录会被链接，改代码即可热生效）
dsh plugin --profile <profile> add D:\codex\deepseek-harness\packages\bundle\dsh-plugin-account

# 2. 让 profile 组合这个 bundle：编辑 <DSH_HOME>\profiles\<profile>\package.json
#    "dsh": { "profile": { "bundles": [ ..., "dsh-plugin-account" ] } }
```

`dsh-plugin-account` 自带 `dsh.bundle.patch`，被列入 `bundles` 后会把自己的 `cordis.patch.yml` 插进插件树。

### 方式二：直接写 profile 补丁

在 `<DSH_HOME>\profiles\<profile>\cordis.patch.yml` 里追加：

```yaml
- insert:
    - id: account-login
      name: dsh-plugin-account
      config:
        users:
          - username: admin
            password: admin123
        allowLaunchToken: false
```

profile 的 `package.json` 依赖与 `node_modules` 里仍需存在 `dsh-plugin-account`（`dsh plugin add <目录>` 或本地链接）。

> 生效方式：**改动 profile 配置（`package.json` 的 bundles 列表 / `cordis.patch.yml`）会被运行中的宿主热监听，2–4 秒内重组生效**；
> 但默认 `hmr.root: []` 不监听插件源码，**改 `index.js` / `lib/*` 之后需要重启宿主**（或在 profile patch 里给 `hmr` 行配置 `root: ["."]`）。

## 配置

| 字段 | 默认 | 说明 |
|---|---|---|
| `users` | `[{ username: 'admin', password: 'admin123' }]` | 账号列表；留空则用默认值 |
| `allowLaunchToken` | `false` | `true` 时启动令牌仍可自动登录（桌面端窗口免登录） |
| `sessionDays` | `7` | 会话有效期（天） |
| `sessionFile` | `<DSH_HOME>/plugins/dsh-plugin-account/session-secret` | 签名密钥文件；不可写时退化为进程内密钥 |
| `cookieName` | `dsh-account` | 会话 Cookie 名 |
| `title` | `登录 DeepSeek Harness` | 登录页标题 |
| `displayName` | `自定义账号` | 登录后账号页与顶栏菜单显示的名称；后续 HTTP 用户信息接口会覆盖它 |
| `maxAttempts` | `5` | 连续失败多少次后开始退避 |
| `lockoutSeconds` | `30` | 退避基准秒数（翻倍增长，上限 15 分钟） |
| `publicBasePath` | `''` | 反向代理挂载前缀（如 `--public-url https://host/ui/` 时填 `/ui`）：路由仍在根路径，所有跳转与表单 action 会带上该前缀 |

## 安全说明

- 会话 Cookie：`HttpOnly`、`SameSite=Strict`、`Path=/`，HTTPS 监听时带 `Secure`；绑定请求的 `Host`，换 authority 无法重放。
- 签名：HMAC-SHA256，密钥 32 字节随机，首次以独占方式创建并持久化（`0600`，Windows 不生效）。
- 凭据比较：对每个配置项都做常量时间摘要比较，不因账号不存在而改变耗时。
- 提交校验：`Origin`/`Sec-Fetch-Site` 不一致直接 403（登录与退出都校验）；登录请求体上限 16 KiB，超限 413（超大 body 会被读完丢弃，不会 RST）。
- **强制登录下插件会话是唯一凭据**（`allowLaunchToken: false`）：安装插件之前用启动令牌换到的原生 `dsh-auth-*` Cookie，在插件运行期间**不再放行 `/api`、RPC 通道、WebSocket 复用与 index**；`/account/logout` 会把它一并过期。
- 已知边界：服务仍靠 **loopback 监听**保护，明文 HTTP 下凭据在网络层可见；写死的密码只适合内网/临时使用，正式环境请把 `users` 接到真实账号体系。

## 已知边界

| 边界 | 说明 | 处理 |
|---|---|---|
| 会话无状态 | 改密码、删账号、在别处退出都**不会**让已签发 Cookie 失效 | 删除 `<DSH_HOME>/plugins/dsh-plugin-account/session-secret` 并重启宿主，全部会话立即失效 |
| 密钥文件 | 能读取密钥文件的本地用户即可伪造会话；`0600` 在 Windows 上不生效 | 用文件系统权限保护 `<DSH_HOME>` |
| 卸载插件后 | 之前签发的原生 `dsh-auth-*` Cookie 若仍在 30 天有效期内会重新生效 | 卸载后重启宿主或清除该站 Cookie |
| 不经过 `connection` 的路由 | 与原生 DSH 一致，不受本门禁管辖：`/plugins/*` 客户端包、客户端 HMR SSE、`/oauth/callback`、GitHub webhook 路径、非 index 静态资源 | 如需收紧请另行处理 |
| 桌面端 | 主窗口与欢迎窗口都先通过原生令牌交换，失败时改由主进程打开宿主登录页完成握手；`allowLaunchToken: false` 不再打断桌面启动 | 会话过期/宿主重启后，HTTP 转发收到 401 会触发一次重登录并刷新主窗口 |
| 反向代理挂载 | 绝对路径跳转会跳出挂载前缀 | 配置 `publicBasePath: '/ui'` |

## 验证

```powershell
# 1. 宿主级冒烟（不启动 DSH）：19 项请求/鉴权断言
node test/smoke.mjs

# 2. 挂载前缀断言（反向代理 /ui/ 场景）：5 项
node test/mount-path.mjs

# 3. 真实 DSH 端到端
$env:DSH_HOME='<隔离的 home>'
node D:\codex\deepseek-harness\apps\cli\lib\bin.js --profile web --port 50899 --no-open
curl.exe -i http://127.0.0.1:50899/                                   # 303 -> /account/login
curl.exe -i -X POST -d 'username=admin&password=admin123' http://127.0.0.1:50899/account/login
curl.exe -i -H 'Cookie: dsh-account=...' http://127.0.0.1:50899/      # 200 + __DSH_BOOT__

# 4. 装进某个 profile 并让运行中的宿主热生效
pwsh -File scripts/install-profile.ps1 -ProfileDir "$env:DSH_PROFILE_DIR"
```

## 回滚

1. 删除 profile `package.json` 里 `dsh.profile.bundles` 中的 `dsh-plugin-account` 行（或删掉 `cordis.patch.yml` 里的 insert 行）；
2. 恢复改动前的 `cordis.patch.yml.bak-<时间戳>`；
3. 重新加载/重启宿主。插件 dispose 会把 `authorizeIndex`、`requestRejection` 还原成原生方法。
