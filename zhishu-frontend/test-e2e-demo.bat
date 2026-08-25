@echo off
chcp 65001 >nul 2>nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   YunQi Platform - Frontend E2E Smoke
echo   Mode: demo
echo ========================================
echo.
echo   Runs: npm run test:e2e:demo
echo   Report: docs\e2e-test-report\index.html
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

echo [INFO] Need Chromium once: npx playwright install chromium
echo.

call npm run test:e2e:demo
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" (
    echo [ERROR] E2E smoke failed, exit code: %EXIT_CODE%
    echo [INFO] Report may still exist at docs\e2e-test-report\
) else (
    echo [OK] E2E smoke passed
    echo [OK] Report: ..\docs\e2e-test-report\index.html
)
pause
endlocal & exit /b %EXIT_CODE%
