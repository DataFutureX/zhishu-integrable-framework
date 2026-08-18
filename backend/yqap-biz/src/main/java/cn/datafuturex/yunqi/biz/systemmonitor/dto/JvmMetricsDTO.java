package cn.datafuturex.yunqi.biz.systemmonitor.dto;

/**
 * JVM 运行指标
 */
public record JvmMetricsDTO(
        Double heapUsedMb,
        Double heapMaxMb,
        Double heapCommittedMb,
        Double heapUsagePercent,
        Double nonHeapUsedMb,
        Double nonHeapCommittedMb,
        Integer activeThreads,
        Integer peakThreads,
        Integer daemonThreads,
        Long totalStartedThreads,
        Long gcCount,
        Long gcTimeMs
) {
}
