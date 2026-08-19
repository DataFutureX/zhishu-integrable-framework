package com.datafuturex.assistant.agent.graph;

import com.datafuturex.assistant.agent.enums.WorkflowType;
import com.datafuturex.assistant.agent.support.RoutingCapabilitySupport;
import com.datafuturex.assistant.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Graph JSON 解析、校验与预设模板编译。
 */
@Component
public class WorkflowGraphCompiler {

    private final ObjectMapper objectMapper;

    public WorkflowGraphCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowGraph parse(String workflowConfig) {
        if (!StringUtils.hasText(workflowConfig)) {
            throw new BusinessException("workflow_config 为空");
        }
        try {
            return readGraph(workflowConfig);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception first) {
            try {
                // 兼容历史 SQL replace 误写入真实换行导致的非法 JSON
                return readGraph(escapeRawControlsInsideJsonStrings(workflowConfig));
            } catch (Exception second) {
                throw new BusinessException("workflow_config JSON 无效: " + first.getMessage());
            }
        }
    }

    private WorkflowGraph readGraph(String workflowConfig) throws Exception {
        WorkflowGraph graph = objectMapper.readValue(workflowConfig, WorkflowGraph.class);
        if (graph.getVersion() <= 0) {
            graph.setVersion(1);
        }
        return graph;
    }

    /**
     * 将 JSON 字符串字面量内部的裸换行 / 回车 / 制表符转义为 \\n / \\r / \\t。
     */
    static String escapeRawControlsInsideJsonStrings(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (inString && c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (inString) {
                if (c == '\n') {
                    sb.append("\\n");
                    continue;
                }
                if (c == '\r') {
                    sb.append("\\r");
                    continue;
                }
                if (c == '\t') {
                    sb.append("\\t");
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public String toJson(WorkflowGraph graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (Exception e) {
            throw new BusinessException("序列化 Graph 失败: " + e.getMessage());
        }
    }

    public GraphValidationResult validate(WorkflowGraph graph) {
        List<String> errors = new ArrayList<>();
        if (graph == null) {
            return GraphValidationResult.fail("Graph 为空");
        }
        if (graph.getVersion() != 1) {
            errors.add("仅支持 version=1");
        }
        List<GraphNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<GraphEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        if (nodes.isEmpty()) {
            errors.add("至少需要一个节点");
        }
        Set<String> ids = new HashSet<>();
        int startCount = 0;
        int endCount = 0;
        for (GraphNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getId())) {
                errors.add("存在缺少 id 的节点");
                continue;
            }
            if (!ids.add(node.getId())) {
                errors.add("节点 id 重复: " + node.getId());
            }
            try {
                GraphNodeType type = GraphNodeType.require(node.getType());
                if (type == GraphNodeType.START) {
                    startCount++;
                } else if (type == GraphNodeType.END) {
                    endCount++;
                }
            } catch (Exception e) {
                errors.add("节点 " + node.getId() + " 类型无效: " + node.getType());
            }
        }
        if (startCount != 1) {
            errors.add("必须有且仅有一个 START 节点");
        }
        if (endCount != 1) {
            errors.add("必须有且仅有一个 END 节点");
        }
        Set<String> edgeIds = new HashSet<>();
        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, Integer> conditionedOut = new HashMap<>();
        for (GraphEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            if (!StringUtils.hasText(edge.getId())) {
                errors.add("存在缺少 id 的边");
            } else if (!edgeIds.add(edge.getId())) {
                errors.add("边 id 重复: " + edge.getId());
            }
            if (!ids.contains(edge.getSource())) {
                errors.add("边 source 不存在: " + edge.getSource());
            }
            if (!ids.contains(edge.getTarget())) {
                errors.add("边 target 不存在: " + edge.getTarget());
            }
            if (StringUtils.hasText(edge.getSource())) {
                outDegree.merge(edge.getSource(), 1, Integer::sum);
                if (StringUtils.hasText(edge.getCondition())) {
                    conditionedOut.merge(edge.getSource(), 1, Integer::sum);
                }
            }
        }
        for (GraphNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getId())) {
                continue;
            }
            try {
                if (GraphNodeType.require(node.getType()) == GraphNodeType.CONDITIONAL) {
                    int outs = outDegree.getOrDefault(node.getId(), 0);
                    if (outs < 1) {
                        errors.add("CONDITIONAL 节点至少需要一条出边: " + node.getId());
                    } else if (outs >= 2 && conditionedOut.getOrDefault(node.getId(), 0) < 1) {
                        errors.add("CONDITIONAL 多出边时至少一条需配置 condition: " + node.getId());
                    }
                }
            } catch (Exception ignored) {
                // 类型错误已在上文记录
            }
        }
        return errors.isEmpty() ? GraphValidationResult.ok() : GraphValidationResult.fail(errors);
    }

