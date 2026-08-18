#Requires -Version 5.1
<#
.SYNOPSIS
  Maven/Modulith 拆分迁移：iot 模块、单位归 security、biz 按领域重组
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Root 'pom.xml'))) {
    $Root = 'd:\workspace-qoder\hydro-monitor-backend'
}
Set-Location $Root

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
}

function Move-And-Rewrite {
    param(
        [string]$SrcFile,
        [string]$DestFile,
        [hashtable]$Replacements
    )
    if (-not (Test-Path $SrcFile)) {
        Write-Warning "Missing: $SrcFile"
        return
    }
    Ensure-Dir (Split-Path $DestFile -Parent)
    $content = Get-Content -Path $SrcFile -Raw -Encoding UTF8
    foreach ($key in $Replacements.Keys) {
        $content = $content.Replace($key, $Replacements[$key])
    }
    Set-Content -Path $DestFile -Value $content -Encoding UTF8 -NoNewline
    Remove-Item -Path $SrcFile -Force
}

function Move-Tree-Rewrite {
    param(
        [string[]]$RelativePaths,
        [string]$SrcRoot,
        [string]$DestRoot,
        [hashtable]$Replacements
    )
    foreach ($rel in $RelativePaths) {
        $src = Join-Path $SrcRoot $rel
        $dest = Join-Path $DestRoot $rel
        Move-And-Rewrite -SrcFile $src -DestFile $dest -Replacements $Replacements
    }
}

# ---------- IoT file lists (relative to modules/) ----------
$iotControllers = @(
    'controller/ElementConfigController.java',
    'controller/HeartbeatController.java',
    'controller/RawMessageController.java',
    'controller/TerminalAlertController.java',
    'controller/TerminalController.java',
    'controller/TerminalRemoteConfigController.java',
    'controller/TerminalThresholdController.java',
    'controller/TimedReportController.java'
)
$iotServices = @(
    'service/AlertSseService.java',
    'service/ElementConfigService.java',
    'service/HeartbeatService.java',
    'service/RawMessageService.java',
    'service/TerminalAlertService.java',
    'service/TerminalRemoteConfigService.java',
    'service/TerminalService.java',
    'service/TerminalThresholdService.java',
    'service/TimedReportService.java',
    'service/impl/AlertSseServiceImpl.java',
    'service/impl/ElementConfigServiceImpl.java',
    'service/impl/HeartbeatServiceImpl.java',
    'service/impl/RawMessageServiceImpl.java',
    'service/impl/TerminalAlertServiceImpl.java',
    'service/impl/TerminalRemoteConfigServiceImpl.java',
    'service/impl/TerminalServiceImpl.java',
    'service/impl/TerminalThresholdServiceImpl.java',
    'service/impl/TimedReportServiceImpl.java'
)
$iotMappers = @(
    'mapper/ElementConfigMapper.java',
    'mapper/HeartbeatMapper.java',
    'mapper/RawMessageMapper.java',
    'mapper/TerminalAlertMapper.java',
    'mapper/TerminalElementThresholdMapper.java',
    'mapper/TerminalMapper.java',
    'mapper/TerminalThresholdRuleMapper.java',
    'mapper/TimedReportMapper.java'
)
$iotEntities = @(
    'entity/ElementConfigEntity.java',
    'entity/HeartbeatEntity.java',
    'entity/RawMessageEntity.java',
    'entity/TerminalAlertEntity.java',
    'entity/TerminalElementThresholdEntity.java',
    'entity/TerminalEntity.java',
    'entity/TerminalThresholdRuleEntity.java',
    'entity/TimedReportEntity.java'
)
$iotDtos = @(
    'dto/ElementConfigCreateDTO.java',
    'dto/ElementConfigQueryDTO.java',
    'dto/ElementConfigUpdateDTO.java',
    'dto/HeartbeatQueryDTO.java',
    'dto/RawMessageQueryDTO.java',
    'dto/RemoteConfigSendDTO.java',
    'dto/TerminalAlertQueryDTO.java',
    'dto/TerminalBatchAssignProjectDTO.java',
    'dto/TerminalCreateDTO.java',
    'dto/TerminalElementThresholdSaveDTO.java',
    'dto/TerminalQueryDTO.java',
    'dto/TerminalThresholdBatchSaveDTO.java',
    'dto/TerminalUpdateDTO.java',
    'dto/ThresholdRuleItemDTO.java',
    'dto/TimedReportQueryDTO.java'
)
$iotVos = @(
    'vo/ElementItemVO.java',
    'vo/RawMessageVO.java',
    'vo/RemoteConfigMessageVO.java',
    'vo/TerminalAlertVO.java',
    'vo/TerminalElementThresholdVO.java',
    'vo/TerminalVO.java',
    'vo/ThresholdRuleVO.java',
    'vo/TimedReportVO.java'
)
$iotConstants = @(
    'constant/ThresholdAlertLevelConstants.java',
    'constant/ThresholdRuleTypeConstants.java'
)
$iotSpi = @(
    'spi/ElementConfigQueryApiImpl.java',
    'spi/MonitoringMessageHandlerImpl.java',
    'spi/TerminalCatalogApiImpl.java'
)

