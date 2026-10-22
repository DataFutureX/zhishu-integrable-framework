package cn.datafuturex.zhishu.gateway.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 调用台账视图。
 *
 * @param id               主键
 * @param requestId        请求唯一标识
 * @param userId           用户标识
 * @param username         用户名
 * @param channel          通道
 * @param modelId          网关模型 id
 * @param providerId       上游供应商 ID
 * @param upstreamModel    供应商真实模型名
 * @param stream           是否流式
 * @param status           状态
 * @param httpStatus       返回 HTTP 状态
 * @param reservedTokens   预留 token
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param totalTokens      总 token
 * @param cachedTokens     缓存 token
 * @param reasoningTokens  推理 token
 * @param estimated        是否估算
 * @param latencyMs        总耗时
 * @param ttftMs           首 token 耗时
 * @param clientName       客户端名
 * @param clientVersion    客户端版本
 * @param ip               来源 IP
 * @param errorCode        错误码
 * @param errorMessage     错误说明
 * @param createTime       创建时间
 */
@Schema(description = "网关调用台账")
public record GatewayUsageVO(
        Long id,
        String requestId,
        String userId,
        String username,
        String channel,
        String modelId,
        Long providerId,
        String upstreamModel,
        Boolean stream,
        String status,
        Integer httpStatus,
        Long reservedTokens,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer cachedTokens,
        Integer reasoningTokens,
        Boolean estimated,
        Integer latencyMs,
        Integer ttftMs,
        String clientName,
        String clientVersion,
        String ip,
        String errorCode,
        String errorMessage,
        LocalDateTime createTime
) {
}
