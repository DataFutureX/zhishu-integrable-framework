package cn.datafuturex.zhishu.ai.shared.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 结构化聊天请求（对比 / 趋势 / 告警）
 */
@Schema(description = "结构化聊天请求")
public record ChatStructuredRequestDTO(
        @NotBlank
        @Schema(description = "用户自然语言描述", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @NotNull
        @Schema(description = "COMPARE | TREND | ALARM", requiredMode = Schema.RequiredMode.REQUIRED)
        StructuredChatType type,

        @Schema(description = "多轮会话 ID")
        String conversationId,

        Integer maxTokens,

        Double temperature,

        @Schema(description = "智能体 ID；为空则使用默认智能体")
        Long agentId
) {
    public enum StructuredChatType {
        COMPARE, TREND, ALARM
    }
}
