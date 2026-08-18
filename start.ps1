#Requires -Version 5.0
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

function Write-Banner {
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  YunQi Platform - Start All" -ForegroundColor Cyan
    Write-Host "  Frontend + Backend" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Command not found: $Name (install it and add to PATH)"
    }
}

Write-Banner

try {
    Assert-Command 'mvn'
    Assert-Command 'node'
    Assert-Command 'npm'
    Write-Host "[OK] node $(node -v)  npm $(npm -v)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

$frontendDir = Join-Path $PSScriptRoot 'frontend'
$backendDir = Join-Path $PSScriptRoot 'backend'

if (-not (Test-Path (Join-Path $backendDir 'pom.xml'))) {
    Write-Host "[ERROR] Missing backend\pom.xml" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
if (-not (Test-Path (Join-Path $frontendDir 'package.json'))) {
    Write-Host "[ERROR] Missing frontend\package.json" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Host "[INFO] Installing frontend dependencies..." -ForegroundColor Yellow
    Push-Location $frontendDir
    try {
        & npm install
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    } finally {
        Pop-Location
    }
    Write-Host "[OK] Frontend dependencies installed" -ForegroundColor Green
    Write-Host ""
}

Write-Host "[START] Backend window (mvn spring-boot:run)..." -ForegroundColor Cyan
$backendCmd = @(
    'chcp 65001 >nul'
    'title YQAP-Backend'
    'set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8'
    'mvn -pl yqap-core -am spring-boot:run -DskipTests "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"'
) -join ' && '
Start-Process -FilePath 'cmd.exe' `
    -WorkingDirectory $backendDir `
    -ArgumentList '/k', $backendCmd

Start-Sleep -Seconds 2

Write-Host "[START] Frontend window (npm run dev)..." -ForegroundColor Cyan
Start-Process -FilePath 'cmd.exe' `
    -WorkingDirectory $frontendDir `
    -ArgumentList '/k', 'title YQAP-Frontend && npm run dev'

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Launched 2 new console windows" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Frontend: http://localhost:3000"
Write-Host "  Backend:  http://localhost:8080"
Write-Host "  Swagger:  http://localhost:8080/swagger-ui.html"
Write-Host ""
Write-Host "  Check taskbar for new CMD windows."
Write-Host "  Close those windows to stop services."
Write-Host "========================================"
Write-Host ""
Read-Host "Press Enter to close this window"
