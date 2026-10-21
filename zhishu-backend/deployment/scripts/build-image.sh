#!/usr/bin/env bash
# 步骤 2（Linux）：用 Windows 侧 package-jar.ps1 产出的 jar 构建运行时镜像并启动容器
# 镜像只含运行时（JRE + 字体 + curl）：jar 通过 ./app 只读挂载，日志/上传件通过 ./data 挂载。
# 用法：
#   ./scripts/build-image.sh              # 准备目录 + docker compose build
#   ./scripts/build-image.sh --up         # 构建后 docker compose up -d
#   ./scripts/build-image.sh --up --logs  # 再顺便跟踪日志
# 日常更新不用重新 build：覆盖 app/ 下的 jar，然后 docker compose restart
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$DEPLOY_DIR/app"
DATA_DIR="$DEPLOY_DIR/data"
# 与 Dockerfile 的 JAR_GLOB 保持一致
JAR_GLOB='zhishu-core-*.jar'
# 与 Dockerfile 的 APP_UID 保持一致
APP_UID=10001

DO_UP=0
SHOW_LOGS=0
for arg in "$@"; do
  case "$arg" in
    --up)      DO_UP=1 ;;
    --logs)    SHOW_LOGS=1 ;;
    -h|--help) sed -n '1,8p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[ERROR] 未知参数：$arg" >&2; exit 1 ;;
  esac
done

echo '========================================'
echo '  [Step 2 / Linux] Build Image & Run'
echo '========================================'

command -v docker >/dev/null 2>&1 || { echo "[ERROR] 未安装 docker" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] 需要 Docker Compose v2（docker compose 子命令）" >&2; exit 1; }

# [1/4] 校验 jar（只读挂载进容器，无需改名或归一化）
JAR="$(ls -1t "$APP_DIR"/$JAR_GLOB 2>/dev/null | grep -v '\.original$' | head -n 1 || true)"
if [ -z "$JAR" ]; then
  echo "[ERROR] 未找到 jar：$APP_DIR/$JAR_GLOB" >&2
  echo "        请先在 Windows 执行 deployment/scripts/package-jar.ps1，再把 jar 放到本机 deployment/app/" >&2
  echo "        （app/*.jar 被 .gitignore 忽略，不能靠 git 同步）" >&2
  exit 1
fi
JAR_COUNT="$(ls -1 "$APP_DIR"/$JAR_GLOB 2>/dev/null | grep -vc '\.original$' || true)"
echo "[1/4] jar 就绪：app/$(basename "$JAR")（$(du -h "$JAR" | cut -f1)），将以只读挂载提供给容器"
if [ "${JAR_COUNT:-1}" -gt 1 ]; then
  echo "[WARN] app/ 下有 $JAR_COUNT 个 jar，容器会取修改时间最新的 $(basename "$JAR")；建议清理旧 jar 避免歧义"
fi

# [2/4] 准备宿主机数据目录（容器内 /app/logs 与 /app/uploads 的落盘位置）
mkdir -p "$DATA_DIR/logs" "$DATA_DIR/uploads"
if [ "$(id -u)" = "0" ]; then
  chown -R "$APP_UID:$APP_UID" "$DATA_DIR"
  echo "[2/4] data/ 就绪，已授权给 uid $APP_UID"
else
  echo "[2/4] data/ 就绪；若容器写日志/上传失败，请执行：sudo chown -R $APP_UID:$APP_UID $DATA_DIR"
fi

# [3/4] 校验 .env（compose 的 env_file 必须有该文件）
if [ ! -f "$DEPLOY_DIR/.env" ]; then
  echo "[ERROR] 缺少 $DEPLOY_DIR/.env，请先执行：cp $DEPLOY_DIR/.env.example $DEPLOY_DIR/.env 并填写必填项" >&2
  exit 1
fi
echo '[3/4] .env 就绪'

# [4/4] 构建镜像（上下文为空，只有 Dockerfile 变化时才会产生新层）
echo '[4/4] docker compose build ...'
cd "$DEPLOY_DIR"
docker compose build

if [ "$DO_UP" -eq 1 ]; then
  echo '      docker compose up -d ...'
  docker compose up -d
  PORT="$(grep -E '^APP_PORT=' .env | cut -d= -f2)"
  echo "启动完成，健康检查：curl http://localhost:${PORT:-8180}/api/v1/system/health（首启 start_period 90s）"
  echo '更新方式：覆盖 app/ 下的 jar → docker compose restart'
fi

if [ "$SHOW_LOGS" -eq 1 ]; then
  docker compose logs -f zhishu-app
else
  echo '后续：docker compose ps / docker compose logs -f zhishu-app'
fi
