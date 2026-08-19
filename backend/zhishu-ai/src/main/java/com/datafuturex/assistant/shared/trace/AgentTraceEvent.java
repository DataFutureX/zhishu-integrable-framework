package com.datafuturex.assistant.shared.trace;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "智能体执行轨迹事件")
public record AgentTraceEvent(
        @Schema(description = "NODE_START | NODE_END | TOOL_CALL | TOOL_RESULT | ROUTE")
        String type,
        @Schema(description = "节点或工具名")
        String name,
        @Schema(description = "摘要/参数/结果片段")
        String detail,
        @Schema(description = "耗时毫秒")
        Long durationMs,
        @Schema(description = "事件时间戳 epoch ms")
        Long timestamp
) {
    public static AgentTraceEvent of(String type, String name, String detail, Long durationMs) {
        return new AgentTraceEvent(type, name, detail, durationMs, System.currentTimeMillis());
    }
}
