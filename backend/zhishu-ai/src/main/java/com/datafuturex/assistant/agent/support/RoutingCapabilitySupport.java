package com.datafuturex.assistant.agent.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ROUTING 工作流：按能力划分 DATA / INSPECTION / NL2SQL / KNOWLEDGE 路由。
 */
public final class RoutingCapabilitySupport {

    public static final String ROUTE_DATA = "DATA";
    public static final String ROUTE_INSPECTION = "INSPECTION";
    public static final String ROUTE_NL2SQL = "NL2SQL";
    public static final String ROUTE_KNOWLEDGE = "KNOWLEDGE";

    private static final Set<String> DATA_CAPS = Set.of("MCP_TOOLS");
    private static final Set<String> INSPECTION_CAPS = Set.of();
    private static final Set<String> NL2SQL_CAPS = Set.of();

    private RoutingCapabilitySupport() {
    }

    public static List<String> dataCapabilities(List<String> capabilities) {
        return filterCaps(capabilities, DATA_CAPS);
    }

    public static List<String> inspectionCapabilities(List<String> capabilities) {
        return filterCaps(capabilities, INSPECTION_CAPS);
    }

    public static List<String> nl2sqlCapabilities(List<String> capabilities) {
        return filterCaps(capabilities, NL2SQL_CAPS);
    }

    /** 当前 Agent 可用的工具类路由（不含 KNOWLEDGE）。 */
    public static List<String> availableToolRoutes(List<String> capabilities) {
        List<String> routes = new ArrayList<>();
        if (!dataCapabilities(capabilities).isEmpty()) {
            routes.add(ROUTE_DATA);
        }
        if (!inspectionCapabilities(capabilities).isEmpty()) {
            routes.add(ROUTE_INSPECTION);
        }
        if (!nl2sqlCapabilities(capabilities).isEmpty()) {
            routes.add(ROUTE_NL2SQL);
        }
        return routes;
    }

    /** 工具路由 + 知识问答兜底（有 RAG 可检索；无 RAG 时纯 LLM）。 */
    public static List<String> availableRoutes(List<String> capabilities) {
        LinkedHashSet<String> routes = new LinkedHashSet<>(availableToolRoutes(capabilities));
        routes.add(ROUTE_KNOWLEDGE);
        return new ArrayList<>(routes);
    }

    public static String buildRouterPrompt(List<String> capabilities) {
        List<String> routes = availableRoutes(capabilities);
        StringBuilder sb = new StringBuilder();
        sb.append("你是路由调度器。只输出一个词：").append(String.join(" / ", routes)).append("。\n");
        if (routes.contains(ROUTE_DATA)) {
            sb.append("DATA=遥测数据、多站对比、在线状态、工程、阈值告警。\n");
        }
        if (routes.contains(ROUTE_INSPECTION)) {
            sb.append("INSPECTION=巡检计划、任务、异常、巡检摘要。\n");
        }
        if (routes.contains(ROUTE_NL2SQL)) {
            sb.append("NL2SQL=自然语言写 SQL 查数、复杂统计、跨表分析。\n");
        }
        if (routes.contains(ROUTE_KNOWLEDGE)) {
            sb.append("KNOWLEDGE=规范文档、知识库、概念解释（非实时取数）。\n");
        }
        return sb.toString();
    }

    /**
     * 解析路由词；无法识别时回退到第一个可用工具路由，再不行回退 KNOWLEDGE。
     */
    public static String resolveRoute(String routeRaw, List<String> capabilities) {
        List<String> available = availableRoutes(capabilities);
        String upper = routeRaw == null ? "" : routeRaw.trim().toUpperCase(Locale.ROOT);
        for (String route : available) {
            if (upper.contains(route) || containsChineseAlias(upper, route)) {
                return route;
            }
        }
        List<String> toolRoutes = availableToolRoutes(capabilities);
        if (!toolRoutes.isEmpty()) {
            return toolRoutes.getFirst();
        }
        return ROUTE_KNOWLEDGE;
    }

    public static List<String> capabilitiesForRoute(String route, List<String> capabilities) {
        return switch (route) {
            case ROUTE_INSPECTION -> inspectionCapabilities(capabilities);
            case ROUTE_NL2SQL -> nl2sqlCapabilities(capabilities);
            case ROUTE_DATA -> dataCapabilities(capabilities);
            default -> List.of();
        };
    }

    public static String nodeLabel(String route) {
        return switch (route) {
            case ROUTE_INSPECTION -> "巡检查询";
            case ROUTE_NL2SQL -> "自然语言查数";
            case ROUTE_KNOWLEDGE -> "知识问答";
            default -> "数据查询";
        };
    }

    private static boolean containsChineseAlias(String upper, String route) {
        return switch (route) {
            case ROUTE_KNOWLEDGE -> upper.contains("知识") || upper.contains("文档");
            case ROUTE_INSPECTION -> upper.contains("巡检");
            case ROUTE_NL2SQL -> upper.contains("SQL") || upper.contains("查数");
            case ROUTE_DATA -> upper.contains("数据") || upper.contains("遥测") || upper.contains("告警");
            default -> false;
        };
    }

    private static List<String> filterCaps(List<String> capabilities, Set<String> allow) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        return capabilities.stream()
                .filter(c -> c != null && allow.contains(c.trim().toUpperCase(Locale.ROOT)))
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
