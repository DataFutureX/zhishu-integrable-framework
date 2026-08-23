package cn.datafuturex.zhishu.ai.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP 中枢概览")
public record McpOverviewVO(
        boolean serverEnabled,
        String endpoint,
        long clientCount,
        long upstreamCount,
        long enabledUpstreamCount,
        boolean cryptoConfigured
) {
}
