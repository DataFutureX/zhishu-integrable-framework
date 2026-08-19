package com.datafuturex.assistant.mcp.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "更新上游 Tool 启用状态")
public record McpUpstreamToolPatchDTO(
        @NotBlank String originalName,
        @NotNull Boolean enabled
) {
}