    public WorkflowGraph compileTemplate(WorkflowType type, List<String> capabilities, String systemPrompt) {
        return switch (type) {
            case REACT -> compileReact(capabilities, systemPrompt);
            case SEQUENTIAL -> compileSequential(capabilities, systemPrompt);
            case ROUTING -> compileRouting(capabilities, systemPrompt);
            case GRAPH -> throw new BusinessException("GRAPH 不是可编译模板，请直接编辑 Graph");
        };
    }

    public WorkflowGraph resolveGraph(String workflowType, String workflowConfig,
                                      List<String> capabilities, String systemPrompt) {
        WorkflowType type = WorkflowType.require(workflowType);
        if (type == WorkflowType.GRAPH || hasGraphPayload(workflowConfig)) {
            WorkflowGraph graph;
            try {
                graph = parse(workflowConfig);
            } catch (BusinessException ex) {
                // GRAPH 配置 JSON 损坏时回退顺序模板，保证编排页可打开并重新保存
                if (type == WorkflowType.GRAPH) {
                    return compileTemplate(WorkflowType.SEQUENTIAL, capabilities, systemPrompt);
                }
                throw ex;
            }
            GraphValidationResult vr = validate(graph);
            if (!vr.valid()) {
                throw new BusinessException("Graph 校验失败: " + String.join("; ", vr.errors()));
            }
            return graph;
        }
        return compileTemplate(type, capabilities, systemPrompt);
    }

    private boolean hasGraphPayload(String workflowConfig) {
        if (!StringUtils.hasText(workflowConfig)) {
            return false;
        }
        String trimmed = workflowConfig.trim();
        return trimmed.contains("\"nodes\"") && trimmed.contains("\"edges\"");
    }

    private WorkflowGraph compileReact(List<String> capabilities, String systemPrompt) {
        WorkflowGraph g = base();
        g.getNodes().add(node("start", "START", "开始", 80, 160));
        g.getNodes().add(toolNode("worker", "执行", capabilities, systemPrompt, 320, 160));
        g.getNodes().add(node("end", "END", "结束", 560, 160));
        g.getEdges().add(edge("e1", "start", "worker", null, null));
        g.getEdges().add(edge("e2", "worker", "end", null, null));
        return g;
    }

    private WorkflowGraph compileSequential(List<String> capabilities, String systemPrompt) {
        WorkflowGraph g = base();
        g.getNodes().add(node("start", "START", "开始", 40, 160));
        g.getNodes().add(llmNode("clarify", "意图澄清",
                "你是意图澄清助手。用一两句中文重述用户监测或巡检相关需求要点；日报/月报/年报未指定日期时分别按当日/当月/当年理解（以系统注入时间为准），不要编造数据与日期，不要调用工具。",
                200, 160));
        g.getNodes().add(toolNode("worker", "工具执行", capabilities, systemPrompt, 400, 160));
        g.getNodes().add(llmNode("polish", "结果润色",
                "你是结果润色助手。将上一阶段结果整理为清晰专业的中文 Markdown，保留关键数据、表格与正确的报告周期，不要编造日期或数据。",
                600, 160));
        g.getNodes().add(node("end", "END", "结束", 800, 160));
        g.getEdges().add(edge("e1", "start", "clarify", null, null));
        g.getEdges().add(edge("e2", "clarify", "worker", null, null));
        g.getEdges().add(edge("e3", "worker", "polish", null, null));
        g.getEdges().add(edge("e4", "polish", "end", null, null));
        return g;
    }

    private WorkflowGraph compileRouting(List<String> capabilities, String systemPrompt) {
        WorkflowGraph g = base();
        List<String> routes = RoutingCapabilitySupport.availableRoutes(capabilities);
        g.getNodes().add(node("start", "START", "开始", 80, 220));
        Map<String, Object> routerData = new LinkedHashMap<>();
        routerData.put("systemPrompt", RoutingCapabilitySupport.buildRouterPrompt(capabilities));
        GraphNode router = node("router", "ROUTER", "路由", 260, 220);
        router.setData(routerData);
        g.getNodes().add(router);

        double y = 40;
        int edgeIdx = 2;
        g.getEdges().add(edge("e1", "start", "router", null, null));
        for (String route : routes) {
            String nodeId = route.toLowerCase(Locale.ROOT);
            String label = RoutingCapabilitySupport.nodeLabel(route);
            if (RoutingCapabilitySupport.ROUTE_KNOWLEDGE.equals(route)) {
                g.getNodes().add(llmNode(nodeId, label,
                        """
                                你是知识问答子智能体。请基于用户消息及其中【知识库检索片段】严谨作答。
                                若片段不足请明确说明，不要编造监测数据。使用中文。
                                """,
                        520, y));
            } else {
                List<String> caps = RoutingCapabilitySupport.capabilitiesForRoute(route, capabilities);
                g.getNodes().add(toolNode(nodeId, label, caps, systemPrompt, 520, y));
            }
            g.getEdges().add(edge("e" + edgeIdx++, "router", nodeId, route, route));
            g.getEdges().add(edge("e" + edgeIdx++, nodeId, "end", null, null));
            y += 140;
        }
        g.getNodes().add(node("end", "END", "结束", 760, 220));
        return g;
    }

