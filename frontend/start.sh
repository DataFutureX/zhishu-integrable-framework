#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "========================================"
echo "  知枢可集成框架 - 启动脚本"
echo "========================================"
echo ""

if ! command -v node >/dev/null 2>&1; then
    echo "[错误] 未检测到 Node.js，请先安装 Node.js"
    echo "下载地址: https://nodejs.org/"
    exit 1
fi

echo "[信息] Node.js 版本:"
node -v
echo ""

if ! command -v npm >/dev/null 2>&1; then
    echo "[错误] 未检测到 npm"
    exit 1
fi

echo "[信息] npm 版本:"
npm -v
echo ""

if [ ! -d "node_modules" ]; then
    echo "[提示] 首次运行，正在安装依赖..."
    npm install
    echo ""
    echo "[成功] 依赖安装完成"
    echo ""
else
    echo "[提示] 检测到已安装的依赖"
    echo ""
fi

PORT=3000
if [ -f ".env.development" ]; then
    ENV_PORT=$(grep -E '^[[:space:]]*VITE_PORT=' .env.development | tail -n 1 | cut -d '=' -f 2- | tr -d '[:space:]' || true)
    if [[ "$ENV_PORT" =~ ^[0-9]+$ ]]; then
        PORT="$ENV_PORT"
    fi
fi

echo "请选择启动方式:"
echo "  1. 开发模式 (联调后端)     npm run dev"
echo "  2. 演示模式 (纯前端 Mock)  npm run dev:demo"
echo "  0. 退出"
echo ""
read -r -p "请输入选项 (0-2，默认 1): " CHOICE
CHOICE=${CHOICE:-1}

case "$CHOICE" in
    0)
        echo "已取消"
        exit 0
        ;;
    2)
        NPM_SCRIPT="dev:demo"
        MODE_LABEL="演示模式"
        ;;
    *)
        NPM_SCRIPT="dev"
        MODE_LABEL="开发模式"
        ;;
esac

echo ""
echo "========================================"
echo "  正在启动${MODE_LABEL}..."
echo "========================================"
echo ""
echo "访问地址: http://localhost:${PORT}"
if [ "$CHOICE" = "2" ]; then
    echo "演示账号: demo / demo123"
fi
echo ""
echo "[提示] 按 Ctrl+C 可停止服务器"
echo ""

npm run "$NPM_SCRIPT"
