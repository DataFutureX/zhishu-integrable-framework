package cn.datafuturex.zhishu.ai.mcp.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "MCP 调用日志")
public record McpCallLogVO(
        Long id,
        String direction,
        Long clientId,
        Long upstreamId,
        Long agentId,
        String toolName,
        boolean success,
        String errorMessage,
        Integer durationMs,
        String userId,
        LocalDateTime createTime
) {
}
