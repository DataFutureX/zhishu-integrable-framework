# 步骤 1（Windows）：构建前端静态站点，并把 dist 产物暂存到 deployment/web/ 供 Linux 上的 nginx 容器挂载
# 本脚本只做构建与暂存，不碰 Docker。
# 用法：
#   .\zhishu-frontend\deployment\scripts\package-web.ps1                # npm run build（含类型检查）
#   .\zhishu-frontend\deployment\scripts\package-web.ps1 -SkipTypeCheck # npx vite build，跳过 vue-tsc
#   .\zhishu-frontend\deployment\scripts\package-web.ps1 -Demo          # npm run build:demo（演示模式）
#   .\zhishu-frontend\deployment\scripts\package-web.ps1 -SkipBuild     # dist 已存在，只做暂存拷贝
[CmdletBinding()]
param(
    [switch]$SkipTypeCheck,
    [switch]$Demo,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$DeployDir = Split-Path -Parent $ScriptDir
# deployment 的上一级即 zhishu-frontend（vite 构建根）
$WebRoot   = Split-Path -Parent $DeployDir
$DistDir   = Join-Path $WebRoot 'dist'
$StageDir  = Join-Path $DeployDir 'web'
$EnvProd   = Join-Path $WebRoot '.env.production'
# 同域反代要求产物里的接口前缀为 /api（nginx 的 location ^~ /api/ 原样透传给后端）
$ApiBase   = '/api'

Write-Host '========================================'
Write-Host '  [Step 1 / Windows] Build Frontend Site'
Write-Host '========================================'

# [1/3] 构建期 API 前缀
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw '未找到 npm，请安装 Node.js 18+ 并加入 PATH'
}
if (-not $SkipBuild -and -not (Test-Path (Join-Path $WebRoot 'node_modules'))) {
    throw "未找到 node_modules，请先在 $WebRoot 执行 npm install"
}
$fileBase = $null
if (Test-Path $EnvProd) {
    $hit = Select-String -Path $EnvProd -Pattern '^\s*VITE_API_BASE_URL\s*=' | Select-Object -First 1
    if ($hit) { $fileBase = ($hit.Line -split '=', 2)[1].Trim().Trim('"', "'") }
}
if ($fileBase -eq $ApiBase) {
    Write-Host "[1/3] VITE_API_BASE_URL=$ApiBase（.env.production）"
}
else {
    $env:VITE_API_BASE_URL = $ApiBase
    Write-Host "[1/3] .env.production 中为 '$fileBase'，本次用进程变量覆盖为 $ApiBase（Vite 中进程环境变量优先）"
}

# [2/3] 构建
Push-Location $WebRoot
try {
    if ($SkipBuild) {
        Write-Host '[2/3] 跳过构建（-SkipBuild）'
    }
    elseif ($Demo) {
        Write-Host '[2/3] npm run build:demo ...'
        & npm run build:demo
    }
    elseif ($SkipTypeCheck) {
        Write-Host '[2/3] npx vite build（跳过 vue-tsc 类型检查）...'
        & npx vite build
    }
    else {
        Write-Host '[2/3] npm run build ...'
        & npm run build
    }
    if (-not $SkipBuild -and $LASTEXITCODE -ne 0) { throw "前端构建失败（exit=$LASTEXITCODE）" }
}
finally { Pop-Location }

# [3/3] 暂存到构建目录（保留 .gitkeep 占位）
if (-not (Test-Path (Join-Path $DistDir 'index.html'))) {
    throw "未找到 $DistDir\index.html，请先不带 -SkipBuild 运行本脚本"
}
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
Get-ChildItem -Path $StageDir -Force | Where-Object { $_.Name -ne '.gitkeep' } | Remove-Item -Recurse -Force
Copy-Item -Path (Join-Path $DistDir '*') -Destination $StageDir -Recurse -Force

$files = Get-ChildItem -Path $StageDir -Recurse -File -Force | Where-Object { $_.Name -ne '.gitkeep' }
$totalMb = [math]::Round(($files | Measure-Object -Property Length -Sum).Sum / 1MB, 1)
Write-Host "[3/3] 已暂存 $($files.Count) 个文件（$totalMb MB）-> $StageDir"
if (-not (Test-Path (Join-Path $StageDir 'assets'))) {
    Write-Warning 'web/ 下没有 assets 目录，请确认构建产物是否完整'
}

Write-Host ''
Write-Host '步骤 2（Linux）：把 deployment 目录传到服务器后执行 ./scripts/deploy-web.sh'
Write-Host '  scp -r zhishu-frontend\deployment user@server:~/zhishu-frontend/'
Write-Host '  注意：web/ 内容已被 .gitignore 忽略，无法通过 git 同步，需直接传文件'
Write-Host ''
Write-Host '后续更新：只把新 web/ 内容覆盖到服务器同目录即可生效，无需重启容器（index.html 已设 no-cache）'
