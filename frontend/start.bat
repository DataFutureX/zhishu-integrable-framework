@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   云起应用平台 - 启动脚本
echo ========================================
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Node.js，请先安装 Node.js
    echo 下载地址: https://nodejs.org/
    pause
    exit /b 1
)

echo [信息] Node.js 版本:
call node -v
echo.

where npm >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 npm
    pause
    exit /b 1
)

echo [信息] npm 版本:
call npm -v
echo.

if not exist "node_modules\" (
    echo [提示] 首次运行，正在安装依赖...
    call npm install
    if errorlevel 1 (
        echo [错误] 依赖安装失败，请检查网络或手动运行 npm install
        pause
        exit /b 1
    )
    echo.
    echo [成功] 依赖安装完成
    echo.
) else (
    echo [提示] 检测到已安装的依赖
    echo.
)

set "PORT=3000"
if exist ".env.development" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env.development") do (
        if /i "%%~a"=="VITE_PORT" set "PORT=%%~b"
    )
)

echo 请选择启动方式:
echo   1. 开发模式 ^(联调后端^)     npm run dev
echo   2. 演示模式 ^(纯前端 Mock^)   npm run dev:demo
echo   0. 退出
echo.
set /p CHOICE=请输入选项 (0-2，默认 1): 
if "%CHOICE%"=="" set "CHOICE=1"

if "%CHOICE%"=="0" (
    echo 已取消
    exit /b 0
)

if "%CHOICE%"=="2" (
    set "NPM_SCRIPT=dev:demo"
    set "MODE_LABEL=演示模式"
) else (
    set "NPM_SCRIPT=dev"
    set "MODE_LABEL=开发模式"
)

echo.
echo ========================================
echo   正在启动%MODE_LABEL%...
echo ========================================
echo.
echo 访问地址: http://localhost:%PORT%
if "%CHOICE%"=="2" (
    echo 演示账号: demo / demo123
) else (
    echo 后端 API: 见 .env.development 中 VITE_API_BASE_URL
)
echo.
echo [提示] 按 Ctrl+C 可停止服务器
echo.

call npm run %NPM_SCRIPT%
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" (
    echo [错误] 启动失败，退出码: %EXIT_CODE%
    pause
)
endlocal & exit /b %EXIT_CODE%
