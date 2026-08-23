package cn.datafuturex.zhishu.openapi.sdk.model;

/**
 * 同步对话响应。
 *
 * @param content        回复内容
 * @param model          使用的模型名称
 * @param conversationId 会话 ID
 * @param agentId        智能体 ID
 */
public record ChatResponse(
        String content,
        String model,
        String conversationId,
        Long agentId
) {
}
