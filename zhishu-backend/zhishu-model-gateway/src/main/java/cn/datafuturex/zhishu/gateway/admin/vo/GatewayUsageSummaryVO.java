package cn.datafuturex.zhishu.gateway.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 调用汇总视图。
 *
 * @param totalRequests 请求总数
 * @param totalTokens   总 token
 * @param successCount  成功次数
 * @param errorCount    失败次数
 * @param estimatedCount 估算计量次数
 * @param byProvider    按供应商聚合
 * @param byModel       按模型聚合
 * @param byUser        按用户聚合
 * @param byDay         按天聚合
 */
@Schema(description = "网关调用汇总")
public record GatewayUsageSummaryVO(
        long totalRequests,
        long totalTokens,
        long successCount,
        long errorCount,
        long estimatedCount,
        List<Bucket> byProvider,
        List<Bucket> byModel,
        List<Bucket> byUser,
        List<Bucket> byDay
) {

    /**
     * 聚合桶。
     *
     * @param key      聚合键（供应商 / 模型 / 用户 / 日期）
     * @param requests 请求数
     * @param tokens   总 token
     */
    @Schema(description = "聚合桶")
    public record Bucket(String key, long requests, long tokens) {
    }
}
