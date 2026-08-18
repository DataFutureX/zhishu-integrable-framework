package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.time.LocalDateTime;

/**
 * 应用基础信息
 */
public record ApplicationMetricsDTO(
        String name,
        String version,
        String javaVersion,
        String springBootVersion,
        String profile,
        Long uptimeMillis,
        LocalDateTime startTime
) {
}
