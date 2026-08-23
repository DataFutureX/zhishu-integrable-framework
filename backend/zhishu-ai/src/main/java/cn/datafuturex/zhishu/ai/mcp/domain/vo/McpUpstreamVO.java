package cn.datafuturex.zhishu.ai.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "接入的他方 MCP Server")
public record McpUpstreamVO(
        Long id,
        String code,
        String name,
        String protocol,
        String baseUrl,
        String endpoint,
        boolean hasAuthHeader,
        Integer requestTimeoutMs,
        String status,
        String healthStatus,
        String healthMessage,
        LocalDateTime lastProbeAt,
        String remark,
        int toolCount,
        LocalDateTime createTime
) {
}
