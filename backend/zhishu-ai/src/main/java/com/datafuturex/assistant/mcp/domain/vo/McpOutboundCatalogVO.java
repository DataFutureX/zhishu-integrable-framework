package com.datafuturex.assistant.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "对外 MCP 默认能力与 Tool 目录")
public record McpOutboundCatalogVO(
        List<String> capabilities,
        List<String> toolNames
) {
}
