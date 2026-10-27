package cn.datafuturex.zhishu.mcp.provide.catalog;

import cn.datafuturex.zhishu.mcp.provide.spi.McpToolContributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对外 MCP Tool 目录：决定「哪些 Tool 出现在 {@code tools/list}」与「某能力码能用哪些 Tool」。
 * <p>
 * Tool 来源两类：① 本模块内置的 {@link McpProvidedTool} Bean；② 他方模块经
 * {@link McpToolContributor} 贡献的业务 Tool。贡献进来的 Tool 必须出现在能力字典里才会对外，
 * 避免他方 Bean 无约束地泄漏到 {@code /mcp}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpProvidedToolCatalog {

    /**
     * 公开目录：无需 Client 能力授权即可调用（平台登录态 JWT 也仅此集合）。
     * <p>
     * 保持 {@code getSystemIntroduction} 常驻，避免迁移后 {@code /mcp} 变成空端点。
     */
    public static final Set<String> PUBLIC_TOOLS = Set.of("getSystemIntroduction");

    /** 新建 Client 的默认能力码（空=只用公开目录） */
    public static final List<String> DEFAULT_CAPABILITIES = List.of();

    /** 能力码 → 可调用 Tool 名（对齐 AgentCapability，NL2SQL / RAG 不对 MCP 开放） */
    private static final Map<String, List<String>> CAPABILITY_TOOLS = buildCapabilityTools();

    /** 能力字典里出现过的全部 Tool 名（贡献 Tool 的准入集合） */
    private static final Set<String> REGISTERED_TOOL_NAMES = register(CAPABILITY_TOOLS, PUBLIC_TOOLS);

    private final ObjectProvider<List<McpProvidedTool>> builtinTools;
    private final ObjectProvider<List<McpToolContributor>> contributors;

    /**
     * 对外候选 Tool（未经守卫包装），顺序稳定：内置优先，再按贡献者声明顺序；同名只保留首个。
     * <p>
     * 去重是防御措施的其中一环：真正避免守卫被绕过，靠的是 {@link McpProvidedTool}
     * 不把裸 {@code ToolCallback} 暴露成 Bean（否则 Spring AI 会直接聚合裸实例）。
     */
    public List<ToolCallback> candidates() {
        List<ToolCallback> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<McpProvidedTool> builtin = builtinTools.getIfAvailable(() -> List.of());
        for (McpProvidedTool tool : builtin) {
            addIfNew(result, seen, tool.toolCallback());
        }
        for (McpToolContributor contributor : contributors.getIfAvailable(() -> List.of())) {
            List<ToolCallback> offered = contributor.contribute();
            if (offered == null || offered.isEmpty()) {
                continue;
            }
            for (ToolCallback callback : offered) {
                String name = callback.getToolDefinition().name();
                if (!REGISTERED_TOOL_NAMES.contains(name)) {
                    log.debug("贡献 Tool {} 不在能力字典内，跳过（source={}）", name, contributor.source());
                    continue;
                }
                addIfNew(result, seen, callback);
            }
        }
        return result;
    }
    
    private static void addIfNew(List<ToolCallback> target, Set<String> seen, ToolCallback callback) {
        String name = callback.getToolDefinition().name();
        if (seen.add(name)) {
            target.add(callback);
        } else {
            log.warn("对外 Tool 名称重复，已丢弃后置同名项: {}", name);
        }
    }

    /**
     * 能力码 → 允许调用的 Tool 名集合；空能力回落到公开目录。
     */
    public static Set<String> allowedTools(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return PUBLIC_TOOLS;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String cap : capabilities) {
            if (cap == null || cap.isBlank()) {
                continue;
            }
            names.addAll(CAPABILITY_TOOLS.getOrDefault(cap.trim().toUpperCase(), List.of()));
        }
        names.addAll(PUBLIC_TOOLS);
        return names;
    }

    /**
     * 能力码字典（供控制面 {@code /catalog} 展示）。
     */
    public static Map<String, List<String>> capabilityTools() {
        return CAPABILITY_TOOLS;
    }

    public static boolean isPublic(String toolName) {
        return PUBLIC_TOOLS.contains(toolName);
    }

    private static Map<String, List<String>> buildCapabilityTools() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("STATION_LATEST", List.of("queryStationLatestElements"));
        map.put("STATION_HISTORY", List.of("queryStationHistoryElements"));
        map.put("STATION_COMPARE", List.of("compareStations"));
        map.put("ONLINE", List.of("getTerminalOnlineOverview", "queryTerminalOnlineStatus", "listTerminals"));
        map.put("PROJECT", List.of("listProjects"));
        map.put("ALERT", List.of("queryRecentAlerts", "queryAlertTrends"));
        map.put("INSPECTION_PLAN", List.of("listInspectionPlans", "getInspectionPlan"));
        map.put("INSPECTION_TASK", List.of("listInspectionTasks", "getInspectionTaskDetail"));
        map.put("INSPECTION_ISSUE", List.of("listOpenInspectionIssues"));
        map.put("INSPECTION_SUMMARY", List.of("getInspectionSummary"));
        map.put("KNOWLEDGE_GRAPH", List.of(
                "searchGraphEntities", "getGraphNeighbors", "findGraphPath",
                "getProjectTopology", "getAlertImpact"));
        return Map.copyOf(map);
    }

    private static Set<String> register(Map<String, List<String>> capabilityTools, Set<String> publicTools) {
        LinkedHashSet<String> names = new LinkedHashSet<>(publicTools);
        capabilityTools.values().forEach(names::addAll);
        return Set.copyOf(names);
    }
}
