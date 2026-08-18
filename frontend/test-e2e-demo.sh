#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "========================================"
echo "  云起应用平台 - 前端冒烟（演示模式）"
echo "========================================"
echo ""
echo "  执行 Playwright demo 项目"
echo "  报告写入: docs/e2e-test-report/index.html"
echo ""

if ! command -v node >/dev/null 2>&1; then
  echo "[错误] 未检测到 Node.js，请先安装 Node.js"
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "[错误] 未检测到 npm"
  exit 1
fi

if [ ! -d node_modules ]; then
  echo "[提示] 首次运行，正在安装依赖..."
  npm install
  echo ""
fi

echo "[提示] 确保已安装 Chromium: npx playwright install chromium"
echo ""

npm run test:e2e:demo
echo ""
echo "[成功] 冒烟测试通过"
echo "[成功] 报告: ../docs/e2e-test-report/index.html"
