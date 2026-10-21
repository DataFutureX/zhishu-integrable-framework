# 步骤 1（Windows）：Maven 打包 zhishu-core 可执行 jar，并暂存到 deployment/app/ 供 Linux 侧构建镜像
# 本脚本只做打包，不碰 Docker。
# 用法：
#   .\zhishu-backend\deployment\scripts\package-jar.ps1              # clean package -DskipTests
#   .\zhishu-backend\deployment\scripts\package-jar.ps1 -Tests       # 打包时执行单元测试
#   .\zhishu-backend\deployment\scripts\package-jar.ps1 -SkipMaven   # 已有 target 产物，仅暂存拷贝
[CmdletBinding()]
param(
    [switch]$Tests,
    [switch]$SkipMaven
)

$ErrorActionPreference = 'Stop'

$ScriptDir  = $PSScriptRoot
$DeployDir  = Split-Path -Parent $ScriptDir
# deployment 与 zhishu-core 同级，上一级即 zhishu-backend（Maven 反应堆根）
$BackendDir = Split-Path -Parent $DeployDir
$TargetDir  = Join-Path $BackendDir 'zhishu-core\target'
$StageDir   = Join-Path $DeployDir 'app'
# 暂存为固定名：服务器上每次覆盖同一个文件即可升级（容器取 deployment/app 下修改时间最新的 jar）
$StageJar   = Join-Path $StageDir 'zhishu-core-1.0.0.jar'

function Find-BuiltJar {
    if (-not (Test-Path $TargetDir)) { return $null }
    Get-ChildItem -Path $TargetDir -Filter 'zhishu-core-*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

Write-Host '========================================'
Write-Host '  [Step 1 / Windows] Package Backend Jar'
Write-Host '========================================'

# [1/2] Maven 打包
if ($SkipMaven) {
    Write-Host '[1/2] 跳过 Maven（-SkipMaven）'
}
else {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw '未找到 mvn，请安装 Maven 3.9+ 与 JDK 21 并加入 PATH'
    }
    $mvnArgs = @('-q', '-pl', 'zhishu-core', '-am', 'clean', 'package')
    if ($Tests) {
        Write-Host '[1/2] mvn -pl zhishu-core -am clean package（含单元测试）...'
    }
    else {
        $mvnArgs += '-DskipTests'
        Write-Host '[1/2] mvn -pl zhishu-core -am clean package -DskipTests ...'
    }
    Push-Location $BackendDir
    try {
        & mvn @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败（exit=$LASTEXITCODE）" }
    }
    finally { Pop-Location }
}

# [2/2] 暂存到构建上下文
$jar = Find-BuiltJar
if (-not $jar) {
    throw "未找到可执行 jar：$TargetDir\zhishu-core-*.jar（请先不带 -SkipMaven 运行）"
}
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
Copy-Item $jar.FullName $StageJar -Force
$sizeMb = [math]::Round($jar.Length / 1MB, 1)
Write-Host "[2/2] 已暂存 $($jar.Name)（$sizeMb MB）-> $StageJar"

Write-Host ''
Write-Host '步骤 2（Linux）：把 deployment 目录传到服务器后执行 ./scripts/build-image.sh --up'
Write-Host '  scp -r zhishu-backend\deployment user@server:~/zhishu-backend/'
Write-Host '  注意：app/*.jar 已被 .gitignore 忽略，无法通过 git 拉取，需直接传文件'
Write-Host ''
Write-Host '后续更新：只把新 jar 传到 deployment/app/ 覆盖，再 docker compose restart（无需重新 build）'
