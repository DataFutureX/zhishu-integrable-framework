package cn.datafuturex.zhishu.biz.systemmonitor.dto;

import java.util.List;

/**
 * 单条分表策略监控指标
 */
public record ShardingStrategyMetricsDTO(
        String name,
        String displayName,
        String tablePrefix,
        String status,
        Boolean autoCreate,
        Integer monthsBehind,
        Integer monthsAhead,
        Integer existingTableCount,
        Integer expectedTableCount,
        Integer missingTableCount,
        Long approximateRowTotal,
        Long dataLengthBytes,
        List<String> missingMonths,
        List<ShardingTableMetricsDTO> tables
) {
}
