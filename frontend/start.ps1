#Requires -Version 5.0
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    # 部分宿主不支持改编码，忽略即可
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  云起应用平台 - 启动脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "未检测到 $Name"
    }
}

try {
    Assert-Command 'node'
    Assert-Command 'npm'
    Write-Host "[信息] Node.js 版本: $(node -v)" -ForegroundColor Green
    Write-Host "[信息] npm 版本: $(npm -v)" -ForegroundColor Green
} catch {
    Write-Host "[错误] $_" -ForegroundColor Red
    Write-Host "请先安装 Node.js: https://nodejs.org/" -ForegroundColor Yellow
    Read-Host "按回车键退出"
    exit 1
}

Write-Host ""

if (-not (Test-Path "node_modules")) {
    Write-Host "[提示] 首次运行，正在安装依赖..." -ForegroundColor Yellow
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[错误] 依赖安装失败" -ForegroundColor Red
        Read-Host "按回车键退出"
        exit 1
    }
    Write-Host ""
    Write-Host "[成功] 依赖安装完成" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "[提示] 检测到已安装的依赖" -ForegroundColor Green
    Write-Host ""
}

$port = 3000
if (Test-Path ".env.development") {
    Get-Content ".env.development" | ForEach-Object {
        if ($_ -match '^\s*VITE_PORT\s*=\s*(\d+)\s*$') {
            $port = $Matches[1]
        }
    }
}

Write-Host "请选择操作:" -ForegroundColor Cyan
Write-Host "1. 开发模式 (联调后端)" -ForegroundColor White
Write-Host "2. 演示模式 (纯前端 Mock)" -ForegroundColor White
Write-Host "3. 生产构建" -ForegroundColor White
Write-Host "4. 演示包构建" -ForegroundColor White
Write-Host "5. 预览生产构建" -ForegroundColor White
Write-Host "6. 代码检查 (Lint)" -ForegroundColor White
Write-Host "7. 类型检查" -ForegroundColor White
Write-Host "8. 清理并重新安装" -ForegroundColor White
Write-Host "0. 退出" -ForegroundColor White
Write-Host ""

$choice = Read-Host "请输入选项 (0-8)"

switch ($choice) {
    "1" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  启动开发服务器..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "访问地址: http://localhost:$port" -ForegroundColor Green
        Write-Host "后端 API: 见 .env.development 中 VITE_API_BASE_URL" -ForegroundColor Green
        Write-Host ""
        Write-Host "[提示] 按 Ctrl+C 可停止服务器" -ForegroundColor Yellow
        Write-Host ""
        npm run dev
    }
    "2" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  启动演示模式..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "访问地址: http://localhost:$port" -ForegroundColor Green
        Write-Host "演示账号: demo / demo123" -ForegroundColor Green
        Write-Host ""
        Write-Host "[提示] 按 Ctrl+C 可停止服务器" -ForegroundColor Yellow
        Write-Host ""
        npm run dev:demo
    }
    "3" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  构建生产版本..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        npm run build
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "[成功] 构建完成！输出目录: dist/" -ForegroundColor Green
        }
    }
    "4" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  构建演示包..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        npm run build:demo
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "[成功] 演示包构建完成！输出目录: dist/" -ForegroundColor Green
        }
    }
    "5" {
        if (-not (Test-Path "dist")) {
            Write-Host ""
            Write-Host "[警告] 未找到构建产物，请先执行选项 3 或 4" -ForegroundColor Yellow
            Read-Host "按回车键继续"
        } else {
            Write-Host ""
            Write-Host "========================================" -ForegroundColor Cyan
            Write-Host "  预览构建产物..." -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            Write-Host ""
            npm run preview
        }
    }
    "6" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  执行代码检查..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        npm run lint
    }
    "7" {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "  执行类型检查..." -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        npm run type-check
    }
    "8" {
        Write-Host ""
        Write-Host "[提示] 正在清理..." -ForegroundColor Yellow
        if (Test-Path "node_modules") {
            Remove-Item -Recurse -Force "node_modules"
            Write-Host "[信息] 已删除 node_modules" -ForegroundColor Green
        }
        if (Test-Path "dist") {
            Remove-Item -Recurse -Force "dist"
            Write-Host "[信息] 已删除 dist" -ForegroundColor Green
        }
        Write-Host ""
        Write-Host "[提示] 正在重新安装依赖..." -ForegroundColor Yellow
        npm install
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Host "[成功] 依赖安装完成" -ForegroundColor Green
        }
    }
    "0" {
        Write-Host ""
        Write-Host "再见！" -ForegroundColor Green
        exit 0
    }
    default {
        Write-Host ""
        Write-Host "[错误] 无效的选项" -ForegroundColor Red
    }
}

Write-Host ""
Read-Host "按回车键退出"
