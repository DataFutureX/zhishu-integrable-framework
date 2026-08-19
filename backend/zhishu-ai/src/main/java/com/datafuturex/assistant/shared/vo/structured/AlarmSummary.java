package com.datafuturex.assistant.shared.vo.structured;

import java.util.List;

/**
 * 告警摘要结构化结果
 */
public record AlarmSummary(
        String level,
        int totalCount,
        String summary,
        List<AlarmItem> items
) {
    public record AlarmItem(
            String stationAddress,
            String element,
            double currentValue,
            double threshold,
            String observeTime,
            String message
    ) {
    }
}
