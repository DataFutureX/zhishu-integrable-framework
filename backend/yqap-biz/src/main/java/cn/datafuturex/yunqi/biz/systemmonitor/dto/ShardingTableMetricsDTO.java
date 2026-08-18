package cn.datafuturex.yunqi.biz.systemmonitor.dto;

import java.time.LocalDateTime;

/**
 * 单个月分表指标
 */
public record ShardingTableMetricsDTO(
        String tableName,
        String month,
        Boolean exists,
        Long approximateRows,
        Long dataLengthBytes,
        LocalDateTime createTime
) {
}
