<#
.SYNOPSIS
  Link dsh-plugin-account into a DSH profile and add it to the bundle list.

.DESCRIPTION
  Local-link install: creates a junction for the plugin package inside the
  profile's node_modules (so edits apply immediately), adds the dependency and
  the `dsh.profile.bundles` entry to the profile's package.json, and writes a
  timestamped backup of that file before touching it.

  A running host watches the profile manifest, so the bundle change recomposes
  the plugin tree without a restart. The Web UI then asks for the account login.

.EXAMPLE
  pwsh -File scripts/install-profile.ps1 -ProfileDir "$env:DSH_PROFILE_DIR"

.EXAMPLE
  pwsh -File scripts/install-profile.ps1 -ProfileDir "$env:DSH_PROFILE_DIR" -Remove
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$ProfileDir,
  [string]$PluginDir = (Split-Path -Parent $PSScriptRoot),
  [switch]$Remove
)

$ErrorActionPreference = 'Stop'
$packageName = 'dsh-plugin-account'
$manifestPath = Join-Path $ProfileDir 'package.json'
if (-not (Test-Path $manifestPath)) { throw "no package.json under $ProfileDir" }
$PluginDir = (Resolve-Path $PluginDir).Path
if (-not (Test-Path (Join-Path $PluginDir 'index.js'))) { throw "$PluginDir does not look like $packageName" }

$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$bundles = @($manifest.dsh.profile.bundles)
$nodeModules = Join-Path $ProfileDir 'node_modules'
$linkPath = Join-Path $nodeModules $packageName

if ($Remove) {
  if (Test-Path $linkPath) { Remove-Item $linkPath -Force -Recurse }
  $manifest.dsh.profile.bundles = @($bundles | Where-Object { $_ -ne $packageName })
  if ($manifest.dependencies.PSObject.Properties.Name -contains $packageName) {
    $manifest.dependencies.PSObject.Properties.Remove($packageName)
  }
  $manifest | ConvertTo-Json -Depth 20 | Set-Content $manifestPath -Encoding utf8
  Write-Host "removed $packageName from $ProfileDir"
  return
}

$backup = "$manifestPath.bak-$(Get-Date -Format 'yyyyMMddHHmmss')"
Copy-Item $manifestPath $backup
Write-Host "backed up manifest to $backup"

New-Item -ItemType Directory -Force -Path $nodeModules | Out-Null
if (Test-Path $linkPath) {
  $existing = Get-Item $linkPath -Force
  if ($existing.LinkType -ne 'Junction') { throw "$linkPath exists and is not a junction; remove it first" }
  Remove-Item $linkPath -Force
}
New-Item -ItemType Junction -Path $linkPath -Target $PluginDir | Out-Null
Write-Host "linked $linkPath -> $PluginDir"

if ($bundles -notcontains $packageName) { $manifest.dsh.profile.bundles = @($bundles + $packageName) }
$dependencies = @{}
if ($manifest.dependencies) {
  foreach ($property in $manifest.dependencies.PSObject.Properties) { $dependencies[$property.Name] = $property.Value }
}
$dependencies[$packageName] = "file:$($PluginDir -replace '\\', '/')"
$manifest.dependencies = [pscustomobject]$dependencies
$manifest | ConvertTo-Json -Depth 20 | Set-Content $manifestPath -Encoding utf8
Write-Host "added $packageName to dsh.profile.bundles"

Write-Host ''
Write-Host 'A running host applies this on its own (profile manifest watch).'
Write-Host "Rollback: pwsh -File scripts/install-profile.ps1 -ProfileDir `"$ProfileDir`" -Remove"
Write-Host "          or restore $backup"
