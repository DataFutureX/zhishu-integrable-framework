package cn.datafuturex.zhishu.ai.shared.vo.structured;

import java.util.List;

/**
 * 趋势分析结构化结果
 */
public record TrendAnalysisResult(
        String stationAddress,
        String element,
        String startTime,
        String endTime,
        int sampleCount,
        Double min,
        Double max,
        Double avg,
        Double sum,
        String trend,
        String summary,
        List<TrendPoint> points
) {
    public record TrendPoint(String observeTime, double value) {
    }
}
