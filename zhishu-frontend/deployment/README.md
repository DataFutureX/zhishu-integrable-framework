# 知枢前端 Nginx 容器部署

本目录（`zhishu-frontend/deployment`）用**官方 nginx 容器**托管前端产物，并**同域反向代理**到后端：
浏览器只访问 nginx 一个源，接口走 `/api/**`，因此不需要给后端加 CORS，也不自建镜像（没有 Dockerfile）。

```
zhishu-frontend/deployment/
├── docker-compose.yml                  # 单服务 zhishu-web：端口 / 挂载 / 健康检查
├── .env.example                        # 站点端口、后端上游、HTTPS 开关（复制为 .env）
├── .gitignore / .dockerignore          # web 产物、渲染配置、证书、日志、.env 均不入库
├── README.md
├── web/                                # 站点产物（dist 内容）-> /usr/share/nginx/html:ro
├── nginx/
│   ├── snippets/static.conf            # 静态资源、gzip、缓存、SPA history 回退
│   ├── snippets/proxy.conf             # /api、/uploads、/mcp 等反代与 SSE 参数
│   ├── snippets/proxy-headers.conf     # 转发头集合（Host / X-Forwarded-*）
│   ├── optional/default.conf.http.template    # HTTP server 源模板
│   ├── optional/default.conf.https.template   # HTTPS server 源模板（含 80 跳转）
│   └── generated/                      # deploy-web.sh 渲染出的 default.conf.template
├── certs/                              # HTTPS 证书（fullchain.pem / privkey.pem）
├── data/                               # data/logs -> /var/log/nginx
└── scripts/
    ├── package-web.ps1                 # 步骤 1（Windows）：npm run build -> 暂存 web/
    └── deploy-web.sh                   # 步骤 2（Linux）：渲染配置 + docker compose up -d
```

要点：

- **不打镜像**：`image: nginx:1.27-alpine` 直接挂载宿主机目录，站点更新不用重建镜像。
- **更新即覆盖文件**：覆盖 `web/` 内容即刻生效（`index.html` 已设 `no-cache`，`/assets/` 长强缓存），无需 `restart`。
- **配置走 envsubst**：官方镜像只对 `BACKEND_` / `NGINX_` 前缀变量做替换，`$host`、`$uri` 等 nginx 变量不受影响。
- **生成的配置必须叫 `default.conf.template`**：这样 envsubst 产物会覆盖镜像自带的 `conf.d/default.conf`，避免 80 端口出现两个默认 server。
- **转发头单一份 snippet**：nginx 的 `proxy_set_header` 继承是“全有或全无”，location 只要自己定义了任意一条，就不再继承 server 级的全部转发头；
  此时若不重新 include，Host 会退化为 upstream 名 `zhishu_backend`（含下划线），**Tomcat 会直接返回 400**（请求进不到应用）。
  所以 `/api/` 块里除了 `proxy_set_header Connection ""` 之外，必须同时 `include proxy-headers.conf`。

## 1. 前置条件

| 项 | 位置 | 说明 |
| --- | --- | --- |
| Node.js 18+ / npm 9+ | Windows 打包机 | 仅步骤 1 使用；首次需 `npm install` |
| Docker Engine 24+ / Compose v2 | Linux 站点机 | 无需安装 Node，容器内不构建 |
| 后端已可访问 | Linux 站点机 | 默认 `host.docker.internal:8180`，对应 `zhishu-backend/deployment` 的 `APP_PORT` |

## 2. 两步部署

### 步骤 1：Windows 构建产物

```powershell
# 仓库根目录执行
.\zhishu-frontend\deployment\scripts\package-web.ps1
```

- 构建期把 `VITE_API_BASE_URL` 强制为 `/api`：若 `.env.production` 已是 `/api` 则直接用文件值，
  否则用进程环境变量覆盖（Vite 中进程环境变量优先），**不改仓库文件也能得到同源产物**。
- `-SkipTypeCheck` 跳过 `vue-tsc`；`-Demo` 走 `build:demo`；`-SkipBuild` 只做 `dist` → `web/` 暂存。
- 产物落到 `deployment/web/`（旧的会被清掉，保留 `.gitkeep`）。

