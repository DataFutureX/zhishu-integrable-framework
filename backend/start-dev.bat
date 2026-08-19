@echo off
chcp 65001 >nul 2>nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   YunQi Platform Backend - Dev Start
echo ========================================
echo.

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven not found. Install Maven and add it to PATH.
    pause
    exit /b 1
)

REM Console UTF-8 ^(requires chcp 65001 above^)
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"

echo [1/3] mvn clean...
call mvn clean
if errorlevel 1 (
    echo.
    echo [ERROR] mvn clean failed.
    pause
    exit /b 1
)

echo.
echo [2/3] mvn compile...
call mvn compile -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Compile failed.
    pause
    exit /b 1
)

echo.
echo [3/3] spring-boot:run...
echo.
call mvn -pl zhishu-core -am spring-boot:run -DskipTests "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo [ERROR] Startup failed, exit code: %EXIT_CODE%
)
pause
endlocal & exit /b %EXIT_CODE%