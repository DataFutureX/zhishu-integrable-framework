# 知枢后端 Docker Compose 部署

本目录（`zhishu-backend/deployment`）只提供**后端单容器**部署：镜像只装运行时（`eclipse-temurin:21-jre` + 字体 + curl），
**jar 不打进镜像**，而是把 `deployment/app` 只读挂载到容器 `/app/deploy`；日志与上传件挂载到 `deployment/data`。
PostgreSQL、前端、Neo4j **不在**本 compose 内，通过环境变量连接已有实例。

打包与构建**分成两步、两台机器**：

```
zhishu-backend/deployment/
├── docker-compose.yml          # 单服务 zhishu-app：端口 / 挂载 / 健康检查 / 内存上限
├── Dockerfile                  # 运行时镜像（JRE + 字体 + curl），不含 jar，非 root 运行
├── .dockerignore               # 构建上下文为空：镜像不 COPY 任何宿主机文件
├── .env.example                # 环境变量样例（复制为 .env 后填写，.env 已被 gitignore）
├── .gitignore                  # 忽略 app/*.jar、data 内容与 .env
├── app/                        # jar 目录 -> 只读挂载到容器 /app/deploy（升级就换这里）
├── data/                       # 运行时数据：logs/ 与 uploads/ 对应容器同名挂载点（不入库，脚本自动创建）
├── scripts/package-jar.ps1     # 步骤 1（Windows）：Maven 打包并暂存 jar，不碰 Docker
└── scripts/build-image.sh      # 步骤 2（Linux）：docker compose build / up，不跑 Maven
```

> 分工边界：Windows 只需 JDK 21 + Maven；Linux 只需 Docker（无需 JDK/Maven，容器内不编译）。
> `package-jar.ps1` 靠目录层级自动定位 `../zhishu-core/target` 的产物，无需配置路径。
> **升级 = 换掉 `app/` 下的 jar + `docker compose restart`**，不必重新 build；只有改 `Dockerfile` 时才需要 build。

## 1. 前置条件

| 项 | 位置 | 说明 |
| --- | --- | --- |
| JDK 21 + Maven 3.9 | Windows 打包机 | 仅步骤 1 使用，产物是可执行 fat jar |
| Docker Engine 24+ / Compose v2 | Linux 构建机 | `docker compose version` 能输出即可，无需装 JDK/Maven |
| PostgreSQL 14+（含 pgvector） | 任一可达网络 | 应用启动会自建向量表，账号需有建表权限 |
| 网络可达 | Linux 构建机 | 容器需能访问数据库地址（宿主机库用 `host.docker.internal`） |

## 2. 准备数据库（一次性）

本方案不含数据库容器，请先在目标 PostgreSQL 上建库并初始化（以下 `-f` 路径相对 `deployment` 目录）：

```bash
psql -U postgres -c "CREATE DATABASE zhishu_integrable_framework WITH ENCODING 'UTF8' TEMPLATE template0;"
psql -U postgres -d zhishu_integrable_framework -f ../zhishu-core/src/main/resources/db/init.sql
psql -U postgres -d zhishu_integrable_framework -f ../zhishu-core/src/main/resources/db/init_ai.sql
```

- `init.sql` 建系统表（用户/角色/菜单/公告/操作日志），`init_ai.sql` 建 AI 表并执行 `CREATE EXTENSION IF NOT EXISTS vector`。
- 默认管理员 **admin / admin123**，上线前务必改密。
- 已有库升级请按需补执行 `db/patch_*.sql`。

## 3. 两步部署

### 步骤 1：Windows 打包 jar

```powershell
# 仓库根目录执行
.\zhishu-backend\deployment\scripts\package-jar.ps1
```

内部执行 `mvn -pl zhishu-core -am clean package -DskipTests`，并把产物拷成固定名
`deployment/app/zhishu-core-1.0.0.jar`。本步骤不接触 Docker。

- `-Tests`：打包时执行单元测试（默认跳过）
- `-SkipMaven`：`zhishu-core/target` 已有产物，只做暂存拷贝

### 步骤 2：把 deployment 目录传到 Linux

```bash
scp -r zhishu-backend/deployment user@server:~/zhishu-backend/
```

