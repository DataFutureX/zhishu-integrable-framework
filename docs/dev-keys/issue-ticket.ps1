# 本地签发 SSO Ticket 并打印回调 URL
# 用法（仓库根目录）:
#   .\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin
#   .\docs\dev-keys\issue-ticket.ps1 -Issuer shuzhi-iot -Username admin -WebBase http://localhost:3000 -Redirect /home/dashboard
#   .\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin -Alg SM2

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('wanxiang', 'shuzhi-iot')]
    [string]$Issuer,

    [Parameter(Mandatory = $true)]
    [string]$Username,

    [string]$WebBase = 'http://localhost:3000',

    [string]$Redirect = '/home/dashboard',

    [ValidateSet('RS256', 'SM2')]
    [string]$Alg = 'RS256'
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $root

$javaFile = Join-Path $PSScriptRoot 'IssueSsoTicket.java'
$classDir = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $classDir | Out-Null

$cp = $classDir
if ($Alg -eq 'SM2') {
    $bcJar = $null
    $candidates = @(
        (Join-Path $env:USERPROFILE '.m2\repository\org\bouncycastle\bcprov-jdk18on\1.78.1\bcprov-jdk18on-1.78.1.jar'),
        'D:\MavenRepository\org\bouncycastle\bcprov-jdk18on\1.78.1\bcprov-jdk18on-1.78.1.jar'
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $bcJar = $c; break }
    }
    if (-not $bcJar) {
        throw 'SM2 签发需要 BouncyCastle jar（bcprov-jdk18on），请先在 backend 执行 mvn 依赖下载'
    }
    $cp = "$classDir;$bcJar"
}

Write-Host "编译 IssueSsoTicket..."
javac -encoding UTF-8 -d $classDir $javaFile
if ($LASTEXITCODE -ne 0) {
    throw 'javac 失败，请确认已安装 JDK 并加入 PATH'
}

Write-Host "签发 Ticket: iss=$Issuer username=$Username alg=$Alg"
java -cp $cp IssueSsoTicket $Issuer $Username $WebBase $Redirect $Alg
