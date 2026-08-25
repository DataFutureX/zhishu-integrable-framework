#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

echo "========================================"
echo "  ZhiShu Integrable Framework - Dev Start"
echo "========================================"
echo ""

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] Maven not found. Install Maven and add it to PATH."
  exit 1
fi

# Unix 终端通常为 UTF-8
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"

echo "[1/3] mvn clean..."
mvn clean

echo ""
echo "[2/3] mvn compile..."
mvn compile -DskipTests

echo ""
echo "[3/3] spring-boot:run..."
echo ""
mvn -pl zhishu-core -am spring-boot:run -DskipTests \
  -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"
