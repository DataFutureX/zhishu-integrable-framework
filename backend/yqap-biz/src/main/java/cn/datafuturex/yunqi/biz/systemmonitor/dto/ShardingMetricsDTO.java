package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.util.List;

/**
 * 分表监控总览
 */
public record ShardingMetricsDTO(
        String status,
        Boolean enabled,
        Integer strategyCount,
        Integer existingTableCount,
        Integer expectedTableCount,
        Integer missingTableCount,
        Long approximateRowTotal,
        List<ShardingStrategyMetricsDTO> strategies
) {
}
