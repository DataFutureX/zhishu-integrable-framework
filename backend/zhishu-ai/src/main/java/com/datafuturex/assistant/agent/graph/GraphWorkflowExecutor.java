package com.datafuturex.assistant.agent.graph;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.agent.runtime.ChatClientSupport;
import com.datafuturex.assistant.shared.exception.AiException;
import com.datafuturex.assistant.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 自研 Graph 执行器（语义对齐 StateGraph：按节点类型推进）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GraphWorkflowExecutor {

    private static final int MAX_STEPS = 32;

    private final ChatClientSupport chatClientSupport;
    private final WorkflowGraphCompiler compiler;

    public String execute(
            WorkflowGraph graph,
            AiAgentEntity agent,
            String userMessage,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces) {
        return execute(graph, agent, userMessage, conversationId, enableMemory, traces, null, null);
    }

    public String execute(
            WorkflowGraph graph,
            AiAgentEntity agent,
            String userMessage,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress) {
        return execute(graph, agent, userMessage, conversationId, enableMemory, traces, onProgress, null);
    }

    public String execute(
            WorkflowGraph graph,
            AiAgentEntity agent,
            String userMessage,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress,
            Consumer<String> onToken) {
        GraphValidationResult vr = compiler.validate(graph);
        if (!vr.valid()) {
            throw new BusinessException("Graph 校验失败: " + String.join("; ", vr.errors()));
        }

        OpenAiChatOptions options = chatClientSupport.buildOptions(agent);
        Map<String, List<GraphEdge>> outs = WorkflowGraphCompiler.indexOutEdges(graph);
        GraphNode current = WorkflowGraphCompiler.findStart(graph);
        String lastOutput = userMessage;
        String workingMessage = userMessage;

        for (int step = 0; step < MAX_STEPS; step++) {
            GraphNodeType type = GraphNodeType.require(current.getType());
            String nodeLabel = StringUtils.hasText(current.getLabel()) ? current.getLabel() : current.getId();
            long start = System.currentTimeMillis();
            emit(traces, onProgress, AgentTraceEvent.of("NODE_START", nodeLabel, type.name(), null));

            try {
                switch (type) {
                    case START -> {
                        List<GraphEdge> nexts = outs.getOrDefault(current.getId(), List.of());
                        if (nexts.isEmpty()) {
                            throw new AiException("START 无出边");
                        }
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel, "→ " + nexts.get(0).getTarget(),
                                System.currentTimeMillis() - start));
                        current = WorkflowGraphCompiler.findById(graph, nexts.get(0).getTarget());
                    }
                    case END -> {
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel, "完成",
                                System.currentTimeMillis() - start));
                        return lastOutput == null ? "" : lastOutput;
                    }
                    case LLM -> {
                        String prompt = strData(current, "systemPrompt");
                        if (!StringUtils.hasText(prompt)) {
                            prompt = "你是助手，请用中文简洁回答。";
                        }
                        ChatClient client = chatClientSupport.buildClient(prompt, List.of(), options, false);
                        // 意图澄清等中间节点不推 token，避免污染最终正文；仅「结果润色」或末级 LLM 推送
                        Consumer<String> tokenCb = shouldStreamLlmTokens(nodeLabel, onToken) ? onToken : null;
                        lastOutput = chatClientSupport.callOrStream(
                                client, workingMessage, null, false, tokenCb);
                        String endDetail = isIntentClarifyLabel(nodeLabel)
                                ? (lastOutput == null ? "" : lastOutput)
                                : truncate(lastOutput, 200);
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel,
                                endDetail, System.currentTimeMillis() - start));
                        workingMessage = composeContinue(userMessage, lastOutput);
                        current = nextLinear(outs, current.getId(), graph);
                    }
                    case TOOL_AGENT -> {
                        List<String> caps = capsData(current);
                        String prompt = strData(current, "systemPrompt");
                        if (!StringUtils.hasText(prompt)) {
                            prompt = agent.getSystemPrompt();
                        }
                        List<ToolCallback> tools = chatClientSupport.resolveTools(
                                caps, traces, onProgress, agent.getId());
                        ChatClient client = chatClientSupport.buildClient(
                                prompt, tools, options, enableMemory);
                        Consumer<String> tokenCb = tools.isEmpty() && onToken != null ? onToken : null;
                        lastOutput = chatClientSupport.callOrStream(
                                client, workingMessage, conversationId, enableMemory, tokenCb);
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel,
                                truncate(lastOutput, 200), System.currentTimeMillis() - start));
                        workingMessage = composeContinue(userMessage, lastOutput);
                        current = nextLinear(outs, current.getId(), graph);
                    }
                    case ROUTER -> {
                        String prompt = strData(current, "systemPrompt");
                        if (!StringUtils.hasText(prompt)) {
                            prompt = "你是路由调度器，只输出分支关键字。";
                        }
                        ChatClient client = chatClientSupport.buildClient(prompt, List.of(), options, false);
                        String routeRaw = chatClientSupport.call(client, userMessage, null, false);
                        String nextId = WorkflowGraphCompiler.pickNext(
                                outs.getOrDefault(current.getId(), List.of()), routeRaw);
                        emit(traces, onProgress, AgentTraceEvent.of("ROUTE", nodeLabel,
                                "route=" + (routeRaw == null ? "" : routeRaw.trim()),
                                System.currentTimeMillis() - start));
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel,
                                "→ " + nextId, System.currentTimeMillis() - start));
                        if (!StringUtils.hasText(nextId)) {
                            throw new AiException("ROUTER 无法选择下一节点");
                        }
                        workingMessage = userMessage;
                        current = WorkflowGraphCompiler.findById(graph, nextId);
                        log.info("Graph 路由 conversationId={}, route={}, next={}",
                                conversationId, routeRaw, nextId);
                    }
                    case CONDITIONAL -> {
                        String source = strData(current, "inputSource");
                        String probe = "userMessage".equalsIgnoreCase(source) ? userMessage : lastOutput;
                        String nextId = WorkflowGraphCompiler.pickConditionalNext(
                                outs.getOrDefault(current.getId(), List.of()), probe);
                        emit(traces, onProgress, AgentTraceEvent.of("ROUTE", nodeLabel,
                                "conditional probe=" + truncate(probe, 80) + " → " + nextId,
                                System.currentTimeMillis() - start));
                        emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel,
                                "→ " + nextId, System.currentTimeMillis() - start));
                        if (!StringUtils.hasText(nextId)) {
                            throw new AiException("CONDITIONAL 无法选择下一节点");
                        }
                        current = WorkflowGraphCompiler.findById(graph, nextId);
                        log.info("Graph 条件分支 conversationId={}, next={}", conversationId, nextId);
                    }
                }
            } catch (AiException | BusinessException e) {
                emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel, "ERROR: " + e.getMessage(),
                        System.currentTimeMillis() - start));
                throw e;
            } catch (Exception e) {
                emit(traces, onProgress, AgentTraceEvent.of("NODE_END", nodeLabel, "ERROR: " + e.getMessage(),
                        System.currentTimeMillis() - start));
                throw new AiException("Graph 节点执行失败 [" + nodeLabel + "]: " + e.getMessage());
            }
        }
        throw new AiException("Graph 执行超过最大步数 " + MAX_STEPS);
    }

    private static void emit(
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress,
            AgentTraceEvent event) {
        traces.add(event);
        if (onProgress != null) {
            onProgress.accept(event);
        }
    }

    private static boolean isIntentClarifyLabel(String label) {
        return label != null && (label.contains("意图") || label.contains("澄清"));
    }

    /** 仅对「润色 / 最终回答」类 LLM 节点推送 token，避免中间节点污染 SSE 正文 */
    private static boolean shouldStreamLlmTokens(String label, Consumer<String> onToken) {
        if (onToken == null || label == null) {
            return false;
        }
        return label.contains("润色") || label.contains("最终") || label.contains("回答") || label.contains("知识");
    }

    private static GraphNode nextLinear(Map<String, List<GraphEdge>> outs, String from, WorkflowGraph graph) {
        List<GraphEdge> nexts = outs.getOrDefault(from, List.of());
        if (nexts.isEmpty()) {
            throw new AiException("节点无出边: " + from);
        }
        return WorkflowGraphCompiler.findById(graph, nexts.get(0).getTarget());
    }

    private static String composeContinue(String userMessage, String lastOutput) {
        return """
                用户原问题：
                %s

                上一阶段结果：
                %s
                """.formatted(userMessage, lastOutput == null ? "" : lastOutput);
    }

    private static List<String> capsData(GraphNode node) {
        if (node.getData() == null) {
            return List.of();
        }
        Object raw = node.getData().get("capabilities");
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                }
            }
            return out;
        }
        return List.of();
    }

    private static String strData(GraphNode node, String key) {
        if (node.getData() == null || node.getData().get(key) == null) {
            return null;
        }
        return String.valueOf(node.getData().get(key));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