    private static WorkflowGraph base() {
        WorkflowGraph g = new WorkflowGraph();
        g.setVersion(1);
        return g;
    }

    private static GraphNode node(String id, String type, String label, double x, double y) {
        GraphNode n = new GraphNode();
        n.setId(id);
        n.setType(type);
        n.setLabel(label);
        n.setPositionX(x);
        n.setPositionY(y);
        n.setData(new LinkedHashMap<>());
        return n;
    }

    private static GraphNode llmNode(String id, String label, String systemPrompt, double x, double y) {
        GraphNode n = node(id, "LLM", label, x, y);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("systemPrompt", systemPrompt);
        n.setData(data);
        return n;
    }

    private static GraphNode toolNode(String id, String label, List<String> capabilities,
                                      String systemPrompt, double x, double y) {
        GraphNode n = node(id, "TOOL_AGENT", label, x, y);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("capabilities", capabilities == null ? List.of() : capabilities);
        data.put("systemPrompt", systemPrompt == null ? "" : systemPrompt);
        n.setData(data);
        return n;
    }

    private static GraphEdge edge(String id, String source, String target, String condition, String label) {
        GraphEdge e = new GraphEdge();
        e.setId(id);
        e.setSource(source);
        e.setTarget(target);
        e.setCondition(condition);
        e.setLabel(label);
        return e;
    }

    /** 供 ROUTER 选边：按文本匹配 condition，否则默认边 */
    public static String pickNext(List<GraphEdge> outs, String routeText) {
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        String route = routeText == null ? "" : routeText.trim().toUpperCase(Locale.ROOT);
        GraphEdge defaultEdge = null;
        for (GraphEdge edge : outs) {
            if (!StringUtils.hasText(edge.getCondition())) {
                if (defaultEdge == null) {
                    defaultEdge = edge;
                }
                continue;
            }
            String cond = edge.getCondition().trim().toUpperCase(Locale.ROOT);
            if (route.contains(cond) || (routeText != null && routeText.contains(edge.getCondition()))) {
                return edge.getTarget();
            }
        }
        return defaultEdge != null ? defaultEdge.getTarget() : outs.get(0).getTarget();
    }

    /**
     * CONDITIONAL 选边：支持 contains:/!contains:/equals:/regex: 前缀，或与 ROUTER 相同的关键字包含。
     */
    public static String pickConditionalNext(List<GraphEdge> outs, String text) {
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        String probe = text == null ? "" : text;
        GraphEdge defaultEdge = null;
        for (GraphEdge edge : outs) {
            if (!StringUtils.hasText(edge.getCondition())) {
                if (defaultEdge == null) {
                    defaultEdge = edge;
                }
                continue;
            }
            if (matchCondition(edge.getCondition().trim(), probe)) {
                return edge.getTarget();
            }
        }
        return defaultEdge != null ? defaultEdge.getTarget() : outs.get(0).getTarget();
    }

    static boolean matchCondition(String condition, String text) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        String probe = text == null ? "" : text;
        String lower = probe.toLowerCase(Locale.ROOT);
        String c = condition.trim();
        String lowerCond = c.toLowerCase(Locale.ROOT);
        if (lowerCond.startsWith("contains:")) {
            String needle = c.substring("contains:".length()).trim();
            return !needle.isEmpty() && lower.contains(needle.toLowerCase(Locale.ROOT));
        }
        if (lowerCond.startsWith("!contains:") || lowerCond.startsWith("notcontains:")) {
            int idx = lowerCond.startsWith("!contains:") ? "!contains:".length() : "notcontains:".length();
            String needle = c.substring(idx).trim();
            return needle.isEmpty() || !lower.contains(needle.toLowerCase(Locale.ROOT));
        }
        if (lowerCond.startsWith("equals:")) {
            String expected = c.substring("equals:".length()).trim();
            return probe.trim().equalsIgnoreCase(expected);
        }
        if (lowerCond.startsWith("regex:")) {
            String pattern = c.substring("regex:".length()).trim();
            if (pattern.isEmpty()) {
                return false;
            }
            try {
                return java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                        .matcher(probe)
                        .find();
            } catch (Exception e) {
                return false;
            }
        }
        // 兼容 ROUTER 风格：condition 关键字出现在文本中
        return probe.toUpperCase(Locale.ROOT).contains(c.toUpperCase(Locale.ROOT))
                || probe.contains(c);
    }

    public static Map<String, List<GraphEdge>> indexOutEdges(WorkflowGraph graph) {
        Map<String, List<GraphEdge>> map = new HashMap<>();
        if (graph.getEdges() == null) {
            return map;
        }
        for (GraphEdge edge : graph.getEdges()) {
            map.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
        }
        return map;
    }

    public static GraphNode findStart(WorkflowGraph graph) {
        return graph.getNodes().stream()
                .filter(n -> "START".equalsIgnoreCase(n.getType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("缺少 START 节点"));
    }

    public static GraphNode findById(WorkflowGraph graph, String id) {
        return graph.getNodes().stream()
                .filter(n -> id.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("节点不存在: " + id));
    }
}
