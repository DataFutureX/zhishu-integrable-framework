package com.datafuturex.assistant.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "上游 Tool")
public record McpUpstreamToolVO(
        String originalName,
        String exposedName,
        String description,
        boolean enabled
) {
}
