<#
.SYNOPSIS
    知枢测试环境一键部署脚本（Windows / PowerShell 7）
.DESCRIPTION
    依据 deploy/test-deploy.md 的约定实现：
      · 后端：构建 jar -> 覆盖服务器 deployment/app/zhishu-core-1.0.0.jar -> 重启容器 zhishu-app -> 健康检查
      · 前端：构建 dist  -> 覆盖服务器 deployment/web 内容（原地覆盖，保留 bind mount）-> 无需重启容器
      · 每次部署先按日期时间备份服务器上的旧产物，失败或需要回滚时用 -Rollback 还原

    运行后会弹出选择菜单：
      1  打包并部署前后端
      2  仅打包并部署前端
      3  仅打包并部署后端
    也可用 -Target 直接指定，便于复用与自动化。

    测试服务器实测端口（与脚本默认值一致）：
      后端  http://192.168.11.14:8180/      容器 zhishu-app
      前端  http://192.168.11.14:8080/      容器 zhishu-web（HTTPS 映射 8443->443）
    注意：前端不是 80，而是 compose 里的 WEB_PORT=8080 / NGINX_HTTP_PORT=8080。

    打包本身复用仓库既有脚本（不在本脚本里重写构建命令）：
      zhishu-backend/deployment/scripts/package-jar.ps1
      zhishu-frontend/deployment/scripts/package-web.ps1
.EXAMPLE
    .\deploy\deploy-test.ps1
    # 交互选择 1/2/3
.EXAMPLE
    .\deploy\deploy-test.ps1 -Target backend -RunTests
    # 仅部署后端，且打包时执行单元测试
.EXAMPLE
    .\deploy\deploy-test.ps1 -Target frontend -Demo
    # 仅部署前端（演示模式构建）
.EXAMPLE
    .\deploy\deploy-test.ps1 -Rollback both
    # 用服务器上的最近一次备份回滚前后端
.EXAMPLE
    .\deploy\deploy-test.ps1 -Check
    # 只检查环境与远端当前状态（容器/jar/站点/健康码），不打包不部署
.EXAMPLE
    .\deploy\deploy-test.ps1 -Target backend -DryRun
    # 只打印将要执行的命令
.EXAMPLE
    .\deploy\deploy-test.ps1 -Check -FixKeyPermissions
    # 先修复 Windows 私钥权限（bad permissions）再做检查
#>
[CmdletBinding()]
param(
    # both | frontend | backend；省略则进入交互菜单
    [ValidateSet('both', 'frontend', 'backend', '')]
    [string]$Target = '',

    # 跳过本地打包，直接用已暂存的产物（deployment/app 与 deployment/web）
    [switch]$SkipBuild,

    # 后端打包时执行单元测试（默认 -DskipTests）
    [switch]$RunTests,

    # 前端用演示模式构建（npm run build:demo）
    [switch]$Demo,

    # 前端构建跳过 vue-tsc 类型检查
    [switch]$SkipTypeCheck,

    # 只打印计划，不执行任何本地/远端命令
    [switch]$DryRun,

    # 跳过部署前的二次确认
    [switch]$Force,

    # 用服务器上的最近备份回滚：both | frontend | backend
    [ValidateSet('', 'both', 'frontend', 'backend')]
    [string]$Rollback = '',

    # 只做环境检查与远端状态打印，不打包、不部署
    [switch]$Check,

    # Windows 上私钥文件权限过宽会被 OpenSSH 拒用（bad permissions），用本开关先备份再收紧
    [switch]$FixKeyPermissions,

    [string]$Server = '192.168.11.14',
    [string]$SshUser = 'root',
    [string]$SshKey = 'D:/LetsWork-Docs/hyf_ed25519',

    # 服务器上的部署目录（与 test-deploy.md 一致）
    [string]$BackendDir = '/home/letswork/zhishu-backend/deployment',
    [string]$FrontendDir = '/home/letswork/zhishu-frontend/deployment',

    [string]$BackendContainer = 'zhishu-app',
    [string]$FrontendContainer = 'zhishu-web',

    [int]$AppPort = 8180,
    # 测试服务器上 zhishu-web 容器实际映射：8080->8080（HTTP）与 8443->443（HTTPS），并非默认 80
    [int]$WebPort = 8080,

    # 后端健康检查轮询次数与间隔（默认 40 x 5s = 200s）
    [int]$HealthRetries = 40,
    [int]$HealthIntervalSeconds = 5
)

