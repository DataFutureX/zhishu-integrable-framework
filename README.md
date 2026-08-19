# 知枢可集成框架 · 快速开始

一套面向企业数字化与智能化应用集成的模块化开发底座（MIT），简称 **ZSIF**（ZhiShu Integrable Framework）。本工程基于 [云起应用平台](https://github.com/DataFutureX/yunqi-application-platform)（YQAP）构建，继承其前后端一体架构、权限体系与伙伴 SSO 能力，面向可集成、可扩展的应用场景二次演进。

**上游底座**：[云起应用平台](https://github.com/DataFutureX/yunqi-application-platform)（`upstream` 远程）

## 目录

- [界面一览](#界面一览)
- [两种体验路径](#两种体验路径)
- [路径 A：仅前端演示（无需后端）](#路径-a仅前端演示无需后端)
- [路径 B：前后端联调](#路径-b前后端联调)
- [默认账号](#默认账号)
- [联调验证清单](#联调验证清单)
- [技术栈一览](#技术栈一览)
- [工程结构说明](#工程结构说明)
- [常见问题](#常见问题)
- [下一步](#下一步)
- [许可证](#许可证)

## 界面一览

产品门户（`/portal`，按顶部导航分屏截取视口）：

| 首屏 | 开源 | 能力 | 技术栈 | 文档 |
|:---:|:---:|:---:|:---:|:---:|
| ![产品门户 · 首屏](./screenshot/01a-portal-hero.png) | ![产品门户 · 开源](./screenshot/01b-portal-opensource.png) | ![产品门户 · 能力](./screenshot/01c-portal-features.png) | ![产品门户 · 技术栈](./screenshot/01d-portal-stack.png) | ![产品门户 · 文档](./screenshot/01e-portal-docs.png) |

登录页：

![登录页](./screenshot/02-login.png)

登录后工作台：

| 仪表盘 | 用户管理 | 单位管理 | 角色管理 |
|:---:|:---:|:---:|:---:|
| ![仪表盘](./screenshot/03-dashboard.png) | ![用户管理](./screenshot/04-user.png) | ![单位管理](./screenshot/05-unit.png) | ![角色管理](./screenshot/06-role.png) |

| 菜单管理 | 系统设置 | 公告管理 | 操作日志 |
|:---:|:---:|:---:|:---:|
| ![菜单管理](./screenshot/07-menu.png) | ![系统设置](./screenshot/08-system-config.png) | ![公告管理](./screenshot/09-announcement.png) | ![操作日志](./screenshot/10-operation-log.png) |

| 系统监控 | API 文档 | 个人信息 | 修改密码 |
|:---:|:---:|:---:|:---:|
| ![系统监控](./screenshot/11-monitor.png) | ![API 文档](./screenshot/12-api-docs.png) | ![个人信息](./screenshot/13-profile.png) | ![修改密码](./screenshot/14-change-password.png) |

> 截图资源见 [`screenshot/`](./screenshot/)。门户分屏：`node scripts/capture-portal-sections.mjs`；其它页面：`node scripts/capture-demo-screenshots.mjs`。

## 两种体验路径

| 路径 | 适用场景 | 需要准备 |
|------|----------|----------|
| **A. 前端演示模式** | 快速浏览界面与交互，不接真实 API | Node.js ≥ 18、npm ≥ 9 |
| **B. 前后端联调** | 完整权限、持久化数据、Swagger 调试 | 另需 JDK 21、Maven 3.9+、PostgreSQL 14+ |

建议：先走路径 A 熟悉产品，再按路径 B 启动后端完成真实联调。

## 路径 A：仅前端演示（无需后端）

```bash
cd zhishu-integrable-framework/frontend
npm install
npm run dev:demo
```

浏览器打开 [http://localhost:3000](http://localhost:3000)（可先看 `/portal`，再登录）。

演示账号：`demo` / `demo123`（演示态任意账号密码均可登录）。

演示模式通过 `--mode demo` 加载 `.env.demo`（`VITE_DEMO_MODE=true`），Axios 挂载内置 Mock。

可选启动脚本（在 `frontend/` 目录）：`start.bat` / `.\start.ps1` / `./start.sh`。

## 路径 B：前后端联调

本仓库为前后端一体的 monorepo：

```text
zhishu-integrable-framework/
├── backend/     # Java 后端（继承 zhishu-* 模块结构）
├── frontend/    # Vue 前端
├── start.bat    # 根目录一键启动（Windows）
├── start.ps1    # 根目录一键启动（PowerShell）
├── start.sh     # 根目录一键启动（Linux / macOS）
└── screenshot/  # 界面截图
```

### 一键启动前后端（推荐）

确认已安装 JDK 21、Maven、Node.js，并完成下方「初始化数据库 / 修改数据源」后，在**仓库根目录**执行：

```bash
# Windows
start.bat
# 或
.\start.ps1

# Linux / macOS
chmod +x start.sh
./start.sh
```

| 地址 | 说明 |
|------|------|
| http://localhost:3000 | 前端 |
| http://localhost:8080 | 后端 |
| http://localhost:8080/swagger-ui.html | API 文档 |

### 1. 启动后端

#### 环境要求

- JDK **21+**
- Maven **3.9+**
- PostgreSQL **14+**

#### 初始化数据库

```bash
cd backend
psql -U postgres -c "CREATE DATABASE zhishu_integrable_framework WITH ENCODING 'UTF8' TEMPLATE template0;"
psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/init.sql
```

默认库名：`zhishu_integrable_framework`。

#### 修改数据源

编辑 `zhishu-core/src/main/resources/application-dev.yml`：

```yaml
yunqi:
  datasource:
    host: localhost
    port: 5432
    database: zhishu_integrable_framework
    username: postgres
    password: 你的密码
```

#### 启动服务

```bash
# Windows
start-dev.bat

# Linux / macOS
chmod +x start-dev.sh
./start-dev.sh

# 或 Maven
mvn -pl zhishu-core -am spring-boot:run -DskipTests
```

### 2. 启动前端（联调模式）

```bash
cd frontend
npm install
npm run dev
```

确认 `.env.development` 中 `VITE_API_BASE_URL=http://localhost:8080`。

联调默认管理员：`admin` / `admin123`。

## 默认账号

| 场景 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 前端演示模式 | `demo` | `demo123` | 演示态任意账号密码均可登录 |
| 后端联调 | `admin` | `admin123` | `init.sql` 默认管理员 |

## 联调验证清单

1. 打开 http://localhost:8080/api/v1/system/health ，确认后端健康
2. 打开 http://localhost:3000/portal ，再进入登录页
3. 使用 `admin / admin123` 完成滑动验证码登录
4. 确认侧栏菜单由 `GET /menus/current-user` 动态加载
5. （可选）`cd frontend && npm run test:e2e:integration`

## 技术栈一览

### 前端

| 类别 | 技术 |
|------|------|
| 框架 | Vue 3.5（Composition API + `<script setup>`） |
| 语言 | TypeScript 5.7（`strict`） |
| 构建 | Vite 5.4 |
| UI | Element Plus 2.9 |
| 状态 / 路由 | Pinia 2.3、Vue Router 4.5（动态菜单） |
| 请求 | Axios 1.8（`/api/v1`、大整数安全） |

### 后端

| 类别 | 技术 |
|------|------|
| 语言 / 框架 | Java 21、Spring Boot 4.1 |
| 安全 | Spring Security 7 + JWT |
| 持久化 | MyBatis-Plus 3.5、PostgreSQL 14 |
| 模块 | Spring Modulith（`api` → `security` → `biz` → `core`） |
| 文档 | SpringDoc / Swagger UI |

## 工程结构说明

| 项 | 值 |
|----|-----|
| 中文名称 | 知枢可集成框架 |
| 英文名称 | ZhiShu Integrable Framework |
| 简称 | ZSIF |
| 前端包名 | `zhishu-integrable-framework` |
| 后端模块 | `zhishu-*`（继承云起工程结构） |
| Java 包名 | `cn.datafuturex.zhishu` |
| 数据库 | `zhishu_integrable_framework` |
| 上游远程 | `upstream` → `git@github.com:DataFutureX/yunqi-application-platform.git` |

从上游同步更新：

```bash
git fetch upstream
git merge upstream/main
```

## 常见问题

**前端依赖安装失败**

```bash
npm cache clean --force
# PowerShell
Remove-Item -Recurse -Force node_modules, package-lock.json -ErrorAction SilentlyContinue
npm install
```

**后端数据库连接被拒**

- 确认 PostgreSQL 已启动，且已执行 `init.sql` 初始化 `zhishu_integrable_framework`
- 检查 `backend/zhishu-core/src/main/resources/application-dev.yml` 中 `yunqi.datasource` 账号密码

## 下一步

- 产品门户文档：本地 [http://localhost:3000/docs](http://localhost:3000/docs)
- 前端完整说明：[frontend/README.md](./frontend/README.md)
- 后端完整说明：[backend/README.md](./backend/README.md)
- 单点登录协议：[docs/单点登录对接说明.md](./docs/单点登录对接说明.md)

## 许可证

本项目基于 [MIT License](./LICENSE) 开源。
