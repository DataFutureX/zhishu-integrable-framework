# 本地签发 SSO Ticket 并打印回调 URL
# 用法（仓库根目录）:
#   .\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin
#   .\docs\dev-keys\issue-ticket.ps1 -Issuer shuzhi-iot -Username admin -WebBase http://localhost:3000 -Redirect /home/dashboard

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('wanxiang', 'shuzhi-iot')]
    [string]$Issuer,

    [Parameter(Mandatory = $true)]
    [string]$Username,

    [string]$WebBase = 'http://localhost:3000',

    [string]$Redirect = '/home/dashboard'
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $root

$javaFile = Join-Path $PSScriptRoot 'IssueSsoTicket.java'
$classDir = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $classDir | Out-Null

Write-Host "编译 IssueSsoTicket..."
javac -encoding UTF-8 -d $classDir $javaFile
if ($LASTEXITCODE -ne 0) {
    throw 'javac 失败，请确认已安装 JDK 并加入 PATH'
}

Write-Host "签发 Ticket: iss=$Issuer username=$Username"
java -cp $classDir IssueSsoTicket $Issuer $Username $WebBase $Redirect
