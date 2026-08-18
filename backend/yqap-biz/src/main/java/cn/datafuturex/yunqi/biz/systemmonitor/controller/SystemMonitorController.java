package cn.datafuturex.yunqi.biz.systemmonitor.controller;

import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.biz.systemmonitor.dto.SystemHealthDTO;
import cn.datafuturex.yunqi.biz.systemmonitor.dto.SystemStatusDTO;
import cn.datafuturex.yunqi.biz.systemmonitor.service.SystemMonitorService;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "系统监控", description = "系统运行状态与健康检查接口")
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_MONITOR_QUERY + "')")
    @Operation(summary = "获取系统综合运行状态",
            description = "采集 JVM、操作系统、MySQL/HikariCP、Web 服务、业务指标、分表及存储信息")
    public Result<SystemStatusDTO> getSystemStatus() {
        SystemStatusDTO status = systemMonitorService.getSystemStatus();
        log.debug("系统状态查询: status={}, heap={}MB/{}MB, db={}, sharding={}",
                status.status(),
                status.jvm().heapUsedMb(),
                status.jvm().heapMaxMb(),
                status.database().status(),
                status.sharding() != null ? status.sharding().status() : null);
        return Result.success(status);
    }

    @GetMapping("/health")
    @Operation(summary = "系统健康检查",
            description = "轻量级探活接口，返回各核心组件 UP/DOWN/DEGRADED 状态，可用于负载均衡或 K8s 探针")
    public Result<SystemHealthDTO> getSystemHealth() {
        return Result.success(systemMonitorService.getSystemHealth());
    }
}
