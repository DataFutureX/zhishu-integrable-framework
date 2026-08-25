package cn.datafuturex.zhishu.biz.systemmonitor.dto;

/**
 * 存储与日志指标
 */
public record StorageMetricsDTO(
        Double diskTotalMb,
        Double diskFreeMb,
        Double diskUsagePercent,
        Double logFileSizeMb,
        Double uploadDirSizeMb
) {
}