> `app/*.jar` 已被 `.gitignore` 忽略，**不能**靠 git 同步 jar，必须整目录传输（或单独 `scp` jar）。

### 步骤 3：Linux 构建镜像并启动

```bash
cd ~/zhishu-backend/deployment
chmod +x scripts/build-image.sh        # 首次执行前可能需要补上可执行位
cp .env.example .env                   # 编辑 .env 填写数据库地址与各项密钥
./scripts/build-image.sh --up          # build + up -d；再加 --logs 可顺便跟踪日志
```

`build-image.sh` 依次做四件事：校验 `app/` 下有 jar（多个时取修改时间最新的一个）→ 建好 `data/logs`、`data/uploads`
（以 root 执行时顺带 `chown 10001:10001`，容器内是非 root 用户，需要写权限）→ 校验 `.env` 存在 → `docker compose build`，
`--up` 时再 `up -d`。jar 或 `.env` 缺失会直接中止，不会在 Linux 上尝试编译源码。

## 4. 验证

```bash
docker compose ps                     # STATUS 应为 healthy（首启 start_period 90s）
curl http://localhost:8180/api/v1/system/health   # 免鉴权健康接口
docker compose logs -f zhishu-app     # 跟踪启动日志
```

容器内监听端口与宿主机发布端口分别由 `SERVER_PORT` 与 `APP_PORT` 控制，默认都是 **8180**。

## 5. 配置项速查（详见 `.env.example` 注释）

| 变量 | 必填 | 作用 |
| --- | --- | --- |
| `YUNQI_DB_HOST/PORT/NAME/USERNAME/PASSWORD` | 是 | 外部 PostgreSQL 连接 |
| `YUNQI_DATASOURCE_PARAMS` | 视情况 | `application-prod.yml` 写死 `sslmode=require`，库不支持 SSL 时置 `sslmode=disable`；也可用 `YUNQI_DATASOURCE_JDBC_URL` 整体覆盖 |
| `JWT_SECRET` | 是 | prod profile 无默认值，缺失直接启动失败 |
| `ZHISHU_AI_CRYPTO_KEY` | 是 | 加密「模型设置」入库的 API Key；一经使用不可更换 |
| `ZHISHU_DESKTOP_AUTH_KEY` | 是 | 留空 = 不鉴权（仅开发可用）；MCP 已取消静态密钥，`/mcp` 走 `wxmcp_*` 或登录 JWT |
| `APP_PORT` / `SERVER_PORT` | 否 | 默认 8180 |
| `MEM_LIMIT` | 否 | 默认 `3g`（`-Xmx2g` + 约 1G 堆外余量） |
| `JAVA_OPTS` | 否 | 默认 `-Xms512m -Xmx2g ...`，整体覆盖时保留 UTF-8 编码与 headless 参数 |
| `JAR_GLOB` | 否 | 容器在 `/app/deploy` 里匹配 jar 的文件名模式，默认 `zhishu-core-*.jar`（多个取修改时间最新的）|
| `WANXIANG_KG_ENABLED` | 否 | 无 Neo4j 建议置 `false`，否则启动日志会报 Driver 初始化失败（功能自动降级，不影响主链路） |

> `.env` 的值中出现 `$` 请写成 `$$`，否则会被 docker compose 当作变量插值展开。

## 6. 运维

```bash
docker compose restart            # 换完 jar 就用它（重新读取挂载目录里的文件）
docker compose up -d              # 改了 .env 或 docker-compose.yml 后需要重建容器（restart 不生效）
docker compose down               # 停止并删除容器；app/ 与 data/ 都是宿主机目录，内容不会丢
```

- **升级**：Windows 跑 `package-jar.ps1` → 把新的 `deployment/app/zhishu-core-1.0.0.jar` 传到服务器覆盖同名文件 →
  `docker compose restart`。只有改动 `Dockerfile`（基础镜像、系统依赖）时才需要重跑 `./scripts/build-image.sh`。
