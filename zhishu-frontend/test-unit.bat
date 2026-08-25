@echo off
chcp 65001 >nul 2>nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   YunQi Platform - Frontend Unit Test
echo ========================================
echo.
echo   Runs: npm run test
echo   Report: docs\unit-test-report\index.html
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js not found. Install Node.js first.
    pause
    exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
    echo [ERROR] npm not found.
    pause
    exit /b 1
)

if not exist "node_modules\" (
    echo [INFO] Installing dependencies...
    call npm install
    if errorlevel 1 (
        echo [ERROR] npm install failed.
        pause
        exit /b 1
    )
    echo.
)

call npm run test
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" (
    echo [ERROR] Unit tests failed, exit code: %EXIT_CODE%
) else (
    echo [OK] All unit tests passed
    echo [OK] Report: ..\docs\unit-test-report\index.html
)
pause
endlocal & exit /b %EXIT_CODE%