$ErrorActionPreference = 'Stop'

# ---------- 路径与常量 ----------
$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDeployLocal = Join-Path $RepoRoot 'zhishu-backend/deployment'
$FrontendDeployLocal = Join-Path $RepoRoot 'zhishu-frontend/deployment'
$JarScript = Join-Path $BackendDeployLocal 'scripts/package-jar.ps1'
$WebScript = Join-Path $FrontendDeployLocal 'scripts/package-web.ps1'
$StageJar = Join-Path $BackendDeployLocal 'app/zhishu-core-1.0.0.jar'
$StageWeb = Join-Path $FrontendDeployLocal 'web'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$SshTarget = "$SshUser@$Server"
$SshOpts = @('-i', $SshKey, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-o', 'StrictHostKeyChecking=accept-new')

# ---------- 输出 helper ----------
function Step($message) {
    Write-Host "`n==== $message ====" -ForegroundColor Cyan
}
function Info($message) {
    Write-Host "  $message"
}
function Warn($message) {
    Write-Host "  ! $message" -ForegroundColor Yellow
}
function Fail($message) {
    Write-Host "  x $message" -ForegroundColor Red
}

# ---------- 远端执行 helper ----------
function Invoke-Remote {
    param([string]$Command, [switch]$Capture)

    if ($DryRun) {
        Write-Host "  [dry-run] ssh $SshTarget `"$Command`"" -ForegroundColor DarkGray
        return ''
    }
    $args = $SshOpts + @($SshTarget, $Command)
    if ($Capture) {
        $out = & ssh @args 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "远端命令失败（exit=$LASTEXITCODE）：$Command`n$($out -join [Environment]::NewLine)"
        }
        return ($out | Out-String).Trim()
    }
    & ssh @args
    if ($LASTEXITCODE -ne 0) {
        throw "远端命令失败（exit=$LASTEXITCODE）：$Command"
    }
    return ''
}

