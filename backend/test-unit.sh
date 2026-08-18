#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

echo "========================================"
echo "  YunQi Platform - Unit Test"
echo "========================================"
echo ""
echo "  Runs mvn test (MockMvc / unit tests)"
echo "  Does NOT require MySQL test DB"
echo "  For API IT, use: mvn -pl yqap-core -am verify"
echo ""

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] Maven not found. Install Maven and add it to PATH."
  exit 1
fi

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"

mvn test -Dconsole.encoding=UTF-8
echo ""
echo "[OK] All unit tests passed"