$bizSrc = 'hydro-monitor-biz/src/main/java/com/hydro/monitor'
$iotSrc = 'hydro-monitor-iot/src/main/java/com/hydro/monitor/iot'
$secSrc = 'hydro-monitor-security/src/main/java/com/hydro/monitor'

Ensure-Dir $iotSrc

$iotRepl = [ordered]@{
    'package com.hydro.monitor.modules.' = 'package com.hydro.monitor.iot.'
    'import com.hydro.monitor.modules.' = 'import com.hydro.monitor.iot.'
    'import com.hydro.monitor.common.HexUtils' = 'import com.hydro.monitor.iot.common.HexUtils'
    'import com.hydro.monitor.common.ThresholdEvaluator' = 'import com.hydro.monitor.iot.common.ThresholdEvaluator'
    'import com.hydro.monitor.schedule.TerminalOnlineStatusScheduler' = 'import com.hydro.monitor.iot.schedule.TerminalOnlineStatusScheduler'
}
# Keep security shared types on modules.* (UserProjectPermissionService, Result, SecurityUtils, PageResult)
# After blind replace, restore security imports that wrongly became iot.*
$restoreSecurityImports = @(
    @{ From = 'import com.hydro.monitor.iot.service.UserProjectPermissionService'; To = 'import com.hydro.monitor.modules.service.UserProjectPermissionService' },
    @{ From = 'import com.hydro.monitor.iot.mapper.UserMapper'; To = 'import com.hydro.monitor.modules.mapper.UserMapper' },
    @{ From = 'import com.hydro.monitor.iot.mapper.UserProjectMapper'; To = 'import com.hydro.monitor.modules.mapper.UserProjectMapper' },
    @{ From = 'import com.hydro.monitor.iot.entity.UserEntity'; To = 'import com.hydro.monitor.modules.entity.UserEntity' },
    @{ From = 'import com.hydro.monitor.iot.entity.UserProjectEntity'; To = 'import com.hydro.monitor.modules.entity.UserProjectEntity' },
    @{ From = 'import com.hydro.monitor.iot.entity.ProjectEntity'; To = 'import com.hydro.monitor.api.dto.ProjectBriefDTO' },
    @{ From = 'import com.hydro.monitor.iot.mapper.ProjectMapper'; To = 'import com.hydro.monitor.api.spi.ProjectCatalogApi' },
    @{ From = 'import com.hydro.monitor.iot.service.SystemConfigService'; To = 'import com.hydro.monitor.api.spi.MonitoringRuntimeSettingsApi' },
    @{ From = 'import com.hydro.monitor.iot.service.ProjectService'; To = 'import com.hydro.monitor.api.spi.ProjectCatalogApi' },
    @{ From = 'import com.hydro.monitor.iot.entity.SystemConfigEntity'; To = 'import com.hydro.monitor.api.spi.MonitoringRuntimeSettingsApi' }
)

Write-Host '=== Moving IoT files ==='
$allIot = $iotControllers + $iotServices + $iotMappers + $iotEntities + $iotDtos + $iotVos + $iotConstants + $iotSpi + @('util/TerminalOnlineStatusUtils.java')
foreach ($rel in $allIot) {
    $src = Join-Path "$bizSrc/modules" $rel
    $dest = Join-Path $iotSrc $rel
    Move-And-Rewrite -SrcFile $src -DestFile $dest -Replacements $iotRepl
}

