#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "========================================"
echo "  云起应用平台 - 前端单元测试"
echo "========================================"
echo ""
echo "  执行 Vitest（permission / dynamicRoutes / format）"
echo "  报告写入: docs/unit-test-report/index.html"
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

npm run test
echo ""
echo "[成功] 单元测试通过"
echo "[成功] 报告: ../docs/unit-test-report/index.html"
