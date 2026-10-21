#!/usr/bin/env bash
# 步骤 2（Linux）：起 nginx 容器托管前端产物，并同域反代到后端
# 不自建镜像：compose 直接用官方 nginx 镜像，产物/配置/证书/日志全部走宿主机挂载。
# 用法：
#   ./scripts/deploy-web.sh           # 渲染配置 + docker compose up -d
#   ./scripts/deploy-web.sh --down    # 停止并移除容器（web/ 与 data/ 内容保留）
# 日常更新：覆盖 deployment/web/ 内容即刻生效，连本脚本都不用跑
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
WEB_DIR="$DEPLOY_DIR/web"
DATA_DIR="$DEPLOY_DIR/data"
CERT_DIR="$DEPLOY_DIR/certs"
GEN_DIR="$DEPLOY_DIR/nginx/generated"
OPT_DIR="$DEPLOY_DIR/nginx/optional"

DO_DOWN=0
for arg in "$@"; do
  case "$arg" in
    --down)    DO_DOWN=1 ;;
    -h|--help) sed -n '1,7p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[ERROR] 未知参数：$arg" >&2; exit 1 ;;
  esac
done

echo '========================================'
echo '  [Step 2 / Linux] Deploy Nginx Site'
echo '========================================'

command -v docker >/dev/null 2>&1 || { echo "[ERROR] 未安装 docker" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] 需要 Docker Compose v2（docker compose 子命令）" >&2; exit 1; }

cd "$DEPLOY_DIR"

# .env 既供 compose 插值，也作为 env_file 注入容器，两种操作都需要它
[ -f .env ] || { echo "[ERROR] 缺少 $DEPLOY_DIR/.env，请先执行：cp .env.example .env" >&2; exit 1; }

read_env() {
  local line value
  line="$(grep -E "^[[:space:]]*$1=" .env | tail -n 1 || true)"
  value="${line#*=}"
  value="${value%$'\r'}"
  # 去掉首尾空白与成对引号
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/")"
  printf '%s' "$value"
}

if [ "$DO_DOWN" -eq 1 ]; then
  echo '[1/1] docker compose down ...'
  docker compose down
  echo '已停止。web/ 与 data/ 为宿主机目录，内容未删除。'
  exit 0
fi

# [1/4] 校验站点产物
if [ ! -f "$WEB_DIR/index.html" ]; then
  echo "[ERROR] 未找到 $WEB_DIR/index.html" >&2
  echo "        请先在 Windows 执行 deployment/scripts/package-web.ps1，再把 web/ 传到本机" >&2
  echo "        （web/ 内容被 .gitignore 忽略，不能靠 git 同步）" >&2
  exit 1
fi
WEB_COUNT="$(find "$WEB_DIR" -type f | wc -l | tr -d ' ')"
WEB_SIZE="$(du -sh "$WEB_DIR" | cut -f1)"
echo "[1/4] 站点就绪：web/（$WEB_COUNT 个文件，$WEB_SIZE）"

# [2/4] 准备日志目录
mkdir -p "$DATA_DIR/logs"
echo '[2/4] data/logs 就绪（nginx access/error 日志落此处）'

# [3/4] 按 ENABLE_HTTPS 渲染生效配置
ENABLE_HTTPS="$(read_env ENABLE_HTTPS)"; ENABLE_HTTPS="${ENABLE_HTTPS:-false}"
if [ "$ENABLE_HTTPS" = "true" ]; then
  for f in fullchain.pem privkey.pem; do
    [ -f "$CERT_DIR/$f" ] || { echo "[ERROR] 已启用 HTTPS 但缺少证书：$CERT_DIR/$f（渲染中止，当前配置未变动）" >&2; exit 1; }
  done
  SRC="$OPT_DIR/default.conf.https.template"
else
  SRC="$OPT_DIR/default.conf.http.template"
fi
# 校验通过后才清理并重渲染，避免失败时把可用配置一并清掉
mkdir -p "$GEN_DIR"
find "$GEN_DIR" -maxdepth 1 -name '*.template' -delete
cp "$SRC" "$GEN_DIR/default.conf.template"
if [ "$ENABLE_HTTPS" = "true" ]; then
  echo '[3/4] 配置已渲染：HTTPS（80 跳转 443）'
else
  echo '[3/4] 配置已渲染：HTTP'
fi
echo "      后端上游：$(read_env BACKEND_UPSTREAM)"

# [4/4] 起容器并校验配置
echo '[4/4] docker compose up -d ...'
docker compose up -d

if TEST_OUT="$(docker compose exec -T zhishu-web nginx -t 2>&1)"; then
  echo '      nginx -t 通过'
else
  echo '[ERROR] nginx -t 失败：' >&2
  echo "$TEST_OUT" >&2
  echo "        请检查 .env 的 BACKEND_UPSTREAM / NGINX_HTTP_PORT 与 nginx/optional 模板" >&2
  exit 1
fi

WEB_PORT="$(read_env WEB_PORT)"; WEB_PORT="${WEB_PORT:-80}"
HTTPS_PORT="$(read_env HTTPS_PORT)"; HTTPS_PORT="${HTTPS_PORT:-443}"
echo ''
echo "站点：      curl -I http://localhost:${WEB_PORT}/"
echo "经反代后端：curl http://localhost:${WEB_PORT}/api/v1/system/health"
if [ "$ENABLE_HTTPS" = "true" ]; then
  echo "HTTPS：    curl -I https://localhost:${HTTPS_PORT}/"
fi
echo '状态与健康：docker compose ps / docker compose logs -f zhishu-web'
echo '更新方式：  覆盖 web/ 内容即刻生效；改 nginx 模板或 .env 后重跑本脚本'
