package cn.datafuturex.zhishu.ai.shared.vo;

import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天响应 VO
 */
@Schema(description = "聊天响应对象")
public record ChatResponseVO(
        @Schema(description = "AI 回复文本") String content,
        @Schema(description = "响应时间") LocalDateTime timestamp,
        @Schema(description = "模型名") String model,
        @Schema(description = "会话 ID，后续请求回传") String conversationId,
        @Schema(description = "结构化结果（可选）") Object structured,
        @Schema(description = "智能体 ID") Long agentId,
        @Schema(description = "执行轨迹") List<AgentTraceEvent> traces
) {
    public static ChatResponseVO of(String content, String model, String conversationId) {
        return new ChatResponseVO(content, LocalDateTime.now(), model, conversationId, null, null, null);
    }

    public static ChatResponseVO of(String content, String model, String conversationId, Object structured) {
        return new ChatResponseVO(content, LocalDateTime.now(), model, conversationId, structured, null, null);
    }

    public static ChatResponseVO of(String content, String model, String conversationId, Object structured, Long agentId) {
        return new ChatResponseVO(content, LocalDateTime.now(), model, conversationId, structured, agentId, null);
    }

    public static ChatResponseVO of(String content, String model, String conversationId, Object structured,
                                    Long agentId, List<AgentTraceEvent> traces) {
        return new ChatResponseVO(content, LocalDateTime.now(), model, conversationId, structured, agentId, traces);
    }

    public ChatResponseVO withAgentId(Long id) {
        return new ChatResponseVO(content, timestamp, model, conversationId, structured, id, traces);
    }

    public ChatResponseVO withTraces(List<AgentTraceEvent> newTraces) {
        return new ChatResponseVO(content, timestamp, model, conversationId, structured, agentId, newTraces);
    }
}