# common + schedule for iot
Move-And-Rewrite -SrcFile "$bizSrc/common/HexUtils.java" -DestFile "$iotSrc/common/HexUtils.java" -Replacements @{
    'package com.hydro.monitor.common' = 'package com.hydro.monitor.iot.common'
}
Move-And-Rewrite -SrcFile "$bizSrc/common/ThresholdEvaluator.java" -DestFile "$iotSrc/common/ThresholdEvaluator.java" -Replacements @{
    'package com.hydro.monitor.common' = 'package com.hydro.monitor.iot.common'
    'import com.hydro.monitor.modules.entity.TerminalThresholdRuleEntity' = 'import com.hydro.monitor.iot.entity.TerminalThresholdRuleEntity'
}
Move-And-Rewrite -SrcFile "$bizSrc/schedule/TerminalOnlineStatusScheduler.java" -DestFile "$iotSrc/schedule/TerminalOnlineStatusScheduler.java" -Replacements @{
    'package com.hydro.monitor.schedule' = 'package com.hydro.monitor.iot.schedule'
    'import com.hydro.monitor.modules.service.TerminalService' = 'import com.hydro.monitor.iot.service.TerminalService'
}

# Fix security / biz cross imports inside iot
Get-ChildItem $iotSrc -Recurse -Filter '*.java' | ForEach-Object {
    $c = Get-Content $_.FullName -Raw -Encoding UTF8
    $orig = $c
    foreach ($r in $restoreSecurityImports) {
        $c = $c.Replace($r.From, $r.To)
    }
    # Result / PageResult / SecurityUtils stay in com.hydro.monitor.common (security module) - never rewritten from modules
    if ($c -ne $orig) {
        Set-Content $_.FullName -Value $c -Encoding UTF8 -NoNewline
    }
}

Write-Host '=== Moving Unit to security ==='
$unitFiles = @(
    'controller/UnitController.java',
    'service/UnitService.java',
    'service/impl/UnitServiceImpl.java',
    'mapper/UnitMapper.java',
    'entity/UnitEntity.java',
    'vo/UnitVO.java',
    'dto/UnitCreateDTO.java',
    'dto/UnitUpdateDTO.java',
    'dto/UnitQueryDTO.java'
)
foreach ($rel in $unitFiles) {
    $src = Join-Path "$bizSrc/modules" $rel
    $dest = Join-Path "$secSrc/modules" $rel
    if (Test-Path $src) {
        Ensure-Dir (Split-Path $dest -Parent)
        Move-Item -Path $src -Destination $dest -Force
    }
}

Write-Host '=== Restructuring biz Modulith domains ==='

function Move-Biz-Domain {
    param(
        [string]$Domain,
        [string[]]$Rels,
        [hashtable]$ExtraRepl = @{}
    )
    $baseRepl = [ordered]@{
        "package com.hydro.monitor.modules.controller" = "package com.hydro.monitor.biz.$Domain.controller"
        "package com.hydro.monitor.modules.service.impl" = "package com.hydro.monitor.biz.$Domain.service.impl"
        "package com.hydro.monitor.modules.service" = "package com.hydro.monitor.biz.$Domain.service"
        "package com.hydro.monitor.modules.mapper" = "package com.hydro.monitor.biz.$Domain.mapper"
        "package com.hydro.monitor.modules.entity" = "package com.hydro.monitor.biz.$Domain.entity"
        "package com.hydro.monitor.modules.dto" = "package com.hydro.monitor.biz.$Domain.dto"
        "package com.hydro.monitor.modules.vo" = "package com.hydro.monitor.biz.$Domain.vo"
        "package com.hydro.monitor.modules.constant" = "package com.hydro.monitor.biz.$Domain.constant"
        "package com.hydro.monitor.modules.spi" = "package com.hydro.monitor.biz.$Domain.spi"
        "package com.hydro.monitor.modules.config" = "package com.hydro.monitor.biz.$Domain.config"
        "package com.hydro.monitor.operation" = "package com.hydro.monitor.biz.$Domain"
        "package com.hydro.monitor.video.hikvision" = "package com.hydro.monitor.biz.$Domain.hikvision"
    }
    foreach ($k in $ExtraRepl.Keys) { $baseRepl[$k] = $ExtraRepl[$k] }

    foreach ($rel in $Rels) {
        $src = Join-Path $bizSrc $rel
        # destination: strip modules/ prefix and nest under biz/$Domain/
        $leaf = $rel -replace '^modules/', '' -replace '^operation/', '' -replace '^video/', ''
        if ($rel.StartsWith('operation/')) {
            $dest = Join-Path "$bizSrc/biz/$Domain" $leaf
        } elseif ($rel.StartsWith('video/')) {
            $dest = Join-Path "$bizSrc/biz/$Domain" $leaf
        } else {
            $dest = Join-Path "$bizSrc/biz/$Domain" $leaf
        }
        Move-And-Rewrite -SrcFile $src -DestFile $dest -Replacements $baseRepl
    }
}

