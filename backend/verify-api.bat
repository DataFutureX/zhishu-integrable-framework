@echo off
chcp 65001 >nul 2>nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   YunQi Platform - API IT Verify
echo ========================================
echo.

set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"

call mvn -pl yqap-core -am verify -Dconsole.encoding=UTF-8
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" (
    echo [ERROR] verify failed, exit code: %EXIT_CODE%
) else (
    echo [OK] Report: docs\api-test-report\index.html
)
pause
endlocal & exit /b %EXIT_CODE%