### 步骤 2：传到 Linux 并起容器

```bash
# 首次整目录传输（web/ 与 .env 被 gitignore，不能靠 git 同步）
scp -r zhishu-frontend/deployment user@server:~/zhishu-frontend/

# 服务器上
cd ~/zhishu-frontend/deployment
chmod +x scripts/deploy-web.sh
cp .env.example .env              # 按需修改 WEB_PORT / BACKEND_UPSTREAM
./scripts/deploy-web.sh           # 渲染配置 + docker compose up -d + nginx -t
```

日常更新只需覆盖产物目录：

```bash
scp -r zhishu-frontend/deployment/web/* user@server:~/zhishu-frontend/deployment/web/
# 无需重启容器；如需彻底重建：docker compose up -d --force-recreate
```

## 3. 验证

```bash
docker compose ps                                       # STATUS 应为 healthy
curl -I http://localhost:80/                            # 200，且 index.html 带 Cache-Control: no-cache
curl http://localhost:80/nginx-health                   # ok（不依赖 web/ 是否有产物）
curl http://localhost:80/api/v1/system/health           # 经 nginx 反代到后端
curl -i http://localhost:80/uploads/system/x.png        # 或 /api/uploads/system/x.png（两种写法都支持）
docker compose logs -f zhishu-web
```

浏览器侧再确认：登录页可登录、公告铃铛能持续收到 SSE 推送（验证 `proxy_buffering off` 生效）。

## 4. 路由与代理对照表

| 浏览器请求 | 去向 | 说明 |
| --- | --- | --- |
| `/`、`/assets/**`、任意 SPA 路径 | 容器静态文件 | `try_files ... /index.html`，history 路由必需 |
| `/api/v1/**` | 后端 `/api/v1/**` | 原样透传（后端路由自带 `/api`，无 context-path）；关缓冲 + 300s 超时供 SSE/AI 流式 |
| `/api/uploads/**` | 后端 `/uploads/**` | `src/utils/asset.ts` 拼出的路径（图标等） |
| `/uploads/**` | 后端 `/uploads/**` | 兼容裸路径写法 |
| `/api/mcp`、`/mcp` | 后端 `/mcp` | `McpHub.vue` 展示的地址带 `/api` 前缀 |
| `/swagger-ui/**`、`/v3/api-docs/**`、`/webjars/**` | 后端同名路径 | 后端 prod 默认关闭 springdoc，仅临时排查用 |

## 5. 配置项速查（`.env.example`）

| 变量 | 默认 | 作用 |
| --- | --- | --- |
| `WEB_PORT` | 80 | 宿主机对外端口（先确认未被占用：`ss -lntp` 看 80） |
| `HTTPS_PORT` | 443 | 宿主机 HTTPS 端口 |
| `NGINX_HTTP_PORT` | 80 | 容器内监听端口，同时用于健康检查 |
| `NGINX_SERVER_NAME` | `_` | server_name；上线按域名收紧 |
| `BACKEND_UPSTREAM` | `host.docker.internal:8180` | 后端地址；同 Docker 网络可写 `zhishu-app:8180`，异机写 `IP:端口` |
| `ENABLE_HTTPS` | `false` | `true` 时渲染 HTTPS 模板（需证书） |
| `NGINX_IMAGE` | `nginx:1.27-alpine` | 镜像与版本 |
| `TZ` | `Asia/Shanghai` | 容器时区（日志时间戳） |

> `.env` 的值中出现 `$` 请写成 `$$`，否则会被 docker compose 当作变量插值展开。

## 6. 启用 HTTPS

```bash
cd ~/zhishu-frontend/deployment
# 1) 放证书（不要提交进仓库，本目录 certs/* 已被 gitignore）
cp /path/to/fullchain.pem /path/to/privkey.pem certs/
# 2) 打开开关
sed -i 's/^ENABLE_HTTPS=.*/ENABLE_HTTPS=true/' .env
# 3) 重新部署（脚本会把 HTTPS 模板渲染成 nginx/generated/default.conf.template）
./scripts/deploy-web.sh
```