$announcement = @(
    'modules/controller/AnnouncementController.java',
    'modules/service/AnnouncementService.java',
    'modules/service/AnnouncementSseService.java',
    'modules/service/impl/AnnouncementServiceImpl.java',
    'modules/service/impl/AnnouncementSseServiceImpl.java',
    'modules/mapper/AnnouncementMapper.java',
    'modules/mapper/AnnouncementReadMapper.java',
    'modules/entity/AnnouncementEntity.java',
    'modules/entity/AnnouncementReadEntity.java',
    'modules/dto/AnnouncementCreateDTO.java',
    'modules/dto/AnnouncementQueryDTO.java',
    'modules/dto/AnnouncementUpdateDTO.java',
    'modules/vo/AnnouncementVO.java'
)
$operationlog = @(
    'modules/controller/OperationLogController.java',
    'modules/service/OperationLogService.java',
    'modules/service/impl/OperationLogServiceImpl.java',
    'modules/mapper/OperationLogMapper.java',
    'modules/entity/OperationLogEntity.java',
    'modules/dto/OperationLogQueryDTO.java',
    'modules/vo/OperationLogVO.java',
    'modules/spi/AuthAuditApiImpl.java',
    'operation/OperationLogInterceptor.java',
    'operation/OperationLogRequestFilter.java',
    'operation/OperationLogUtils.java',
    'operation/OperationLogWebConfig.java'
)
$project = @(
    'modules/controller/ProjectController.java',
    'modules/service/ProjectService.java',
    'modules/service/impl/ProjectServiceImpl.java',
    'modules/mapper/ProjectMapper.java',
    'modules/entity/ProjectEntity.java',
    'modules/dto/ProjectCreateDTO.java',
    'modules/dto/ProjectQueryDTO.java',
    'modules/dto/ProjectUpdateDTO.java',
    'modules/vo/ProjectVO.java',
    'modules/vo/ProjectTreeNodeVO.java',
    'modules/constant/ProjectTypeConstants.java',
    'modules/spi/ProjectCatalogApiImpl.java'
)
$systemconfig = @(
    'modules/controller/SystemConfigController.java',
    'modules/service/SystemConfigService.java',
    'modules/service/impl/SystemConfigServiceImpl.java',
    'modules/mapper/SystemConfigMapper.java',
    'modules/entity/SystemConfigEntity.java',
    'modules/dto/SystemConfigUpdateDTO.java',
    'modules/vo/SystemConfigVO.java',
    'modules/spi/LoginSecuritySettingsApiImpl.java'
)
$video = @(
    'modules/controller/VideoStationController.java',
    'modules/service/VideoStationService.java',
    'modules/service/impl/VideoStationServiceImpl.java',
    'modules/mapper/VideoStationMapper.java',
    'modules/entity/VideoStationEntity.java',
    'modules/dto/HikvisionCameraQueryDTO.java',
    'modules/dto/VideoPtzControlDTO.java',
    'modules/dto/VideoStationBatchCreateDTO.java',
    'modules/dto/VideoStationBatchCreateItemDTO.java',
    'modules/dto/VideoStationCreateDTO.java',
    'modules/dto/VideoStationQueryDTO.java',
    'modules/dto/VideoStationStreamTestDTO.java',
    'modules/dto/VideoStationUpdateDTO.java',
    'modules/vo/HikvisionCameraVO.java',
    'modules/vo/VideoPreviewUrlVO.java',
    'modules/vo/VideoPtzControlVO.java',
    'modules/vo/VideoStationBatchCreateIssueVO.java',
    'modules/vo/VideoStationBatchCreateResultVO.java',
    'modules/vo/VideoStationVO.java',
    'modules/vo/VideoTreeNodeVO.java',
    'modules/constant/HikvisionPreviewProtocolConstants.java',
    'modules/constant/HikvisionPtzActionConstants.java',
    'modules/constant/HikvisionPtzCommandConstants.java',
    'modules/constant/VideoStationTypeConstants.java',
    'modules/config/HikvisionPlatformConfig.java',
    'video/hikvision/HikvisionArtemisClient.java'
)
$systemmonitor = @(
    'modules/controller/SystemMonitorController.java',
    'modules/controller/DevToolsTestController.java',
    'modules/service/SystemMonitorService.java',
    'modules/service/impl/SystemMonitorServiceImpl.java',
    'modules/dto/ApplicationMetricsDTO.java',
    'modules/dto/BusinessMetricsDTO.java',
    'modules/dto/ComponentHealthDTO.java',
    'modules/dto/DatabaseMetricsDTO.java',
    'modules/dto/JvmMetricsDTO.java',
    'modules/dto/NettyMetricsDTO.java',
    'modules/dto/NettyServerMetricsDTO.java',
    'modules/dto/OsMetricsDTO.java',
    'modules/dto/ProtocolBusinessMetricsDTO.java',
    'modules/dto/SimulationMetricsDTO.java',
    'modules/dto/StorageMetricsDTO.java',
    'modules/dto/SystemHealthDTO.java',
    'modules/dto/SystemStatusDTO.java',
    'modules/dto/WebServerMetricsDTO.java'
)

