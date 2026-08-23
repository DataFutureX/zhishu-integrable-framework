package cn.datafuturex.zhishu.ai.agent.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 知枢内核能力。监测/巡检/NL2SQL 由万象 MCP upstream 动态提供，不再作为内置枚举。
 */
public enum AgentCapability {

    RAG("知识库增强", "向量检索 + Hybrid 关键词补充（非 Tool）", false, List.of()),
    MEMORY("多轮记忆", "会话窗口记忆（非 Tool）", false, List.of()),
    WORKFLOW_GRAPH("工作流 Graph", "可视化编排执行（非 Tool）", false, List.of()),
    MCP_TOOLS("MCP 上游工具", "调用已绑定的外部 MCP（如万象监测）", false, List.of()),
    KNOWLEDGE_GRAPH("知识图谱引擎", "图谱检索骨架；业务拓扑由接入方投喂", false, List.of()),
    BRIEFING("AI 简报", "调度生成与投递（非内嵌监测 Tool）", false, List.of());

    private final String label;
    private final String description;
    private final boolean toolBased;
    private final List<String> toolNames;

    AgentCapability(String label, String description, boolean toolBased, List<String> toolNames) {
        this.label = label;
        this.description = description;
        this.toolBased = toolBased;
        this.toolNames = toolNames;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isToolBased() {
        return toolBased;
    }

    public List<String> getToolNames() {
        return toolNames;
    }

    public static Optional<AgentCapability> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }
}
