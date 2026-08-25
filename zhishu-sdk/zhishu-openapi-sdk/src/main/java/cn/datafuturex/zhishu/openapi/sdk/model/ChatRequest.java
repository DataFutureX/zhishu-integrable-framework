package cn.datafuturex.zhishu.openapi.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 同步对话请求。
 *
 * @param message        用户消息（必填）
 * @param conversationId 会话 ID（可选，续接上下文）
 * @param agentId        智能体 ID（可选）
 * @param enableMemory   是否保存问答历史（默认 true）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String message,
        String conversationId,
        Long agentId,
        Boolean enableMemory
) {
    /** 仅传消息的快捷构造 */
    public static ChatRequest of(String message) {
        return new ChatRequest(message, null, null, null);
    }

    public static Builder builder(String message) {
        return new Builder(message);
    }

    public static final class Builder {
        private final String message;
        private String conversationId;
        private Long agentId;
        private Boolean enableMemory;

        private Builder(String message) {
            this.message = message;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder agentId(Long agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder enableMemory(Boolean enableMemory) {
            this.enableMemory = enableMemory;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(message, conversationId, agentId, enableMemory);
        }
    }
}