Move-Biz-Domain -Domain 'announcement' -Rels $announcement
Move-Biz-Domain -Domain 'operationlog' -Rels $operationlog
Move-Biz-Domain -Domain 'project' -Rels $project
Move-Biz-Domain -Domain 'systemconfig' -Rels $systemconfig
Move-Biz-Domain -Domain 'video' -Rels $video
Move-Biz-Domain -Domain 'systemmonitor' -Rels $systemmonitor

# GlobalExceptionHandler -> security common (or keep in biz shared)
if (Test-Path "$bizSrc/common/GlobalExceptionHandler.java") {
    Move-And-Rewrite -SrcFile "$bizSrc/common/GlobalExceptionHandler.java" `
        -DestFile "$secSrc/common/GlobalExceptionHandler.java" -Replacements @{}
}

Write-Host '=== Rewrite imports inside biz domains ==='

# Map old modules.* type names to new packages by scanning moved files is hard;
# do a global replace of known prefixes within biz/biz tree.
$bizDomainRoot = Join-Path $bizSrc 'biz'
$domainImportMap = @{
    # will be filled by second pass: replace intra-domain first via package already set
}

# Cross-domain and shared imports fixup for all biz java files
$bizJava = Get-ChildItem $bizDomainRoot -Recurse -Filter '*.java'
foreach ($f in $bizJava) {
    $c = Get-Content $f.FullName -Raw -Encoding UTF8
    $orig = $c

    # Same-domain relative: imports still pointing to modules.* need remapping
    # Announcement types
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.(controller|service|service\.impl|mapper|entity|dto|vo)\.(Announcement)', 'import com.hydro.monitor.biz.announcement.$1.$2'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.(AnnouncementService|AnnouncementSseService)', 'import com.hydro.monitor.biz.announcement.service.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.mapper\.(AnnouncementMapper|AnnouncementReadMapper)', 'import com.hydro.monitor.biz.announcement.mapper.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.entity\.(AnnouncementEntity|AnnouncementReadEntity)', 'import com.hydro.monitor.biz.announcement.entity.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.(Announcement\w+)', 'import com.hydro.monitor.biz.announcement.dto.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.vo\.AnnouncementVO', 'import com.hydro.monitor.biz.announcement.vo.AnnouncementVO'

    # Operation log
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.OperationLogService', 'import com.hydro.monitor.biz.operationlog.service.OperationLogService'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.mapper\.OperationLogMapper', 'import com.hydro.monitor.biz.operationlog.mapper.OperationLogMapper'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.entity\.OperationLogEntity', 'import com.hydro.monitor.biz.operationlog.entity.OperationLogEntity'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.OperationLogQueryDTO', 'import com.hydro.monitor.biz.operationlog.dto.OperationLogQueryDTO'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.vo\.OperationLogVO', 'import com.hydro.monitor.biz.operationlog.vo.OperationLogVO'
    $c = $c -replace 'import com\.hydro\.monitor\.operation\.', 'import com.hydro.monitor.biz.operationlog.'

    # Project
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.ProjectService', 'import com.hydro.monitor.biz.project.service.ProjectService'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.mapper\.ProjectMapper', 'import com.hydro.monitor.biz.project.mapper.ProjectMapper'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.entity\.ProjectEntity', 'import com.hydro.monitor.biz.project.entity.ProjectEntity'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.(Project\w+)', 'import com.hydro.monitor.biz.project.dto.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.vo\.(ProjectVO|ProjectTreeNodeVO)', 'import com.hydro.monitor.biz.project.vo.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.constant\.ProjectTypeConstants', 'import com.hydro.monitor.biz.project.constant.ProjectTypeConstants'

    # System config
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.SystemConfigService', 'import com.hydro.monitor.biz.systemconfig.service.SystemConfigService'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.mapper\.SystemConfigMapper', 'import com.hydro.monitor.biz.systemconfig.mapper.SystemConfigMapper'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.entity\.SystemConfigEntity', 'import com.hydro.monitor.biz.systemconfig.entity.SystemConfigEntity'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.SystemConfigUpdateDTO', 'import com.hydro.monitor.biz.systemconfig.dto.SystemConfigUpdateDTO'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.vo\.SystemConfigVO', 'import com.hydro.monitor.biz.systemconfig.vo.SystemConfigVO'

    # Video
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.VideoStationService', 'import com.hydro.monitor.biz.video.service.VideoStationService'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.mapper\.VideoStationMapper', 'import com.hydro.monitor.biz.video.mapper.VideoStationMapper'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.entity\.VideoStationEntity', 'import com.hydro.monitor.biz.video.entity.VideoStationEntity'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.(Video\w+|HikvisionCameraQueryDTO)', 'import com.hydro.monitor.biz.video.dto.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.vo\.(Video\w+|HikvisionCameraVO)', 'import com.hydro.monitor.biz.video.vo.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.constant\.(Hikvision\w+|VideoStationTypeConstants)', 'import com.hydro.monitor.biz.video.constant.$1'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.config\.HikvisionPlatformConfig', 'import com.hydro.monitor.biz.video.config.HikvisionPlatformConfig'
    $c = $c -replace 'import com\.hydro\.monitor\.video\.hikvision\.', 'import com.hydro.monitor.biz.video.hikvision.'

    # System monitor DTOs
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.service\.SystemMonitorService', 'import com.hydro.monitor.biz.systemmonitor.service.SystemMonitorService'
    $c = $c -replace 'import com\.hydro\.monitor\.modules\.dto\.(ApplicationMetricsDTO|BusinessMetricsDTO|ComponentHealthDTO|DatabaseMetricsDTO|JvmMetricsDTO|NettyMetricsDTO|NettyServerMetricsDTO|OsMetricsDTO|ProtocolBusinessMetricsDTO|SimulationMetricsDTO|StorageMetricsDTO|SystemHealthDTO|SystemStatusDTO|WebServerMetricsDTO)', 'import com.hydro.monitor.biz.systemmonitor.dto.$1'

    # Unit (security)
    # already modules.unit types stay as modules.*

    if ($c -ne $orig) {
        Set-Content $f.FullName -Value $c -Encoding UTF8 -NoNewline
    }
}

Write-Host 'Done file moves. Leftover modules files:'
Get-ChildItem "$bizSrc/modules" -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }

Write-Host 'Migration script finished.'
