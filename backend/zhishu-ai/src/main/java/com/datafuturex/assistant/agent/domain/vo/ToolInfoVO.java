package com.datafuturex.assistant.agent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tool 能力项")
public record ToolInfoVO(
        @Schema(description = "Tool 方法名") String name,
        @Schema(description = "Tool 功能描述") String description
) {
}
