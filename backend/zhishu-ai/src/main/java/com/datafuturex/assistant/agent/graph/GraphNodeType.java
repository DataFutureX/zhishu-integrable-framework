package com.datafuturex.assistant.agent.graph;

public enum GraphNodeType {
    START,
    LLM,
    TOOL_AGENT,
    ROUTER,
    /** 规则条件分支（不调 LLM）：按 edge.condition 匹配上一节点输出或用户原文 */
    CONDITIONAL,
    END;

    public static GraphNodeType require(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("节点类型不能为空");
        }
        return GraphNodeType.valueOf(type.trim().toUpperCase());
    }
}
