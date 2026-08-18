# 云起应用平台

一套面向企业数字化应用建设的模块化开发基础平台，简称 **YQAP**（YunQi Application Platform）。前端基于 **Vue 3 + TypeScript + Element Plus**，通过统一技术架构、业务组件、AI 能力与行业扩展能力，帮助企业快速构建智能化应用系统。

[License: MIT](../LICENSE)
[Vue](https://vuejs.org/)
[TypeScript](https://www.typescriptlang.org/)
[Vite](https://vitejs.dev/)
[Element Plus](https://element-plus.org/)
[Node](https://nodejs.org/)

**当前版本**：v1.0.0（包名 `yunqi-application-platform`）

**在线演示**：[https://yunqi.datafuturex.cn](https://yunqi.datafuturex.cn)

## 开源源码

本项目与后端同属 **yunqi-application-platform** 单体仓库（monorepo），以 **MIT** 协议开源。默认分支：`main`（跟踪 `origin/main`）。本文档对应仓库内 **`frontend/`** 目录（前端源码）。

| 平台 | 仓库 | remote | 说明 |
|------|------|--------|------|
| GitHub | `git@github.com:DataFutureX/yunqi-application-platform.git` | `origin` | 主仓库 |
| Gitee | `git@gitee.com:DataFutureX/yunqi-application-platform.git` | `gitee` | 国内镜像 |

| 路径 | 内容说明 |
|------|----------|
| `frontend/` | 前端源码（本 README 所在目录） |
| `backend/` | 后端源码（Java / Spring Boot） |
| [`../README.md`](../README.md) | 仓库根目录：前后端快速开始（演示 / 联调） |

### 克隆

```bash
# GitHub（推荐 / origin）
git clone git@github.com:DataFutureX/yunqi-application-platform.git
cd yunqi-application-platform/frontend

# 或 Gitee
git clone git@gitee.com:DataFutureX/yunqi-application-platform.git
cd yunqi-application-platform/frontend
```

本地已同时配置双远程时，可按需推送：

```bash
git push origin main
git push gitee main
```

包名与产品名以 `frontend/package.json`（`yunqi-application-platform`）与本 README 为准。许可证全文见 [LICENSE](../LICENSE)。

## 目录

- [开源源码](#开源源码)
- [特性一览](#特性一览)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [演示模式](#演示模式)
- [在线演示](#在线演示)
- [项目结构](#项目结构)
- [架构说明](#架构说明)
- [配置说明](#配置说明)
- [常用命令](#常用命令)
- [部署](#部署)
- [开发规范](#开发规范)
- [故障排除](#故障排除)
- [相关文档](#相关文档)
- [License](#license)



## 特性一览


| 能力    | 说明                                                                |
| ----- | ----------------------------------------------------------------- |
| 动态权限  | 后端菜单驱动路由；按钮 `v-permission`；路由 `meta.permissions` 二次兜底，无权限进 `/403` |
| 安全登录  | 滑动验证码 + RSA 加密传输（演示与生产同一登录链路）                                     |
| 多布局主题 | 侧栏 / 混合布局，标签页或面包屑，多主题与内容密度                                        |
| 系统运维  | 公告、操作日志、运行监控                                                      |
| 演示模式  | `npm run dev:demo` 纯前端 Mock，无需后端即可完整体验                            |
| 工程化   | Vite 5、ESLint 9 flat、Prettier、Vitest、Husky + lint-staged          |




### 业务模块


| 模块     | 路径                      | 说明                |
| ------ | ----------------------- | ----------------- |
| 产品门户   | `/portal`               | 产品落地页             |
| 登录     | `/login`                | 滑动验证码 + RSA       |
| 仪表盘    | `/home/dashboard`       | 概览、公告、快捷入口        |
| 权限管理   | `/permission/*`         | 用户 / 单位 / 角色 / 菜单 |
| 系统设置   | `/system/config`        | 站点品牌与登录策略         |
| 公告管理   | `/system/announcement`  | 发布 / 撤回 / 已读      |
| 操作日志   | `/system/operation-log` | 审计查询              |
| 系统监控   | `/monitor/ops`          | 运行状态与健康检查         |
| API 文档 | `/devtools/api`         | 内嵌 Swagger        |
| 个人中心   | `/profile/*`            | 资料与改密             |
| 错误页    | `/403`、通配 404           | 无权访问 / 页面不存在      |


> 旧路径（如 `/system/user`）会重定向到 `/permission/user` 等新域。菜单与按钮权限对照见 [docs/MENU_ROUTES.json](docs/MENU_ROUTES.json)。



## 技术栈


| 类别  | 技术                                                                     |
| --- | ---------------------------------------------------------------------- |
| 框架  | Vue 3.5（Composition API + `<script setup>`）                            |
| 语言  | TypeScript 5.7（`strict`）                                               |
| 构建  | Vite 5.4 + `@vitejs/plugin-vue` 5                                      |
| UI  | Element Plus 2.9（unplugin 按需 + `el-config-provider` 中文）                |
| 状态  | Pinia 2.3（setup store）                                                 |
| 路由  | Vue Router 4.5（动态菜单注册 + 路由级权限）                                         |
| 请求  | Axios 1.8（解包业务 `data`、大整数安全、`skipErrorMessage`）                        |
| 其他  | NProgress、JSEncrypt、Sass 1.85                                          |
| 工程化 | ESLint 9、Prettier、Vitest 3、Husky、unplugin-auto-import / vue-components |


> Node **≥ 18**（`engines` 已声明）；推荐 **20 LTS**。升级 Vite 6 需 Node ≥ 20.19。



## 快速开始

> 前后端联调总览见仓库根目录 [README.md](../README.md)。

### 环境要求


| 依赖      | 版本                                      |
| ------- | --------------------------------------- |
| Node.js | >= 18（推荐 [20 LTS](https://nodejs.org/)） |
| npm     | >= 9                                    |
| 操作系统    | Windows 10/11、macOS、Linux               |




### 最快体验（演示模式，无需后端）

在 `frontend/` 目录下执行：

```bash
npm install
npm run dev:demo
```

浏览器打开 [http://localhost:3000](http://localhost:3000) ，推荐账号：`demo` / `demo123`（演示态任意账号密码均可登录）。

### 联调真实后端

1. 在 `.env.development` 配置后端地址：

```env
VITE_PORT=3000
VITE_API_BASE_URL=http://localhost:8080
```

1. 启动：

```bash
npm install
npm run dev
```

开发服务器监听 `0.0.0.0`：

- 本机：[http://localhost:3000](http://localhost:3000)
- 局域网：http://本机IP:3000



### 启动脚本（可选）


| 平台            | 命令            | 说明             |
| ------------- | ------------- | -------------- |
| Windows CMD   | `start.bat`   | 可选开发 / 演示模式    |
| PowerShell    | `.\start.ps1` | 含构建、Lint、清理重装等 |
| Linux / macOS | `./start.sh`  | 可选开发 / 演示模式    |




## 演示模式

```bash
npm run dev:demo      # 本地演示
npm run build:demo    # 打演示静态包
```

通过 `--mode demo` 加载 `.env.demo`（`VITE_DEMO_MODE=true`），Axios 挂载内置 Mock adapter：

- 覆盖登录、公钥、权限、CRUD、监控、审计等与正式接口对齐的行为
- 数据存于内存会话，刷新后回到初始 Mock
- 无后端时 Swagger 页展示空状态说明
- 业务代码走统一请求链路；Demo 特判集中在 `config/demo`、`mock/*`、`request` 挂载与登录提示



## 在线演示

无需本地安装，可直接访问线上环境：

**[https://yunqi.datafuturex.cn](https://yunqi.datafuturex.cn)**

## 项目结构

```
yunqi-application-platform/
├── backend/                 # 后端源码
└── frontend/                # 前端源码（本目录）
    ├── src/
    │   ├── api/                 # 接口封装（按业务模块）
    │   ├── assets/              # 静态资源
    │   ├── components/
    │   │   ├── auth/            # 滑动验证码
    │   │   ├── layout/          # 布局壳（侧栏、顶栏、标签页…）
    │   │   ├── list-page/       # 列表页壳（PageHero、筛选条…）
    │   │   ├── user|unit|menu/  # 业务表单弹窗（*FormDialog）
    │   │   └── common/          # DemoBanner 等
    │   ├── composables/         # useLogin / useUserList / usePermission …
    │   ├── config/              # 主题、演示模式、快捷入口
    │   ├── constants/           # 权限码等常量
    │   ├── directives/          # v-permission
    │   ├── layouts/             # MainLayout
    │   ├── mock/                # 演示 Mock（adapter / handler / data / state）
    │   ├── plugins/             # 页面过渡等
    │   ├── router/              # 静态路由、动态注册、路由单测
    │   ├── stores/              # Pinia（user / menu / layout / theme …）
    │   ├── styles/              # 全局 SCSS
    │   ├── types/               # TS 类型与 RouteMeta 扩展
    │   ├── utils/               # request、permission、RSA、登出…
    │   ├── views/
    │   │   ├── dashboard|login|portal|devtools|profile…
    │   │   ├── system/          # 配置、公告、日志、监控、菜单/单位/角色
    │   │   ├── user/            # 用户列表、资料、改密
    │   │   └── error/           # Forbidden / NotFound
    │   ├── App.vue              # el-config-provider（zh-CN）
    │   └── main.ts              # Pinia、指令、按需 EP、图标注册
    ├── docs/
    │   ├── MENU_ROUTES.json     # 菜单 / 路由 / BUTTON 权限对照
    │   └── GUIDE.md             # 指向本 README 的短说明
    ├── public/
    ├── .env*                    # 各环境变量
    ├── eslint.config.js         # ESLint 9 flat
    ├── vitest.config.ts
    ├── vite.config.ts
    ├── start.bat / .ps1 / .sh
    ├── test-unit.bat / .sh          # Vitest → docs/unit-test-report
    ├── test-e2e-demo.bat / .sh      # Playwright demo → docs/e2e-test-report
    ├── playwright.config.ts
    ├── e2e/
    └── README.md
```

> 仓库根目录另有权威 [LICENSE](../LICENSE)（MIT）。

路径别名：`@` → `src/`。

列表页推荐模式：`views/xxx` 只做编排 → `composables/useXxxList.ts` 承载逻辑 → `components/*/XxxFormDialog.vue` 承载表单。

## 架构说明

```
浏览器
  │  Token（localStorage）
  ▼
路由守卫
  ├─ 无 Token → /login
  ├─ 拉菜单 / 权限 → 动态注册 Layout 子路由
  └─ 校验 meta.permissions → 不通过则 /403
  │
  ▼
Axios（baseURL: /api/v1）
  ├─ 开发联调 → Vite 代理 → 后端
  └─ demo 模式 → mock adapter（惰性加载）
```

要点：

1. **鉴权**：`Authorization: Bearer <token>`；业务码 / HTTP `401` 统一登出（可用 `skipErrorMessage` 静默请求）
2. **菜单**：`GET /menus/current-user` 驱动侧栏与动态路由；组件路径如 `views/user/UserList.vue`
3. **按钮权限**：`GET /menus/current-user/permissions` + `v-permission` / `usePermission`
4. **路由权限**：菜单下 `BUTTON` 的 `routeName`（权限码）汇总进页面路由 `meta.permissions`；管理员绕过
5. **大整数**：响应拦截对超长整型字符串化，避免 JS 精度丢失
6. **保留路径**：`/login`、`/portal`、`/403`、`/404` 等不作为动态子路由注册



## 配置说明



### 环境文件


| 文件                 | 用途                                |
| ------------------ | --------------------------------- |
| `.env`             | 通用默认                              |
| `.env.development` | `npm run dev`                     |
| `.env.demo`        | `npm run dev:demo` / `build:demo` |
| `.env.production`  | `npm run build`                   |




### 变量一览


| 变量                  | 说明                 | 示例                      |
| ------------------- | ------------------ | ----------------------- |
| `VITE_PORT`         | 开发端口               | `3000`                  |
| `VITE_API_BASE_URL` | 后端基址（代理目标 / 生产拼接）  | `http://localhost:8080` |
| `VITE_DEMO_MODE`    | 是否启用 Mock          | `true`（仅 demo mode）     |
| `VITE_SWAGGER_URL`  | 覆盖默认 Swagger UI 地址 | 可选                      |


注意：

- 仅 `VITE_` 前缀会注入前端；修改后需重启 Vite
- 值不要加引号；不要把密钥写进仓库
- 本地覆盖可用 `.env.*.local`（已在 `.gitignore`）



### 开发代理

```typescript
// vite.config.ts（示意）
server: {
  host: '0.0.0.0',
  port: Number(env.VITE_PORT) || 3000,
  proxy: {
    '/api': { target: env.VITE_API_BASE_URL, changeOrigin: true },
    '/swagger-ui': { /* … */ },
    '/v3/api-docs': { /* … */ },
    '/webjars': { /* … */ },
  },
}
```

前端请求相对路径，例如 `/api/v1/users/page`（`baseURL` 为 `/api/v1`）。

## 常用命令

```bash
npm run dev          # 联调开发
npm run dev:demo     # 演示开发
npm run build        # 生产构建（先 vue-tsc）
npm run build:demo   # 演示包构建
npm run preview      # 预览 dist
npm run lint         # ESLint（含 --fix）
npm run format       # Prettier 格式化 src/
npm run type-check   # 仅类型检查
npm run test         # Vitest 单测（permission / dynamicRoutes / format）
npm run test:watch   # Vitest 监听模式
npm run test:e2e:demo         # Playwright 冒烟（演示模式，自动起 Vite）
npm run test:e2e:integration  # Playwright 冒烟（联调，需前后端已启动）
npm run test:e2e:report       # 打开 docs/e2e-test-report HTML 报告
```

一键脚本（在 `frontend/` 目录）：

| 脚本 | 说明 |
|------|------|
| `test-unit.bat` / `./test-unit.sh` | 跑 Vitest，报告 → [`docs/unit-test-report/`](../docs/unit-test-report/) |
| `test-e2e-demo.bat` / `./test-e2e-demo.sh` | 跑演示模式冒烟，报告 → [`docs/e2e-test-report/`](../docs/e2e-test-report/) |

提交前若已启用 Husky，会经 `lint-staged` 对暂存的 `src` 文件跑 ESLint + Prettier。

### 前端单元测试（Vitest）

```bash
# Windows
test-unit.bat
# Linux / macOS
chmod +x test-unit.sh && ./test-unit.sh
# 或
npm run test
```

HTML 报告：[`docs/unit-test-report/index.html`](../docs/unit-test-report/index.html)（每次覆盖）。

### 前端界面冒烟（Playwright）

覆盖门户、登录与登录后业务页（仪表盘、用户/单位/角色/菜单、系统设置、公告、操作日志、监控、API 文档、个人信息、修改密码）：页面可打开且关键 UI 可见。不含完整 CRUD。

首次或换机后需安装浏览器：

```bash
npx playwright install chromium
```

| 命令 | 环境 | 说明 |
|------|------|------|
| `npm run test:e2e:demo` 或 `test-e2e-demo.bat` | 演示 Mock | 自动 `vite --mode demo`；账号默认 `demo` / `demo123` |
| `npm run test:e2e:integration` | 前后端联调 | **不**自启前端；请先起后端与 `npm run dev`；账号默认 `admin` / `admin123` |

可选环境变量：`E2E_BASE_URL`（默认 `http://127.0.0.1:3000`）、`E2E_USERNAME`、`E2E_PASSWORD`。强制不启 webServer 时可设 `E2E_NO_WEBSERVER=1`。

报告目录：[`docs/e2e-test-report/`](../docs/e2e-test-report/)（已 gitignore）。失败时保留 trace / 截图。

## 部署



### 静态资源

```bash
npm run build
# 产物：dist/
```

这是 **SPA**：托管需回退到 `index.html`（History 路由），例如 Nginx：

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

生产环境请配置正确的 `VITE_API_BASE_URL`（构建期注入）。接口需允许前端域名 CORS，或同域反代 `/api`。

演示包：`npm run build:demo`。

## 开发规范

与仓库 Vue3 规范一致，摘要如下：


| 项     | 要求                                                              |
| ----- | --------------------------------------------------------------- |
| 组件    | 必须 `<script setup lang="ts">`，禁止 Options API                    |
| 类型    | 避免 `any`；API DTO / VO 在 `types/` 或 `api/` 定义                    |
| Props | 明确类型；变更通过 emit / `defineModel`                                  |
| 样式    | 优先 `scoped`；主题色走 CSS 变量                                         |
| 异步    | 请求需错误处理；组件卸载时清理定时器 / 监听                                         |
| 权限    | 按钮：`v-permission`；页面：路由 `meta.permissions`（BUTTON 自动汇总）→ `/403` |
| 命名    | 组件 PascalCase；composable `useXxx.ts`；Store `useXxxStore.ts`     |
| 页面拆分  | 列表逻辑进 `composables/useXxxList.ts`；表单弹窗独立组件                      |
| 错误页   | `/403`、通配 404（`views/error/*`）                                  |
| Demo  | 特判尽量留在 `config/demo`、`mock/*`、`request` 挂载；业务层统一走 API           |
| 图表    | 若接入 ECharts，按需从 `echarts/core` 引入（见 `src/utils/echarts.ts`）     |


完整约定：[.qoder/rules/vue3-code.md](.qoder/rules/vue3-code.md)（另有 [.cursor/rules/vue3-code.md](.cursor/rules/vue3-code.md)）

### 贡献建议

1. 从最新主干拉取分支
2. 提交前本地建议跑通：`npm run type-check && npm run lint && npm run test`
3. 新 CRUD：页面编排 + `useXxxList` + 表单弹窗；接口放 `src/api/`
4. 变更菜单结构时同步 `docs/MENU_ROUTES.json` 与演示 Mock，并核对：
  - `path` / `component` / `routeName` 与页面文件一致
  - `BUTTON` 节点的 `routeName` 为权限码（如 `system:user:query`）
  - 与 `src/constants/permissions.ts`、页面 `v-permission` 保持同步



## 故障排除

**Node.js / npm 未找到**

安装 [Node.js LTS](https://nodejs.org/) 后重启终端，执行 `node -v` / `npm -v` 验证。

**依赖安装失败**

```bash
npm cache clean --force
# PowerShell
Remove-Item -Recurse -Force node_modules, package-lock.json -ErrorAction SilentlyContinue
npm install
```

**端口被占用**

```bash
# Windows
netstat -ano | findstr :3000
taskkill /F /PID <PID>
```

或修改 `.env.development` 中 `VITE_PORT`。

**环境变量未生效**

确认使用了对应 mode 的 `.env.*`，变量以 `VITE_` 开头且无引号，修改后重启 Vite。

**浏览器打不开页面**

1. 看终端与控制台报错
2. 试 [http://127.0.0.1:3000](http://127.0.0.1:3000)
3. 检查防火墙；同网段设备访问 `http://<本机IP>:3000`

**Windows 下 bat 启动异常**

请使用仓库提供的 `start.bat`（内部已对 `npm.cmd` 使用 `call`）。勿在自定义 bat 中直接写 `npm` 而不加 `call`。

**已登录仍进 403**

检查该页菜单下 BUTTON 权限码是否已下发给当前角色，以及 `routeName` 是否与 `permissions` 一致。管理员角色会绕过路由权限校验。

**调试**

```bash
# Vite 详细日志（Unix / Git Bash）
DEBUG=vite:* npm run dev

npm list --depth=0
npm run type-check
npm run test
```



## 相关文档


| 文档                                                               | 说明                 |
| ---------------------------------------------------------------- | ------------------ |
| [仓库快速开始](../README.md) | 根目录 README：演示模式与前后端联调 |
| [在线演示](https://yunqi.datafuturex.cn) | 线上体验地址 |
| [GitHub 源码](https://github.com/DataFutureX/yunqi-application-platform) | `git@github.com:DataFutureX/yunqi-application-platform.git`（`origin` / `main`）；前端位于 `frontend/` |
| [Gitee 镜像](https://gitee.com/DataFutureX/yunqi-application-platform) | `git@gitee.com:DataFutureX/yunqi-application-platform.git`（`gitee`）；前端位于 `frontend/` |
| [docs/MENU_ROUTES.json](docs/MENU_ROUTES.json)                   | 菜单树与路由、按钮权限对照      |
| [docs/GUIDE.md](docs/GUIDE.md)                                   | 短说明（内容已并入本 README） |
| [LICENSE](../LICENSE)                                             | MIT 许可证全文（仓库根目录） |
| [.qoder/rules/vue3-code.md](.qoder/rules/vue3-code.md)           | Vue3 / TS 编码细则     |




## License

本项目采用 [MIT License](../LICENSE) 开源。源码路径见上文 [开源源码](#开源源码)。