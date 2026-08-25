package cn.datafuturex.zhishu.ai.agent.registry;

import cn.datafuturex.zhishu.ai.agent.domain.vo.ToolInfoVO;
import cn.datafuturex.zhishu.ai.agent.enums.AgentCapability;
import cn.datafuturex.zhishu.ai.agent.support.AgentJsonUtils;
import cn.datafuturex.zhishu.ai.biztools.api.BizToolProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力 → Tool 名称 / ToolCallback 映射。
 */
@Component
@RequiredArgsConstructor
public class ToolCapabilityRegistry {

    private final BizToolProviderPort bizToolProviderPort;

    public List<String> resolveToolNames(List<String> capabilities) {
        Set<String> names = new LinkedHashSet<>();
        for (String code : capabilities) {
            AgentCapability.fromCode(code).ifPresent(cap -> {
                if (cap.isToolBased()) {
                    names.addAll(cap.getToolNames());
                }
            });
        }
        return new ArrayList<>(names);
    }

    public List<ToolCallback> resolveToolCallbacks(List<String> capabilities) {
        List<String> allowed = resolveToolNames(capabilities);
        if (allowed.isEmpty()) {
            return List.of();
        }
        ToolCallback[] all = ToolCallbacks.from(bizToolProviderPort.toolBeans());
        return Arrays.stream(all)
                .filter(cb -> allowed.contains(cb.getToolDefinition().name()))
                .toList();
    }

    public Object[] toolBeans() {
        return bizToolProviderPort.toolBeans();
    }

    public boolean supportsRag(List<String> capabilities) {
        return AgentJsonUtils.hasCapability(capabilities, AgentCapability.RAG);
    }

    /**
     * 按 Tool 名称解析描述（来自 @Tool description）。
     */
    public List<ToolInfoVO> describeTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        Map<String, ToolInfoVO> catalog = toolCatalog();
        List<ToolInfoVO> result = new ArrayList<>();
        for (String name : toolNames) {
            ToolInfoVO info = catalog.get(name);
            if (info != null) {
                result.add(info);
            } else {
                result.add(new ToolInfoVO(name, ""));
            }
        }
        return result;
    }

    public Map<String, ToolInfoVO> toolCatalog() {
        Map<String, ToolInfoVO> map = new LinkedHashMap<>();
        for (ToolCallback cb : ToolCallbacks.from(bizToolProviderPort.toolBeans())) {
            ToolDefinition def = cb.getToolDefinition();
            String name = def.name();
            String description = StringUtils.hasText(def.description()) ? def.description() : "";
            map.put(name, new ToolInfoVO(name, description));
        }
        return map;
    }
}
