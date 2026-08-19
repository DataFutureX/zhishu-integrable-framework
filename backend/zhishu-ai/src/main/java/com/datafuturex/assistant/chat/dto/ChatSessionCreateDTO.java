package com.datafuturex.assistant.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "新建 Agent 会话")
public record ChatSessionCreateDTO(
        @Schema(description = "场景，默认 CHAT") String scene,
        @Schema(description = "标题，可空则「新会话」") String title,
        @Schema(description = "智能体 ID") Long agentId
) {
}
