#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

echo "========================================"
echo "  云起应用平台 - 一键启动（前后端）"
echo "========================================"
echo ""

need_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "[错误] 未检测到 $1"
        exit 1
    fi
}

need_cmd mvn
need_cmd node
need_cmd npm

echo "[信息] Node.js: $(node -v)  npm: $(npm -v)"
echo ""

if [ ! -d "frontend/node_modules" ]; then
    echo "[提示] 首次运行，正在安装前端依赖..."
    (cd frontend && npm install)
    echo "[成功] 前端依赖安装完成"
    echo ""
fi

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo ""
    echo "[停止] 正在结束前后端进程..."
    if [ -n "${FRONTEND_PID}" ] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
        kill "${FRONTEND_PID}" 2>/dev/null || true
    fi
    if [ -n "${BACKEND_PID}" ] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
        kill "${BACKEND_PID}" 2>/dev/null || true
    fi
    wait 2>/dev/null || true
    echo "[完成] 已停止"
}

trap cleanup EXIT INT TERM

mkdir -p logs

echo "[启动] 后端: mvn -pl yqap-core -am spring-boot:run"
(
    cd "${ROOT}/backend"
    mvn -pl yqap-core -am spring-boot:run -DskipTests
) >"${ROOT}/logs/yqap-backend-dev.log" 2>&1 &
BACKEND_PID=$!
echo "  PID=${BACKEND_PID}  日志: logs/yqap-backend-dev.log"

sleep 2

echo "[启动] 前端: npm run dev"
(
    cd "${ROOT}/frontend"
    npm run dev
) >"${ROOT}/logs/yqap-frontend-dev.log" 2>&1 &
FRONTEND_PID=$!
echo "  PID=${FRONTEND_PID}  日志: logs/yqap-frontend-dev.log"

echo ""
echo "========================================"
echo "  前后端已在后台启动"
echo "========================================"
echo "  前端: http://localhost:3000"
echo "  后端: http://localhost:8080"
echo "  API 文档: http://localhost:8080/swagger-ui.html"
echo ""
echo "  查看日志: tail -f logs/yqap-*-dev.log"
echo "  按 Ctrl+C 停止全部服务"
echo "========================================"
echo ""

# 任一子进程退出则结束
while kill -0 "${BACKEND_PID}" 2>/dev/null && kill -0 "${FRONTEND_PID}" 2>/dev/null; do
    sleep 2
done

echo "[警告] 有服务已退出，准备收尾..."
exit 1
