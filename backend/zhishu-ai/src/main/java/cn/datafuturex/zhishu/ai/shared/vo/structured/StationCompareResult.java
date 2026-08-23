package cn.datafuturex.zhishu.ai.shared.vo.structured;

import java.util.List;

/**
 * 多站对比结构化结果
 */
public record StationCompareResult(
        String element,
        String summary,
        List<StationCompareItem> items
) {
    public record StationCompareItem(
            String stationAddress,
            String observeTime,
            Double value,
            String remark
    ) {
    }
}
