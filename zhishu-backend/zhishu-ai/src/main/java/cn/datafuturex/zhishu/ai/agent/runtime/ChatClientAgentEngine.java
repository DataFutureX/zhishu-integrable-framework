package cn.datafuturex.zhishu.ai.agent.runtime;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.enums.WorkflowType;
import cn.datafuturex.zhishu.ai.agent.graph.GraphWorkflowExecutor;
import cn.datafuturex.zhishu.ai.agent.graph.WorkflowGraph;
import cn.datafuturex.zhishu.ai.agent.graph.WorkflowGraphCompiler;
import cn.datafuturex.zhishu.ai.agent.registry.ToolCapabilityRegistry;
import cn.datafuturex.zhishu.ai.agent.support.AgentJsonUtils;
import cn.datafuturex.zhishu.ai.agent.support.RoutingCapabilitySupport;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.knowledge.api.HybridRetrievalPort;
import cn.datafuturex.zhishu.ai.biztools.api.GraphContextEnrichPort;
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
    private final ModelProviderRegistry modelProviderRegistry;
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

        // 打印模型调用详细信息
        logModelCallInfo(agent);
        long execStart = System.currentTimeMillis();

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
            long elapsed = System.currentTimeMillis() - execStart;
            log.info("[Agent完成] agentId={}, agentName={}, workflow={}, model={}, 耗时={}ms",
                    agent.getId(), agent.getName(), workflowType,
                    chatClientSupport.resolveModelName(agent), elapsed);
            return new AgentRuntimeResult(
                    content,
                    chatClientSupport.resolveModelName(agent),
                    conversationId,
                    traces,
                    NAME,
                    null);
        } catch (AiException e) {
            log.error("[LLM错误] agentId={}, agentName={}, modelProviderId={}, model={}, 错误: {}",
                    agent.getId(), agent.getName(), agent.getModelProviderId(),
                    chatClientSupport.resolveModelName(agent), e.getMessage());
            throw new AiException(rewriteLlmError(e.getMessage()));
        } catch (Exception e) {
            if (isInterruptedException(e)) {
                Thread.currentThread().interrupt();
                log.warn("[LLM中断] agentId={}, agentName={}, model={}, 原因: {}",
                        agent.getId(), agent.getName(),
                        chatClientSupport.resolveModelName(agent), e.getMessage(), e);
                throw new AiException("模型调用被中断（可能因请求超时或客户端断开连接）");
            }
            log.error("[LLM错误] agentId={}, agentName={}, modelProviderId={}, model={}, 错误: {}",
                    agent.getId(), agent.getName(), agent.getModelProviderId(),
                    chatClientSupport.resolveModelName(agent), e.getMessage(), e);
            throw new AiException("智能体执行失败: " + rewriteLlmError(e.getMessage()));
        }
    }

    /**
     * 打印模型调用详细信息（agent、provider、baseUrl、model）。
     */
    private void logModelCallInfo(AiAgentEntity agent) {
        Long providerId = agent.getModelProviderId();
        String modelName = chatClientSupport.resolveModelName(agent);
        ModelProviderEntity provider = modelProviderRegistry.getProvider(providerId);
        String baseUrl = modelProviderRegistry.resolveBaseUrl(providerId);
        log.warn("[LLM调用] agentId={}, agentName={}, workflow={}, providerName={}, baseUrl={}, model={}",
                agent.getId(), agent.getName(), agent.getWorkflowType(),
                provider != null ? provider.getName() : "(默认)",
                baseUrl, modelName);
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
        ChatClientSupport.setStepLabel("REACT");
        OpenAiChatOptions options = chatClientSupport.buildOptions(agent);
        List<ToolCallback> tools = chatClientSupport.resolveTools(
                capabilities, traces, request.onProgress(), agent.getId());
        ChatClient client = chatClientSupport.buildClient(
                agent.getModelProviderId(), agent.getSystemPrompt(), tools, options, enableMemory);
        // 无 Tools 时可真流式；有 Tools 必须同步 call
        Consumer<String> tokenCb = tools.isEmpty() && request.streamingTokens() ? request::emitToken : null;
        String content = chatClientSupport.callOrStream(
                client, userText, conversationId, enableMemory, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "REACT", "完成", System.currentTimeMillis() - start));
        ChatClientSupport.setStepLabel(null);
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
        ChatClientSupport.setStepLabel("意图澄清");
        long t1 = System.currentTimeMillis();
        ChatClient clarify = chatClientSupport.buildClient(
                agent.getModelProviderId(),
                """
                你是意图澄清助手。用中文重述用户监测或巡检相关需求要点。
                硬性要求：
                1. 如果用户请求涉及报告、简报、日报、月报、年报或综合概览，必须逐条列出需要获取的数据类别及对应工具名（如在线状态 → getTerminalOnlineOverview，告警趋势 → queryRecentAlerts / queryAlertTrends，巡检 → listInspectionPlans / listInspectionTasks）。
                2. 日报/月报/年报未指定日期时分别按当日/当月/当年理解（以系统注入时间为准）。
                3. 不要编造数据与日期，不要调用工具。
                """,
                List.of(), options, false);
        String clarified = chatClientSupport.call(clarify, userText, null, false);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "意图澄清", clarified == null ? "" : clarified,
                System.currentTimeMillis() - t1));

        trace(traces, request, AgentTraceEvent.of("NODE_START", "工具执行", "TOOL_AGENT", null));
        ChatClientSupport.setStepLabel("工具执行");
        long t2 = System.currentTimeMillis();
        String workInput = """
                用户原问题：
                %s

                意图澄清结果：
                %s

                【执行要求】
                - 必须逐一调用所有相关工具获取每一类数据（在线状态、告警、巡检等），禁止只调用一个工具就输出结论。
                - 每个工具调用完成后，记录返回的关键数据。
                - 所有工具调用完成后，将全部数据综合整理为一份结构化的 Markdown 报告。
                - 报告需包含用户要求的各章节（如在线概览、告警趋势、巡检摘要、建议关注等）。
                - 禁止只输出站点列表或单一工具的原始返回结果。
                - 若某类工具不可用或调用失败，在报告中明确标注该部分数据缺失及原因，不要用其他数据替代。
                """.formatted(userText, clarified == null ? "" : clarified);
        List<ToolCallback> tools = chatClientSupport.resolveTools(
                capabilities, traces, request.onProgress(), agent.getId());
        ChatClient main = chatClientSupport.buildClient(
                agent.getModelProviderId(), agent.getSystemPrompt(), tools, options, enableMemory);
        String worked = chatClientSupport.call(main, workInput, conversationId, enableMemory);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "工具执行", truncate(worked, 200),
                System.currentTimeMillis() - t2));

        trace(traces, request, AgentTraceEvent.of("NODE_START", "结果润色", "LLM", null));
        ChatClientSupport.setStepLabel("结果润色");
        long t3 = System.currentTimeMillis();
        String polishInput = """
                用户原问题：
                %s

                中间结果：
                %s

                【润色要求】
                1. 将中间结果整理为清晰专业的中文 Markdown 报告，保留所有关键数据与表格。
                2. 若用户要求的是报告/简报/概览，输出必须包含用户要求的各章节结构（如在线概览、告警趋势、巡检摘要、建议关注等），不要只输出站点列表。
                3. 若中间结果缺少某些章节的数据（如只有在线状态而无告警或巡检数据），请在对应章节标注「该部分数据未获取」，不要编造。
                4. 保留正确的报告周期，不要编造日期或数据。
                5. 综合总结，避免简单罗列工具返回的原始 JSON 或表格。
                """.formatted(userText, worked == null ? "" : worked);
        ChatClient polish = chatClientSupport.buildClient(
                agent.getModelProviderId(),
                "你是结果润色助手。将上一阶段结果整理为清晰专业的中文 Markdown 报告，保留关键数据、表格与正确的报告周期，不要编造日期或数据。",
                List.of(), options, false);
        Consumer<String> tokenCb = request.streamingTokens() ? request::emitToken : null;
        String polished = chatClientSupport.callOrStream(polish, polishInput, null, false, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "结果润色", truncate(polished, 200),
                System.currentTimeMillis() - t3));
        ChatClientSupport.setStepLabel(null);
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
        ChatClientSupport.setStepLabel("路由");
        long t1 = System.currentTimeMillis();
        String routerPrompt = RoutingCapabilitySupport.buildRouterPrompt(capabilities);
        ChatClient router = chatClientSupport.buildClient(
                agent.getModelProviderId(), routerPrompt, List.of(), options, false);
        String routeRaw = chatClientSupport.call(router, userText, null, false);
        String route = RoutingCapabilitySupport.resolveRoute(routeRaw, capabilities);
        AgentTraceEvent routeEv = AgentTraceEvent.of("ROUTE", "路由", "route=" + route,
                System.currentTimeMillis() - t1);
        trace(traces, request, routeEv);
        trace(traces, request, AgentTraceEvent.of("NODE_END", "路由", route,
                System.currentTimeMillis() - t1));

        String nodeName = RoutingCapabilitySupport.nodeLabel(route);
        boolean knowledge = RoutingCapabilitySupport.ROUTE_KNOWLEDGE.equals(route);
        ChatClientSupport.setStepLabel(nodeName);
        trace(traces, request, AgentTraceEvent.of("NODE_START", nodeName, knowledge ? "LLM" : "TOOL_AGENT", null));
        long t2 = System.currentTimeMillis();
        ChatClient target;
        Consumer<String> tokenCb = null;
        if (knowledge) {
            target = chatClientSupport.buildClient(
                    agent.getModelProviderId(),
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
            target = chatClientSupport.buildClient(
                    agent.getModelProviderId(), agent.getSystemPrompt(), tools, options, enableMemory);
        }
        String content = chatClientSupport.callOrStream(
                target, userText, conversationId, enableMemory, tokenCb);
        trace(traces, request, AgentTraceEvent.of("NODE_END", nodeName, truncate(content, 200),
                System.currentTimeMillis() - t2));
        log.info("Agent 路由 conversationId={}, routeRaw={}, route={}",
                conversationId, routeRaw, route);
        ChatClientSupport.setStepLabel(null);
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

    private static String rewriteLlmError(String message) {
        if (message != null && (message.contains("Incorrect API key") || message.contains("invalid_api_key"))) {
            return "模型 API Key 无效。请打开「模型设置」重新粘贴完整密钥（不要带引号或 Bearer 前缀）；"
                    + "若密钥来自国际站，Base URL 需改为 https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
        }
        return message == null ? "未知错误" : message;
    }

    /**
     * 递归检查异常链中是否包含 InterruptedException（Reactor 会包装异常）。
     */
    private static boolean isInterruptedException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof InterruptedException
                    || current instanceof java.io.InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