HTTPS 模板里 80 端口只保留 `/nginx-health` 与到 443 的 301 跳转；确认证书受信后再放开 `Strict-Transport-Security`（模板已注释预留）。
回退到 HTTP：把 `ENABLE_HTTPS=false` 后重跑脚本即可（`nginx/generated` 每次先清空 `*.template` 再渲染，幂等）。

## 7. 运维

```bash
./scripts/deploy-web.sh --down          # 停止并移除容器（web/、data/ 内容保留）
docker compose restart                  # 改了挂载内容之外的东西时重启
docker compose exec -T zhishu-web nginx -t        # 只校验容器内实际生效的配置
docker compose exec -T zhishu-web nginx -s reload # 配置变更后热加载（重建容器时不需要）
```

snippets 与模板是挂载进容器的普通文件：改完 `nginx/snippets/*.conf` 只需 `nginx -t` 后 `nginx -s reload`，
无需重建容器；只改 `nginx/optional/*.template`（含 envsubst 变量）才需要重跑 `./scripts/deploy-web.sh`。

- **日志**：宿主机 `deployment/data/logs/{access,error}.log`（对应容器 `/var/log/nginx`）；
  stdout 由 Docker `json-file` 收集，单文件 20MB、保留 5 个。
- **上传体积**：nginx `client_max_body_size 20m`，后端 multipart 上限 10MB（`application.yml`），
  要放大需两头一起改。
- **共用后端网络（可选）**：若希望 nginx 直连后端容器而不经宿主机端口，可在后端项目里
  `docker network create zhishu-net`，两个 compose 各加
  `networks: { zhishu-net: { external: true } }` 并把 `BACKEND_UPSTREAM` 改为 `zhishu-app:8180`。
- **单站点多域名**：复制一份 deployment 目录并改 `name:`、`container_name`、`WEB_PORT`、`NGINX_SERVER_NAME`。

## 8. 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `docker compose ps` 一直 `unhealthy` | 没跑 `deploy-web.sh`，`nginx/generated/` 为空 → 镜像自带 `default.conf` 生效，`/nginx-health` 404。重跑脚本即可 |
| 页面能开、接口全 404 | 产物不是同源版本：`.env.production` 的 `VITE_API_BASE_URL` 被构建成了完整域名。用 `package-web.ps1` 重新构建（会把前缀强制为 `/api`） |
| 刷新子路由 404 | 站点配置未回退到 index.html；确认 `nginx/snippets/static.conf` 的 `location / { try_files $uri $uri/ /index.html; }` 未被删改 |
| 图标/上传件不显示 | 前端拼的是 `/api/uploads/**`，后端在 `/uploads/**`；确认 `proxy.conf` 中 `/api/uploads/` 的映射还在 |
| SSE 收不到推送 / 流式卡住不出字 | `/api/` 的 `proxy_buffering off` 与 `proxy_read_timeout 300s` 被去掉；若前面还有云 LB/上层 nginx，也要关缓冲 |
| **经 nginx 的 `/api/**` 全部 400，直连后端 200（`/uploads/**` 却正常）** | 响应体是 Tomcat 错误页而非 nginx 页 → Host 头退化：某 location 自定义了 `proxy_set_header` 却没重新 include `proxy-headers.conf`，导致 `Host: zhishu_backend`（下划线被 Tomcat 拒）。另外 nginx 不认单引号，`Connection '';` 应为 `Connection "";` |
| `host not found in upstream` | `BACKEND_UPSTREAM` 写错或后端未启动；`host.docker.internal` 需 compose 里的 `extra_hosts: host-gateway` |
| 启用 HTTPS 后 nginx -t 报证书找不到 | `certs/` 下缺 `fullchain.pem` / `privkey.pem`，或文件名不符；目录是只读挂载，权限需可读 |
| `nginx: [emerg] duplicate ... 80` | `nginx/generated` 里出现了多个 `*.template`，或手工放了额外配置；该目录只应由 `deploy-web.sh` 渲染出唯一的 `default.conf.template` |
| 大文件上传 413 | 先到 nginx（20m）再到后端（10MB multipart）；两头同步放大 |
| `deploy-web.sh` 报 `bad interpreter` 或无法执行 | Windows 检出带 CRLF / 缺可执行位：`dos2unix scripts/deploy-web.sh && chmod +x scripts/deploy-web.sh` |