function Copy-ToRemote {
    param([string]$Source, [string]$RemotePath, [string]$ExtraKeyFile = $SshKey)

    if ($DryRun) {
        Write-Host "  [dry-run] scp `"$Source`" -> ${SshTarget}:${RemotePath}" -ForegroundColor DarkGray
        return
    }
    $args = @('-i', $ExtraKeyFile, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new', '-O', '-r', $Source, "${SshTarget}:${RemotePath}")
    & scp @args
    if ($LASTEXITCODE -ne 0) {
        throw "上传失败（exit=$LASTEXITCODE）：$Source -> ${SshTarget}:${RemotePath}"
    }
}

# ---------- 环境检查 ----------
function Test-Preflight {
    Step '0. 环境检查'
    $missing = @()
    foreach ($tool in @('ssh', 'scp', 'tar')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { $missing += $tool }
    }
    if ($missing.Count -gt 0) {
        throw "缺少必需命令：$($missing -join ', ')（Windows 需启用 OpenSSH 客户端）"
    }
    if (-not (Test-Path $SshKey)) {
        throw "找不到 SSH 私钥：$SshKey（可用 -SshKey 指定）"
    }
    if (-not (Test-Path $JarScript)) { throw "找不到后端打包脚本：$JarScript" }
    if (-not (Test-Path $WebScript)) { throw "找不到前端打包脚本：$WebScript" }
    if (-not $SkipBuild) {
        if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw '未找到 mvn（后端打包需要 Maven + JDK 21）' }
        if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw '未找到 npm（前端打包需要 Node.js 18+）' }
    }
    Info "私钥：$SshKey"
    Info "目标：$SshTarget"
    if ($FixKeyPermissions) {
        if ($DryRun) {
            Write-Host '  [dry-run] icacls 私钥 /save + /inheritance:r /grant:r 当前用户:(R)' -ForegroundColor DarkGray
        }
        else {
            $aclBackup = Join-Path $env:TEMP ("key-acl-backup-" + $Stamp + '.txt')
            & icacls $SshKey /save $aclBackup | Out-Null
            & icacls $SshKey /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
            Info "私钥权限已收紧为仅当前用户只读；原 ACL 备份于 $aclBackup"
        }
    }
    try {
        $probe = Invoke-Remote -Command 'echo ok' -Capture
        if (-not $DryRun -and $probe -notmatch 'ok') { throw "SSH 返回异常：$probe" }
        Info 'SSH 连通正常'
        Info "远端时间：$(Invoke-Remote -Command 'date +"%F %T"' -Capture)"
    }
    catch {
        if ($DryRun) {
            Warn 'DryRun 模式忽略连通性结果'
            return
        }
        Fail "SSH 连接失败：$($_.Exception.Message)"
        Warn '若提示 bad permissions / UNPROTECTED PRIVATE KEY FILE，请重跑并加 -FixKeyPermissions'
        Warn ('手工修复：icacls "' + $SshKey + '" /inheritance:r /grant:r "' + $env:USERNAME + ':(R)"')
        Warn '若为 Permission denied (publickey)，确认该私钥已加入服务器 root 的 authorized_keys'
        throw
    }
}

# ---------- 选择菜单 ----------
function Select-Target {
    if ($Target) { return $Target }
    Write-Host ''
    Write-Host '=========================================' -ForegroundColor Cyan
    Write-Host '  知枢测试环境部署  目标服务器 ' + $SshTarget -ForegroundColor Cyan
    Write-Host '=========================================' -ForegroundColor Cyan
    Write-Host '  1  打包并部署前后端'
    Write-Host '  2  仅打包并部署前端'
    Write-Host '  3  仅打包并部署后端'
    Write-Host '  0  退出'
    while ($true) {
        $choice = Read-Host '请选择 [1/2/3/0]'
        switch ($choice) {
            '1' { return 'both' }
            '2' { return 'frontend' }
            '3' { return 'backend' }
            '0' { return '' }
            default { Warn '请输入 1 / 2 / 3 / 0' }
        }
    }
}

function Confirm-Deploy {
    param([string]$Which)

    if ($DryRun -or $Force) { return }
    Write-Host ''
    Warn "即将部署到测试服务器 $SshTarget ：$Which"
    Warn "远端旧产物会先备份为 *.bak-$Stamp"
    $answer = Read-Host '确认继续？[y/N]'
    if ($answer -notmatch '^[yY]') {
        Write-Host '已取消，未对服务器做任何修改。' -ForegroundColor Yellow
        exit 0
    }
}

# ---------- 本地打包 ----------
function Invoke-BackendBuild {
    Step '1a. 后端打包（Maven）'
    if ($SkipBuild) {
        Info '跳过打包（-SkipBuild），使用已有暂存 jar'
    }
    elseif ($DryRun) {
        Write-Host "  [dry-run] & $JarScript" -ForegroundColor DarkGray
    }
    else {
        Push-Location $RepoRoot
        try {
            if ($RunTests) {
                & $JarScript -Tests
            }
            else {
                & $JarScript
            }
            if ($LASTEXITCODE -ne 0) { throw "后端打包失败（exit=$LASTEXITCODE）" }
        }
        finally { Pop-Location }
    }
    if (-not $DryRun) {
        if (-not (Test-Path $StageJar)) { throw "未找到暂存 jar：$StageJar" }
        $jar = Get-Item $StageJar
        Info "待上传：$($jar.Name)  $([math]::Round($jar.Length / 1MB, 1)) MB  构建于 $($jar.LastWriteTime)"
    }
}

function Invoke-FrontendBuild {
    Step '1b. 前端打包（Vite）'
    if ($SkipBuild) {
        Info '跳过打包（-SkipBuild），使用已有暂存 web/ 产物'
    }
    elseif ($DryRun) {
        Write-Host "  [dry-run] & $WebScript" -ForegroundColor DarkGray
    }
    else {
        Push-Location $RepoRoot
        try {
            if ($Demo) {
                & $WebScript -Demo
            }
            elseif ($SkipTypeCheck) {
                & $WebScript -SkipTypeCheck
            }
            else {
                & $WebScript
            }
            if ($LASTEXITCODE -ne 0) { throw "前端打包失败（exit=$LASTEXITCODE）" }
        }
        finally { Pop-Location }
    }
    if (-not $DryRun) {
        if (-not (Test-Path (Join-Path $StageWeb 'index.html'))) { throw "未找到暂存站点：$StageWeb\index.html" }
        $files = Get-ChildItem -Path $StageWeb -Recurse -File -Force
        Info "待上传：$($files.Count) 个文件  $([math]::Round(($files | Measure-Object -Property Length -Sum).Sum / 1MB, 1)) MB"
    }
}

# ---------- 后端部署 ----------
function Deploy-Backend {
    Step '2. 部署后端'
    $jarName = 'zhishu-core-1.0.0.jar'
    $remoteJar = "$BackendDir/app/$jarName"

    Invoke-Remote -Command "test -d '$BackendDir/app' || mkdir -p '$BackendDir/app'"
    # 按日期备份旧 jar（不存在则跳过）
    Invoke-Remote -Command "if [ -f '$remoteJar' ]; then cp -f '$remoteJar' '${remoteJar}.bak-$Stamp'; echo backed-up; else echo no-previous-jar; fi" -Capture | ForEach-Object { Info "旧 jar：$_" }

    # 先传临时名再原子替换，避免容器启动时读到半截 jar
    Step '2.1 上传 jar'
    Copy-ToRemote -Source $StageJar -RemotePath "${remoteJar}.upload-$Stamp"
    Invoke-Remote -Command "mv -f '${remoteJar}.upload-$Stamp' '$remoteJar' && ls -l '$remoteJar'" -Capture | ForEach-Object { Info $_ }

    Step '2.2 重启容器'
    # 换 jar 只需 restart；改 .env 或 compose 才需要 up -d
    $restart = "cd '$BackendDir' && (docker compose restart || docker restart $BackendContainer)"
    Invoke-Remote -Command $restart | Out-Null
    Info "已重启：$BackendContainer（compose 优先，失败则 docker restart）"

    Step '2.3 健康检查'
    Test-BackendHealth
}

function Test-BackendHealth {
    $url = "http://${Server}:${AppPort}/api/v1/system/health"
    if ($DryRun) {
        Write-Host "  [dry-run] GET $url" -ForegroundColor DarkGray
        return
    }
    for ($i = 1; $i -le $HealthRetries; $i++) {
        Start-Sleep -Seconds ($HealthIntervalSeconds)
        try {
            $resp = Invoke-WebRequest -Uri $url -TimeoutSec 8 -UseBasicParsing
            if ($resp.StatusCode -eq 200) {
                Info "第 $i 次探测成功：$url"
                Write-Host '  ' -NoNewline
                Write-Host $resp.Content.Substring(0, [Math]::Min(220, $resp.Content.Length)) -ForegroundColor Green
                return
            }
        }
        catch {
            Write-Host "  [$i/$HealthRetries] 未就绪：$($_.Exception.Message)" -ForegroundColor DarkGray
        }
    }
    throw "后端健康检查超时（$($HealthRetries * $HealthIntervalSeconds)s）：$url`n请查看容器日志：ssh $SshTarget 'docker logs --tail 200 $BackendContainer'"
}

# ---------- 前端部署 ----------
function Deploy-Frontend {
    Step '3. 部署前端'
    $remoteWeb = "$FrontendDir/web"

    Invoke-Remote -Command "test -d '$FrontendDir' || mkdir -p '$FrontendDir'"

    Step '3.1 打包本地产物为 tar.gz'
    $bundle = Join-Path $env:TEMP "zhishu-web-$Stamp.tar.gz"
    if ($DryRun) {
        Write-Host "  [dry-run] tar -czf $bundle -C `"$StageWeb`" ." -ForegroundColor DarkGray
    }
    else {
        & tar -czf $bundle -C $StageWeb .
        if ($LASTEXITCODE -ne 0) { throw "前端产物打包失败（exit=$LASTEXITCODE）" }
        Info "已打包：$bundle  $([math]::Round((Get-Item $bundle).Length / 1MB, 1)) MB"
    }
    # 关键：nginx 以 bind mount 挂载 ./web，必须原地覆盖目录内容；
    # 若把 web 目录整体重命名再新建，容器内看到的仍是旧 inode（站点不会更新）。
    Step '3.2 备份远端旧站点（原地保留 web 目录）'
    Invoke-Remote -Command "if [ -d '$remoteWeb' ]; then cp -a '$remoteWeb' '${remoteWeb}.bak-$Stamp'; echo backed-up; else mkdir -p '$remoteWeb'; echo created; fi" -Capture | ForEach-Object { Info "旧站点：$_" }

    Step '3.3 上传并解压到临时目录'
    $tmpBundle = "/tmp/zhishu-web-$Stamp.tar.gz"
    Copy-ToRemote -Source $bundle -RemotePath $tmpBundle
    Invoke-Remote -Command "rm -rf '${remoteWeb}.new-$Stamp'; mkdir -p '${remoteWeb}.new-$Stamp'; tar -xzf '$tmpBundle' -C '${remoteWeb}.new-$Stamp' && test -f '${remoteWeb}.new-$Stamp/index.html' && echo extracted" -Capture | ForEach-Object { Info "解压：$_" }

    Step '3.4 原地替换内容'
    Invoke-Remote -Command "find '$remoteWeb' -mindepth 1 -maxdepth 1 -exec rm -rf {} + ; cp -a '${remoteWeb}.new-$Stamp/.' '$remoteWeb/' && rm -rf '${remoteWeb}.new-$Stamp' '$tmpBundle' && ls '$remoteWeb' | head -8" -Capture | ForEach-Object { Info "站点内容：$($_ -replace "`r", '')" }

    Step '3.5 校验（无需重启容器）'
    if (-not $DryRun) {
        $check = Invoke-Remote -Command "docker exec $FrontendContainer sh -c 'test -f /usr/share/nginx/html/index.html && echo in-container-ok'" -Capture
        Info "容器内可见：$check"
        $url = "http://${Server}:${WebPort}/"
        try {
            $resp = Invoke-WebRequest -Uri $url -TimeoutSec 10 -UseBasicParsing
            Info "HTTP $($resp.StatusCode) $url"
        }
        catch {
            Warn "浏览器侧探测失败：$($_.Exception.Message)（请确认 WEB_PORT=$WebPort 与防火墙）"
        }
    }
}

# ---------- 回滚 ----------
function Invoke-Rollback {
    Step "回滚（$Rollback）"
    if ($Rollback -in @('both', 'backend')) {
        $jar = "$BackendDir/app/zhishu-core-1.0.0.jar"
        $latest = Invoke-Remote -Command "ls -1 '${jar}.bak-'* 2>/dev/null | sort | tail -1" -Capture
        if ([string]::IsNullOrWhiteSpace($latest) -and -not $DryRun) {
            Warn '后端没有找到 jar 备份，跳过后端回滚'
        }
        else {
            Invoke-Remote -Command "cp -f '$latest' '$jar' && cd '$BackendDir' && (docker compose restart || docker restart $BackendContainer)" | Out-Null
            Info "已回滚后端到：$latest 并重启容器"
            Test-BackendHealth
        }
    }
    if ($Rollback -in @('both', 'frontend')) {
        $web = "$FrontendDir/web"
        $latestWeb = Invoke-Remote -Command "ls -1d '${web}.bak-'* 2>/dev/null | sort | tail -1" -Capture
        if ([string]::IsNullOrWhiteSpace($latestWeb) -and -not $DryRun) {
            Warn '前端没有找到站点备份，跳过前端回滚'
        }
        else {
            Invoke-Remote -Command "find '$web' -mindepth 1 -maxdepth 1 -exec rm -rf {} + ; cp -a '$latestWeb/.' '$web/'" | Out-Null
            Info "已回滚前端到：$latestWeb（nginx 无需重启）"
        }
    }
}

# ---------- 远端状态（只读） ----------
function Show-RemoteState {
    Step '远端当前状态（只读）'
    Info ("后端 jar：" + (Invoke-Remote -Command "ls -l '$BackendDir/app/zhishu-core-1.0.0.jar' 2>&1 | tail -1" -Capture))
    Info ("后端备份：" + (Invoke-Remote -Command "ls -1 '$BackendDir/app'/*.bak-* 2>/dev/null | wc -l" -Capture) + ' 个')
    Info ("前端站点：" + (Invoke-Remote -Command "ls '$FrontendDir/web' 2>/dev/null | tr '\n' ' '" -Capture))
    Info ("容器：" + (Invoke-Remote -Command "docker ps --format '{{.Names}} {{.Status}}' 2>&1 | tr '\n' ';'" -Capture))
    Info ("后端健康：" + (Invoke-Remote -Command "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$AppPort/api/v1/system/health || true" -Capture))
    Info ("前端首页：" + (Invoke-Remote -Command "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$WebPort/ || true" -Capture))
}

# ---------- 主流程 ----------
if ($Check) {
    Test-Preflight
    Show-RemoteState
    exit 0
}

if ($Rollback) {
    Test-Preflight
    Invoke-Rollback
    exit 0
}

$chosen = Select-Target
if ([string]::IsNullOrWhiteSpace($chosen)) {
    Write-Host '未选择目标，已退出。' -ForegroundColor Yellow
    exit 0
}

$needBackend = $chosen -in @('both', 'backend')
$needFrontend = $chosen -in @('both', 'frontend')
$label = switch ($chosen) {
    'both' { '前后端' }
    'frontend' { '仅前端' }
    'backend' { '仅后端' }
}
Step "部署计划：$label -> $SshTarget"
Info "时间戳：$Stamp"
if ($DryRun) { Warn 'DryRun 模式：只打印命令，不做任何修改' }
Confirm-Deploy $label

Test-Preflight

if ($needBackend) {
    Invoke-BackendBuild
    Deploy-Backend
}
if ($needFrontend) {
    Invoke-FrontendBuild
    Deploy-Frontend
}

Step '完成'
Write-Host '  部署目标：' -NoNewline
Write-Host $label -ForegroundColor Green
Info "备份标记：*-$Stamp"
if ($needBackend) {
    Info "后端备份：${BackendDir}/app/zhishu-core-1.0.0.jar.bak-$Stamp"
    Info "后端日志：ssh -i `"$SshKey`" $SshTarget 'docker logs --tail 200 $BackendContainer'"
}
if ($needFrontend) {
    Info "前端备份：${FrontendDir}/web.bak-$Stamp"
}
Info "回滚命令：.\deploy\deploy-test.ps1 -Rollback $chosen -Force"
Write-Host ''
