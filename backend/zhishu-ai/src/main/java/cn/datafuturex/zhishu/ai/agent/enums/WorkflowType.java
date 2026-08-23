package cn.datafuturex.zhishu.ai.agent.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 预设工作流模板。
 */
public enum WorkflowType {

    REACT("单智能体 ReAct", "推理-行动循环，按需调用工具完成任务"),
    SEQUENTIAL("顺序多步", "意图澄清 → 工具执行 → 结果润色"),
    ROUTING("路由分发", "入口路由至数据查询或知识问答子智能体"),
    GRAPH("可视化编排", "按 workflow_config Graph JSON 执行节点与边");

    private final String label;
    private final String description;

    WorkflowType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public static Optional<WorkflowType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static WorkflowType require(String code) {
        return fromCode(code).orElseThrow(() -> new IllegalArgumentException("不支持的工作流类型: " + code));
    }
}
