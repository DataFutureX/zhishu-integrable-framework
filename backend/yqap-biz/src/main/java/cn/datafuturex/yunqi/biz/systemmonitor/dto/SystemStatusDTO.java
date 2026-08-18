package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统运行状态综合 DTO
 */
public record SystemStatusDTO(
        String status,
        LocalDateTime timestamp,
        ApplicationMetricsDTO application,
        JvmMetricsDTO jvm,
        OsMetricsDTO os,
        DatabaseMetricsDTO database,
        WebServerMetricsDTO webServer,
        BusinessMetricsDTO business,
        ShardingMetricsDTO sharding,
        StorageMetricsDTO storage,
        List<ComponentHealthDTO> components
) {
}
