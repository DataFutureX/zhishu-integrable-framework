package cn.datafuturex.zhishu.ai.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agent 执行监控统计概览 VO。
 */
@Schema(description = "执行监控统计概览")
public record AgentMonitorStatsVO(
        @Schema(description = "总执行数") int totalCount,
        @Schema(description = "成功数") int successCount,
        @Schema(description = "失败数") int failedCount,
        @Schema(description = "运行中数") int runningCount,
        @Schema(description = "成功率（%）") double successRate,
        @Schema(description = "平均耗时（毫秒）") long avgDurationMs,
        @Schema(description = "今日执行数") int todayCount
) {}
