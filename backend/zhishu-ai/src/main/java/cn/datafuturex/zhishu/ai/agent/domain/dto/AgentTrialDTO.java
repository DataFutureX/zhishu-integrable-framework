package cn.datafuturex.zhishu.ai.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "智能体试运行")
public record AgentTrialDTO(
        @NotBlank
        @Schema(description = "试运行用户消息")
        String message,

        @Schema(description = "是否启用 RAG（仅当 Agent 勾选 RAG 能力时生效）")
        Boolean enableRag,

        @Schema(description = "多轮试运行会话 ID；空则新建")
        String conversationId,

        @Schema(description = "试运行是否启用记忆（默认 false）")
        Boolean enableMemory
) {
}
