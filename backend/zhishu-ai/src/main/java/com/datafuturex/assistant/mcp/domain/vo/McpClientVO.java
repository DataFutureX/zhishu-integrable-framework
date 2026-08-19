package com.datafuturex.assistant.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "对外 MCP Client")
public record McpClientVO(
        Long id,
        String name,
        String keyPrefix,
        Long boundUserId,
        String boundUsername,
        List<String> capabilities,
        Integer rpmLimit,
        String status,
        String remark,
        LocalDateTime lastUsedAt,
        String createdBy,
        LocalDateTime createTime,
        String apiKey
) {
}
