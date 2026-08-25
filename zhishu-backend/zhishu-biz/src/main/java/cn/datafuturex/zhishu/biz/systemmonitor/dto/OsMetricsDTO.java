package cn.datafuturex.zhishu.biz.systemmonitor.dto;

/**
 * 操作系统与宿主机指标
 */
public record OsMetricsDTO(
        String osName,
        String osArch,
        String osVersion,
        Integer availableProcessors,
        Double systemCpuUsagePercent,
        Double processCpuUsagePercent,
        Double systemMemoryTotalMb,
        Double systemMemoryFreeMb,
        Double systemMemoryUsagePercent
) {
}