- **数据位置**：容器 `/app/logs`、`/app/uploads` 对应宿主机 `deployment/data/logs`、`deployment/data/uploads`；
  这两个子目录不入库，首次由 `build-image.sh` 创建（手工 `up -d` 前自己 `mkdir -p data/logs data/uploads` 也可）；
  已不使用命名卷，重建容器甚至 `down -v` 都不会清空它们，备份直接打包 `data/` 即可。
- **内存调整**：堆由 `JAVA_OPTS` 的 `-Xmx` 决定（默认 2G）。调大时 `MEM_LIMIT` 至少比堆多 1G，
  否则容器会被 `OOMKilled`。OOM 堆转储写到 `/app/logs`（即宿主机 `data/logs`）。
- **日志**：文件日志在 `data/logs/zhishu-backend.log`（logback 按 10MB / 天滚动）；
  stdout 经 Docker `json-file` 驱动收集，单文件 20MB、保留 5 个。
- **单实例约束**：SSO ticket 的一次性 `jti` 存储与验证码会话均为进程内存
  （见 `SsoJtiStore` / `CaptchaStore`），多副本部署需先外置 Redis，本方案不支持水平扩容。
- **前端**：jar 内无静态资源（`zhishu-core` 没有 `static/`），后端也未配置 CORS，
  浏览器直连 `:8180` 会跨域。生产需由外部 Nginx 同源反向代理 `/api`、`/mcp`、`/uploads`
  （本地开发走 Vite proxy，其默认 target 同为 `http://localhost:8180`）。

## 7. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| Linux 报 `未找到 jar：.../app/zhishu-core-*.jar` | jar 没随目录传过来（`app/*.jar` 被 `.gitignore` 忽略）；回到步骤 1 打包后整目录 `scp -r` |
| 容器反复重启并打印 `[FATAL] /app/deploy 下未找到 ...` | `app/` 下没有匹配 `zhishu-core-*.jar` 的文件；放入 jar 或按需在 `.env` 覆盖 `JAR_GLOB` |
| 换了 jar 但 `restart` 后行为没变 | 容器取的是 `app/` 下**修改时间最新**的 jar，残留旧包会造成歧义；清理旧 jar 或直接 `docker compose up -d` 重建容器 |
| 日志或上传报 `Permission denied` | 宿主机 `data/` 属主与容器运行用户（uid 10001）不匹配：`sudo chown -R 10001:10001 deployment/data`（`build-image.sh` 以 root 执行时会自动处理）|
| `Connection to <host>:5432 refused` | 数据库地址不可达。宿主机库请写 `host.docker.internal`（compose 已配 `host-gateway`），并确认 PG 监听 `0.0.0.0` 而非仅 `127.0.0.1` |
| `FATAL: ... SSL`／认证被拒 | 未覆盖 prod 的 `sslmode=require`：在 `.env` 设 `YUNQI_DATASOURCE_PARAMS=sslmode=disable`，或给出完整 `YUNQI_DATASOURCE_JDBC_URL` |
| `Could not resolve placeholder 'JWT_SECRET'` / 启动即退出 | `.env` 缺少 `JWT_SECRET`（prod profile 无默认值） |
| 向量表建表失败 / `extension "vector" is not available` | 镜像未装 pgvector，或数据库账号无建扩展权限；先用超级用户执行 `init_ai.sql` |
| 验证码接口 500、日志含 `java.awt` 相关异常 | 基础镜像缺字体。本 Dockerfile 已装 `fontconfig` + `fonts-dejavu-core`，若自行裁剪基础镜像需保留 |
| 控制台日志中文乱码 | 覆盖 `JAVA_OPTS` 时丢了 `-Dfile.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8` 等参数 |
| `docker compose ps` 一直 `unhealthy` | 先看 `docker compose logs` 前 100 行；首启超过 90s 属正常，可适当调大 `start_period` |
| 容器状态 `OOMKilled` | `MEM_LIMIT` 小于 `-Xmx` + 余量；上调 `MEM_LIMIT` |
| `env file ...not found: .env` | Linux 侧未从 `.env.example` 复制 `.env` |
| `build-image.sh` 报 `bad interpreter` 或 `Permission denied` | CRLF 与可执行位问题：`dos2unix scripts/build-image.sh && chmod +x scripts/build-image.sh` |
