package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.time.LocalDateTime;

/**
 * 组件健康状态
 */
public record ComponentHealthDTO(
        String name,
        String status,
        String message,
        Long responseTimeMs
) {
    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String DEGRADED = "DEGRADED";
    public static final String UNKNOWN = "UNKNOWN";
}
