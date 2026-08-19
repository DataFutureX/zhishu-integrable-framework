package com.datafuturex.assistant.agent.runtime;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.agent.enums.WorkflowType;
import com.datafuturex.assistant.agent.graph.GraphWorkflowExecutor;
import com.datafuturex.assistant.agent.graph.WorkflowGraph;
import com.datafuturex.assistant.agent.graph.WorkflowGraphCompiler;
import com.datafuturex.assistant.agent.registry.ToolCapabilityRegistry;
import com.datafuturex.assistant.agent.support.AgentJsonUtils;
import com.datafuturex.assistant.agent.support.RoutingCapabilitySupport;
import com.datafuturex.assistant.shared.exception.AiException;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
import com.datafuturex.assistant.knowledge.api.HybridRetrievalPort;
import com.datafuturex.assistant.biztools.api.GraphContextEnrichPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 默认引擎：ChatClient 实现 REACT / SEQUENTIAL / ROUTING，以及 GRAPH。
 * <p>
 * 无 Tools 的 LLM 阶段在提供 onToken 时走真流式；含 Tools 时同步 call + Tool progress。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatClientAgentEngine implements AgentEngine {

    public static final String NAME = "chatclient";

    private final ChatClientSupport chatClientSupport;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final HybridRetrievalPort hybridRetrievalService;
    private final ModelConfigPort aiModelConfigService;
    private final WorkflowGraphCompiler workflowGraphCompiler;
    private final GraphWorkflowExecutor graphWorkflowExecutor;
    private final GraphContextEnrichPort graphContextEnricher;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AgentRuntimeResult execute(AgentRuntimeRequest request) {
        AiAgentEntity agent = request.agent();
        List<AgentTraceEvent> traces = new ArrayList<>();
        String conversationId = request.conversationId();
        boolean enableMemory = request.enableMemory();
        List<String> capabilities = AgentJsonUtils.parseCapabilities(agent.getCapabilities());
        String userText = enrichUserMessage(
                request.message(), request.enableRag(), capabilities, request.documentIds());
        WorkflowType workflowType = WorkflowType.require(agent.getWorkflowType());

        try {
            String content;
            if (workflowType == WorkflowType.GRAPH || shouldForceGraph(agent)) {
                WorkflowGraph graph = workflowGraphCompiler.resolveGraph(
                        agent.getWorkflowType(),
                        agent.getWorkflowConfig(),
                        capabilities,
                        agent.getSystemPrompt());
                content = graphWorkflowExecutor.execute(
                        graph, agent, userText, conversationId, enableMemory, traces,
                        request::emitProgress, request.streamingTokens() ? request::emitToken : null);
            } else {
                content = switch (workflowType) {
                    case REACT -> runReact(agent, capabilities, userText, conversationId, enableMemory, traces, request);
                    case SEQUENTIAL -> runSequential(agent, capabilities, userText, conversationId, enableMemory, traces, request);
                    case ROUTING -> runRouting(agent, capabilities, userText, conversationId, enableMemory, traces, request);
                    case GRAPH -> throw new IllegalStateException("unreachable");
                };
            }
            return new AgentRuntimeResult(
                    content,
                    chatClientSupport.resolveModelName(agent),
                    conversationId,
                    traces,
                    NAME,
                    null);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("ChatClientAgentEngine 失败 agentId={}: {}", agent.getId(), e.getMessage(), e);
            throw new AiException("智能体执行失败: " + e.getMessage());
        }
    }

    private boolean shouldForceGraph(AiAgentEntity agent) {
        String cfg = agent.getWorkflowConfig();
        return StringUtils.hasText(cfg) && cfg.contains("\"nodes\"") && cfg.contains("\"edges\"")
                && cfg.contains("\"version\"");
    }

    private void trace(
            List<AgentTraceEvent> traces,
            AgentRuntimeRequest request,
            AgentTraceEvent event) {
        traces.add(event);
        request.emitProgress(event);
    }

    private String runReact(
            AiAgentEntity agent,
            List<String> capabilities,
            String userText,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces,
            AgentRuntimeRequest request) {
        long start = System.currentTimeMillis();
        trace(traces, request, AgentTraceEvent.of("NODE_START", "REACT", "TOOL_AGENT", null));
        OpenAiChatOptions options = chatClientSupport.buildOptions(agent);
        List<ToolCallback> tools = chatClientSupport.resolveTools(
                capabilities, traces, request.onProgress(), agent.getId());
        ChatClient client = chatClientSupport.buildClient(
                agent.getSystemPrompt(), tools, options, enableMemory);
        // 无 Tools 时可真流式；有 Tools 必须同步 call
        Consumer<String> tokenCb = tools.isEmpty() && request.streamingTokens() ? request::emitToken : null;
        String content = chatClientSupport.callOrStream(
                client, userText, conversationId, enableMemory, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "REACT", "完成", System.currentTimeMillis() - start));
        return content;
    }

    private String runSequential(
            AiAgentEntity agent,
            List<String> capabilities,
            String userText,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces,
            AgentRuntimeRequest request) {
        OpenAiChatOptions options = chatClientSupport.buildOptions(agent);

        trace(traces, request, AgentTraceEvent.of("NODE_START", "意图澄清", "LLM", null));
        long t1 = System.currentTimeMillis();
        ChatClient clarify = chatClientSupport.buildClient(
                "你是意图澄清助手。用一两句中文重述用户监测或巡检相关需求要点；日报/月报/年报未指定日期时分别按当日/当月/当年理解（以系统注入时间为准），不要编造数据与日期，不要调用工具。",
                List.of(), options, false);
        String clarified = chatClientSupport.call(clarify, userText, null, false);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "意图澄清", clarified == null ? "" : clarified,
                System.currentTimeMillis() - t1));

        trace(traces, request, AgentTraceEvent.of("NODE_START", "工具执行", "TOOL_AGENT", null));
        long t2 = System.currentTimeMillis();
        String workInput = """
                用户原问题：
                %s

                意图澄清结果：
                %s
                """.formatted(userText, clarified == null ? "" : clarified);
        List<ToolCallback> tools = chatClientSupport.resolveTools(
                capabilities, traces, request.onProgress(), agent.getId());
        ChatClient main = chatClientSupport.buildClient(
                agent.getSystemPrompt(), tools, options, enableMemory);
        String worked = chatClientSupport.call(main, workInput, conversationId, enableMemory);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "工具执行", truncate(worked, 200),
                System.currentTimeMillis() - t2));

        trace(traces, request, AgentTraceEvent.of("NODE_START", "结果润色", "LLM", null));
        long t3 = System.currentTimeMillis();
        String polishInput = """
                用户原问题：
                %s

                中间结果：
                %s

                请润色为最终回答。
                """.formatted(userText, worked == null ? "" : worked);
        ChatClient polish = chatClientSupport.buildClient(
                "你是结果润色助手。将上一阶段结果整理为清晰专业的中文 Markdown，保留关键数据、表格与正确的报告周期，不要编造日期或数据。",
                List.of(), options, false);
        Consumer<String> tokenCb = request.streamingTokens() ? request::emitToken : null;
        String polished = chatClientSupport.callOrStream(polish, polishInput, null, false, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "结果润色", truncate(polished, 200),
                System.currentTimeMillis() - t3));
        return StringUtils.hasText(polished) ? polished : (worked == null ? "" : worked);
    }

    private String runRouting(
            AiAgentEntity agent,
            List<String> capabilities,
            String userText,
            String conversationId,
            boolean enableMemory,
            List<AgentTraceEvent> traces,
            AgentRuntimeRequest request) {
        OpenAiChatOptions options = chatClientSupport.buildOptions(agent);

        trace(traces, request, AgentTraceEvent.of("NODE_START", "路由", "ROUTER", null));
        long t1 = System.currentTimeMillis();
        String routerPrompt = RoutingCapabilitySupport.buildRouterPrompt(capabilities);
        ChatClient router = chatClientSupport.buildClient(routerPrompt, List.of(), options, false);
        String routeRaw = chatClientSupport.call(router, userText, null, false);
        String route = RoutingCapabilitySupport.resolveRoute(routeRaw, capabilities);
        AgentTraceEvent routeEv = AgentTraceEvent.of("ROUTE", "路由", "route=" + route,
                System.currentTimeMillis() - t1);
        trace(traces, request, routeEv);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "路由", route,
                System.currentTimeMillis() - t1));

        String nodeName = RoutingCapabilitySupport.nodeLabel(route);
        boolean knowledge = RoutingCapabilitySupport.ROUTE_KNOWLEDGE.equals(route);
        trace(traces, request, AgentTraceEvent.of("NODE_START", nodeName, knowledge ? "LLM" : "TOOL_AGENT", null));
        long t2 = System.currentTimeMillis();
        ChatClient target;
        Consumer<String> tokenCb = null;
        if (knowledge) {
            target = chatClientSupport.buildClient(
                    """
                            你是知识问答子智能体。请基于用户消息及其中【知识库检索片段】严谨作答。
                            若片段不足请明确说明，不要编造监测数据。使用中文。
                            """,
                    List.of(), options, false);
            tokenCb = request.streamingTokens() ? request::emitToken : null;
        } else {
            List<String> routeCaps = RoutingCapabilitySupport.capabilitiesForRoute(route, capabilities);
            List<ToolCallback> tools = chatClientSupport.resolveTools(
                    routeCaps, traces, request.onProgress(), agent.getId());
            target = chatClientSupport.buildClient(agent.getSystemPrompt(), tools, options, enableMemory);
        }
        String content = chatClientSupport.callOrStream(
                target, userText, conversationId, enableMemory, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", nodeName, truncate(content, 200),
                System.currentTimeMillis() - t2));
        log.info("Agent 路由 conversationId={}, routeRaw={}, route={}",
                conversationId, routeRaw, route);
        return content;
    }

    private String enrichUserMessage(
            String message,
            Boolean enableRag,
            List<String> capabilities,
            List<Long> documentIds) {
        String text = message;
        boolean wantRag = enableRag != null
                ? enableRag
                : aiModelConfigService.currentEnableRagDefault();
        if (wantRag && toolCapabilityRegistry.supportsRag(capabilities)) {
            text = hybridRetrievalService.enrichUserMessage(text, 5, documentIds);
        }
        if (capabilities != null && capabilities.stream()
                .anyMatch(c -> "KNOWLEDGE_GRAPH".equalsIgnoreCase(c))) {
            text = graphContextEnricher.enrich(text);
        }
        return text;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
