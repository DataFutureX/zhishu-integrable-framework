<p align="center">
  <img src="./logo.svg" width="88" height="88" alt="知枢可集成框架">
</p>

<h1 align="center">知枢可集成智能体框架 · 快速开始</h1>

一套面向企业数字化与智能化应用集成的模块化开发底座（MIT），简称 **ZSIF**（ZhiShu Integrable Framework）。提供前后端一体架构、RBAC 权限、伙伴 SSO，以及 Agent / RAG / 知识图谱 / MCP Hub / 开放 API。

**源码仓库**：[GitHub](https://github.com/DataFutureX/zhishu-integrable-framework) · [Gitee](https://gitee.com/DataFutureX/zhishu-integrable-framework)

**在线演示**：[产品门户](https://zhishu.datafuturex.cn/portal) · [登录](https://zhishu.datafuturex.cn/login) · [文档](https://zhishu.datafuturex.cn/docs)

## 目录

- [界面一览](#界面一览)
- [两种体验路径](#两种体验路径)
- [路径 A：仅前端演示（无需后端）](#路径-a仅前端演示无需后端)
- [路径 B：前后端联调](#路径-b前后端联调)
- [生产部署（Docker Compose）](#生产部署docker-compose)
- [默认账号](#默认账号)
- [联调验证清单](#联调验证清单)
- [技术栈一览](#技术栈一览)
- [工程结构说明](#工程结构说明)
- [常见问题](#常见问题)
- [下一步](#下一步)
- [许可证](#许可证)

## 界面一览

截图来自联调环境 `http://localhost:3100`（`admin`）。完整清单见 [`screenshot/`](./screenshot/)。

产品门户（`/portal`，按顶部导航分屏）：

<table>
  <tr>
    <td align="center" width="20%"><img src="./screenshot/01a-portal-hero.png" width="220" alt="首屏"><br>首屏</td>
    <td align="center" width="20%"><img src="./screenshot/01b-portal-opensource.png" width="220" alt="开源"><br>开源</td>
    <td align="center" width="20%"><img src="./screenshot/01c-portal-features.png" width="220" alt="能力"><br>能力</td>
    <td align="center" width="20%"><img src="./screenshot/01d-portal-stack.png" width="220" alt="技术栈"><br>技术栈</td>
    <td align="center" width="20%"><img src="./screenshot/01e-portal-docs.png" width="220" alt="文档"><br>文档</td>
  </tr>
</table>

登录页：

<p align="center">
  <img src="./screenshot/02-login.png" width="720" alt="登录页">
</p>

登录后默认进入智能中心 · Agent 会话（工作台与仪表盘已禁用）：

<table>
  <tr>
    <td align="center" width="25%"><img src="./screenshot/03-dashboard.png" width="280" alt="Agent 会话"><br>Agent 会话</td>
    <td align="center" width="25%"><img src="./screenshot/15-knowledge-qa.png" width="280" alt="知识检索"><br>知识检索</td>
    <td align="center" width="25%"><img src="./screenshot/16-knowledge-graph.png" width="280" alt="知识图谱"><br>知识图谱</td>
    <td align="center" width="25%"><img src="./screenshot/17-agents.png" width="280" alt="Agents"><br>Agents</td>
  </tr>
  <tr>
    <td align="center"><img src="./screenshot/18-knowledges.png" width="280" alt="知识库"><br>知识库</td>
    <td align="center"><img src="./screenshot/19-mcp-hub.png" width="280" alt="MCP Hub"><br>MCP Hub</td>
    <td align="center"><img src="./screenshot/20-model-config.png" width="280" alt="模型设置"><br>模型设置</td>
    <td align="center"><img src="./screenshot/21-open-api.png" width="280" alt="开放能力"><br>开放能力</td>
  </tr>
  <tr>
    <td align="center"><img src="./screenshot/04-user.png" width="280" alt="用户管理"><br>用户管理</td>
    <td align="center"><img src="./screenshot/05-unit.png" width="280" alt="单位管理"><br>单位管理</td>
    <td align="center"><img src="./screenshot/06-role.png" width="280" alt="角色管理"><br>角色管理</td>
    <td align="center"><img src="./screenshot/07-menu.png" width="280" alt="菜单管理"><br>菜单管理</td>
  </tr>
  <tr>
    <td align="center"><img src="./screenshot/08-system-config.png" width="280" alt="参数配置"><br>参数配置</td>
    <td align="center"><img src="./screenshot/09-announcement.png" width="280" alt="公告管理"><br>公告管理</td>
    <td align="center"><img src="./screenshot/10-operation-log.png" width="280" alt="操作日志"><br>操作日志</td>
    <td align="center"><img src="./screenshot/11-monitor.png" width="280" alt="运维监控"><br>运维监控</td>
  </tr>
  <tr>
    <td align="center"><img src="./screenshot/12-api-docs.png" width="280" alt="后端接口"><br>后端接口</td>
    <td align="center"><img src="./screenshot/13-profile.png" width="280" alt="个人信息"><br>个人信息</td>
    <td align="center"><img src="./screenshot/14-change-password.png" width="280" alt="修改密码"><br>修改密码</td>
    <td></td>
  </tr>
</table>

> 重新截图：`node scripts/capture-demo-screenshots.mjs`（默认 `http://localhost:3100`）。门户分屏也可单独跑 `node scripts/capture-portal-sections.mjs`。

## 两种体验路径

| 路径 | 适用场景 | 需要准备 |
|------|----------|----------|
| **A. 前端演示模式** | 快速浏览界面与交互，不接真实 API | Node.js ≥ 18、npm ≥ 9 |
| **B. 前后端联调** | 完整权限、持久化数据、Swagger 调试 | 另需 JDK 21、Maven 3.9+、PostgreSQL 14+（`vector` 扩展）；知识图谱可选 Neo4j 5.x |

建议：先走路径 A 熟悉产品，再按路径 B 启动后端完成真实联调。

## 路径 A：仅前端演示（无需后端）

```bash
cd zhishu-frontend
npm install
npm run dev:demo
```

浏览器打开 [http://localhost:3100](http://localhost:3100)（可先看 `/portal`，再登录）。

演示账号：`demo` / `demo123`（演示态任意账号密码均可登录）。

演示模式通过 `--mode demo` 加载 `.env.demo`（`VITE_DEMO_MODE=true`），Axios 挂载内置 Mock，覆盖登录、权限、CRUD、智能中心与开放应用 AK/SK。

可选启动脚本（在 `zhishu-frontend/`）：`start.bat` / `.\start.ps1` / `./start.sh`。

## 路径 B：前后端联调

本仓库为前后端一体的 monorepo：

```text
zhishu-integrable-framework/
├── zhishu-frontend/   # Vue 3 前端（deployment/ = Nginx 容器部署）
├── zhishu-backend/    # Java 后端（Maven 父工程，deployment/ = Docker 容器部署）
├── zhishu-sdk/        # 伙伴 SSO SDK、开放 API Java SDK
├── docs/              # 对接说明
├── screenshot/        # 界面截图
├── start.bat / start.ps1 / start.sh
└── README.md
```

### 1. 启动后端

#### 环境要求

- JDK **21+**
- Maven **3.9+**
- PostgreSQL **14+**（RAG 需 `vector` 扩展）
- Neo4j **5.x**（可选，知识图谱）

#### 初始化数据库

```bash
cd zhishu-backend
psql -U postgres -c "CREATE DATABASE zhishu_integrable_framework WITH ENCODING 'UTF8' TEMPLATE template0;"
psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/init.sql
psql -U postgres -d zhishu_integrable_framework -f zhishu-core/src/main/resources/db/init_ai.sql
```

默认库名：`zhishu_integrable_framework`。系统表在 `init.sql`，AI 表（pgvector / Agent / MCP / 开放应用）在 `init_ai.sql`。

#### 修改数据源

编辑 `zhishu-backend/zhishu-core/src/main/resources/application-dev.yml`：

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
cd zhishu-backend

# Windows
start-dev.bat

# Linux / macOS
chmod +x start-dev.sh
./start-dev.sh

# 或 Maven
mvn -pl zhishu-core -am spring-boot:run -DskipTests
```

`zhishu-ai` 与 `zhishu-core` **同进程**，dev 默认端口 **8180**，不单独占端口。

| 地址 | 说明 |
|------|------|
| http://localhost:8180 | 后端 HTTP（dev） |
| http://localhost:8180/swagger-ui.html | API 文档（dev 放行） |
| http://localhost:8180/api/v1/system/health | 健康检查（无需登录） |

### 2. 启动前端（联调模式）

```bash
cd zhishu-frontend
npm install
npm run dev
```

确认 `zhishu-frontend/.env.development`：

```env
VITE_PORT=3100
VITE_API_BASE_URL=http://localhost:8180
```

| 地址 | 说明 |
|------|------|
| http://localhost:3100 | 前端联调 |
| http://localhost:3100/portal | 产品门户 |
| http://localhost:3100/login | 登录 |

联调默认管理员：`admin` / `admin123`（滑动验证码）。

## 生产部署（Docker Compose）

本地体验用路径 A / B，正式环境部署用本章。前后端各有一个独立部署目录，均采用**打包与运行分离**的两步流程：Windows 只出产物，Linux 只跑容器。

| 项 | 后端 `zhishu-backend/deployment/` | 前端 `zhishu-frontend/deployment/` |
|----|-----------------------------------|-----------------------------------|
| 运行载体 | `eclipse-temurin:21-jre` 镜像 + 宿主机 jar（镜像内不含 jar） | 官方 `nginx:1.27-alpine`（不自建镜像） |
| 步骤 1（Windows） | `scripts/package-jar.ps1` → `app/zhishu-core-1.0.0.jar` | `scripts/package-web.ps1` → `web/`（dist 内容） |
| 步骤 2（Linux） | `scripts/build-image.sh --up` | `scripts/deploy-web.sh`（可用 `--down` 停止） |
| 默认端口 | 8180（`APP_PORT` / `SERVER_PORT`） | 80（`WEB_PORT`） |
| 资源默认 | `-Xms512m -Xmx2g`，`mem_limit=3g` | — |
| 数据落盘 | `data/logs`、`data/uploads` | `data/logs`（nginx 访问/错误日志） |
| 更新方式 | 覆盖 `app/*.jar` + `docker compose restart` | 覆盖 `web/` 内容即刻生效 |
| 详细文档 | [后端部署说明](./zhishu-backend/deployment/README.md) | [前端部署说明](./zhishu-frontend/deployment/README.md) |

前置：Linux 侧只需 Docker 24+ / Compose v2（无需 JDK、Maven、Node）；Windows 侧需 JDK 21 + Maven 3.9（后端）与 Node 18+（前端）。`app/*.jar`、`web/`、`.env` 均被 `.gitignore` 忽略，需 `scp -r` 整目录传输。

### 1. 初始化数据库（一次性）

```bash
# 在仓库根目录执行（需已安装 pgvector 扩展的 PostgreSQL 14+）
psql -U postgres -c "CREATE DATABASE zhishu_integrable_framework WITH ENCODING 'UTF8' TEMPLATE template0;"
psql -U postgres -d zhishu_integrable_framework -f zhishu-backend/zhishu-core/src/main/resources/db/init.sql
psql -U postgres -d zhishu_integrable_framework -f zhishu-backend/zhishu-core/src/main/resources/db/init_ai.sql
```

`init_ai.sql` 会执行 `CREATE EXTENSION IF NOT EXISTS vector`，需目标库已安装 pgvector 且账号有建扩展权限。

### 2. 部署后端

```powershell
# Windows：打可执行 jar 并暂存到 deployment/app/
.\zhishu-backend\deployment\scripts\package-jar.ps1
```

```bash
# 传到 Linux 后
cd zhishu-backend/deployment
cp .env.example .env            # 必填：YUNQI_DB_*、JWT_SECRET、ZHISHU_AI_CRYPTO_KEY 等
./scripts/build-image.sh --up   # 构建运行时镜像 + up -d
curl http://localhost:8180/api/v1/system/health
```

要点：

- 容器内激活 `prod` profile，敏感项全部来自 `.env`，不改 `application-prod.yml`。
- 外部 PostgreSQL 不支持 SSL 时设 `YUNQI_DATASOURCE_PARAMS=sslmode=disable`（prod 默认写死 `require`）。
- 无 Neo4j 时设 `WANXIANG_KG_ENABLED=false`，知识图谱自动降级，不影响主链路。
- `ZHISHU_MCP_AUTH_KEY` / `ZHISHU_DESKTOP_AUTH_KEY` 生产必须填（留空 = 不鉴权）。
- **改 `.env` 必须 `docker compose up -d`**（restart 不会重新读取环境变量）；只有换 jar 用 `docker compose restart`。

### 3. 部署前端

```powershell
# Windows：构建 dist 并暂存到 deployment/web/（构建期强制 VITE_API_BASE_URL=/api）
.\zhishu-frontend\deployment\scripts\package-web.ps1
```

```bash
cd zhishu-frontend/deployment
cp .env.example .env            # BACKEND_UPSTREAM 默认 host.docker.internal:8180
./scripts/deploy-web.sh
curl http://localhost/api/v1/system/health   # 经 nginx 反代，应与后端直连一致
```

要点：

- **同域反向代理**：nginx 既发静态资源，又把 `/api`、`/uploads`、`/mcp` 转发到后端，浏览器全程同源，无需给后端配 CORS。
- `/api/**` 已关闭缓冲并设 300s 读超时，保证公告 SSE 与 AI 流式对话不被网关提前切断。
- `web/` 与 `nginx/snippets/*.conf` 都是挂载文件：覆盖 `web/` 即刻生效；改 snippet 后 `docker compose exec -T zhishu-web nginx -s reload` 热加载。
- HTTPS 默认关闭：证书放入 `certs/{fullchain,privkey}.pem`，`.env` 设 `ENABLE_HTTPS=true` 后重跑脚本。
- 两套 compose 默认互不耦合（经宿主机端口访问）。想让 nginx 直连后端容器，可共用一个外部网络并把 `BACKEND_UPSTREAM` 改为 `zhishu-app:8180`。

### 4. 上线前检查

- 修改默认管理员 `admin` / `admin123` 的密码
- `JWT_SECRET` 换成随机串；`ZHISHU_AI_CRYPTO_KEY` 定稿后不再变更（改了已入库的模型 Key 无法解密）
- `NGINX_SERVER_NAME` 按域名收紧，按需启用 HTTPS
- 备份范围：数据库 + `zhishu-backend/deployment/data/uploads`（上传件与系统图标）
- 单实例约束：验证码会话与 SSO `jti` 存在进程内存里，多副本部署需先外置 Redis

## 默认账号

| 场景 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 前端演示模式 | `demo` | `demo123` | 演示态任意账号密码均可登录 |
| 后端联调 | `admin` | `admin123` | `init.sql` 默认管理员 |

> 默认账户仅用于本地开发。上线前务必修改密码，并更换 `jwt.secret` 等敏感配置。

## 联调验证清单

1. 打开 http://localhost:8180/api/v1/system/health ，确认后端健康
2. 打开 http://localhost:3100/portal ，再进入登录页
3. 使用 `admin / admin123` 完成滑动验证码登录
4. 确认侧栏菜单由 `GET /api/v1/menus/current-user` 动态加载，默认落地 `/ai/chat`
5. （可选）`cd zhishu-frontend && npm run test:e2e:integration`

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
| 持久化 | MyBatis-Plus 3.5、PostgreSQL 14 + pgvector |
| 模块 | Spring Modulith（`api` → `security` → `biz` / `ai` → `core`） |
| AI | Spring AI 2.0、Agent Framework、Neo4j 5.x（可选） |
| 文档 | SpringDoc / Swagger UI |

开放双平面：控制台 JWT（`/api/v1/**`）、开放 REST（`/open/v1/**`，AK/SK）、MCP Streamable HTTP（`/mcp`）。

## 工程结构说明

| 项 | 值 |
|----|-----|
| 中文名称 | 知枢可集成智能体框架 |
| 英文名称 | ZhiShu Integrable Framework |
| 简称 | ZSIF |
| 前端目录 / 包名 | `zhishu-frontend/` · `zhishu-integrable-framework` |
| 后端目录 / 模块 | `zhishu-backend/` · `zhishu-*` |
| SDK | `zhishu-sdk/`（伙伴 SSO、开放 API） |
| Java 包名 | `cn.datafuturex.zhishu` |
| 数据库 | `zhishu_integrable_framework` |
| 源码远程 | `origin` → [GitHub](https://github.com/DataFutureX/zhishu-integrable-framework) · [Gitee](https://gitee.com/DataFutureX/zhishu-integrable-framework) |

## 常见问题

**前端依赖安装失败**

```bash
cd zhishu-frontend
npm cache clean --force
# PowerShell
Remove-Item -Recurse -Force node_modules, package-lock.json -ErrorAction SilentlyContinue
npm install
```

**后端数据库连接被拒**

- 确认 PostgreSQL 已启动，且已执行 `init.sql` 与 `init_ai.sql` 初始化 `zhishu_integrable_framework`
- 检查 `zhishu-backend/zhishu-core/src/main/resources/application-dev.yml` 中 `yunqi.datasource` 账号密码

**端口被占用**

```bash
# 前端 3100 / 后端 8180
netstat -ano | findstr ":3100 :8180"
```

**容器化部署异常**

- 先看容器在不在：`docker compose ps`（后端在 `zhishu-backend/deployment`，前端在 `zhishu-frontend/deployment`）
- 接口 400/404 而直连后端正常：先看响应体是 nginx 页还是 Tomcat 页，再按两侧部署文档的「常见问题」对照
- 完整排查表：[后端部署说明](./zhishu-backend/deployment/README.md)、[前端部署说明](./zhishu-frontend/deployment/README.md)

## 下一步

- 产品门户文档：本地 [http://localhost:3100/docs](http://localhost:3100/docs) · 线上 [https://zhishu.datafuturex.cn/docs](https://zhishu.datafuturex.cn/docs)
- 前端完整说明：[zhishu-frontend/README.md](./zhishu-frontend/README.md)
- 后端完整说明：[zhishu-backend/README.md](./zhishu-backend/README.md)
- 后端容器部署：[zhishu-backend/deployment/README.md](./zhishu-backend/deployment/README.md)
- 前端容器部署：[zhishu-frontend/deployment/README.md](./zhishu-frontend/deployment/README.md)
- 单点登录协议：[docs/单点登录对接说明.md](./docs/单点登录对接说明.md)
- 开放 API SDK：[zhishu-sdk/zhishu-openapi-sdk/README.md](./zhishu-sdk/zhishu-openapi-sdk/README.md)

## 许可证

本项目基于 [MIT License](./LICENSE) 开源。
