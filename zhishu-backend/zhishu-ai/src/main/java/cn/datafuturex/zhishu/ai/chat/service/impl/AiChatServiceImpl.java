package cn.datafuturex.zhishu.ai.chat.service.impl;

import cn.datafuturex.zhishu.ai.agent.api.AgentChatPort;
import cn.datafuturex.zhishu.ai.agent.runtime.ChatClientSupport;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.shared.dto.ChatRequestDTO;
import cn.datafuturex.zhishu.ai.shared.dto.ChatStructuredRequestDTO;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;
import cn.datafuturex.zhishu.ai.shared.vo.structured.AlarmSummary;
import cn.datafuturex.zhishu.ai.shared.vo.structured.StationCompareResult;
import cn.datafuturex.zhishu.ai.shared.vo.structured.TrendAnalysisResult;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import cn.datafuturex.zhishu.ai.chat.service.AiChatService;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.biztools.api.TerminalOverviewPort;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.ai.shared.sse.ChatSseSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * AI 聊天：经 Agent 执行；Memory + 可选 RAG / Hybrid。
 * <p>
 * 流式：progress（节点/Tool）→ 无 Tools 时 token 真流式 message；有 Tools 时同步 call 后伪分片。
 * 规避 Spring AI 2.0.0 流式 Tool 聚合 NoSuchElementException。
 * 结构化输出限制条目并在 JSON 截断时降级，避免 maxTokens 截断导致解析失败。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatServiceImpl implements AiChatService {

    private static final int SSE_CHUNK_SIZE = 48;
    private static final int STRUCTURED_DEFAULT_MAX_TOKENS = 4096;
    private static final int STRUCTURED_MAX_ITEMS = 20;

    private final ChatClient chatClient;
    private final TerminalOverviewPort terminalOverviewPort;
    private final ModelConfigPort aiModelConfigService;
    private final AgentChatPort agentChatPort;
    private final ChatClientSupport chatClientSupport;

    @Override
    public ChatResponseVO chat(ChatRequestDTO request) {
        String conversationId = resolveConversationId(request.conversationId());
        long agentId = agentChatPort.resolveAgentId(request.agentId());
        // if (looksLikeOnlineOverview(request.message())) {
        //     return buildOnlineStatusResponse(conversationId).withAgentId(agentId);
        // }
        try {
            if (Boolean.FALSE.equals(request.enableMemory())) {
                return agentChatPort.run(
                                agentId,
                                request.message(),
                                conversationId,
                                request.enableRag(),
                                false,
                                request.maxTokens(),
                                request.temperature())
                        .withAgentId(agentId);
            }
            return agentChatPort.run(
                            agentId,
                            request.message(),
                            conversationId,
                            request.enableRag(),
                            request.maxTokens(),
                            request.temperature())
                    .withAgentId(agentId);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 对话失败: {}", e.getMessage(), e);
            throw new AiException("AI 服务暂时不可用: " + e.getMessage());
        }
    }

    @Override
    @Cacheable(value = "ai-responses",
            key = "#request.message + ':' + (#request.conversationId ?: '') + ':' + (#request.enableRag ?: false) + ':' + (#request.agentId ?: 0)",
            unless = "#result == null")
    public ChatResponseVO cachedChat(ChatRequestDTO request) {
        return chat(request);
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequestDTO request) {
        String conversationId = resolveConversationId(request.conversationId());
        long agentId = agentChatPort.resolveAgentId(request.agentId());
        // 在线概览：直出 Markdown 表，避免模型只写说明不贴明细
        if (looksLikeOnlineOverview(request.message())) {
            TerminalOverviewPort.Overview overview =
                    terminalOverviewPort.build(TerminalOverviewPort.DEFAULT_DETAIL_LIMIT);
            String body = StringUtils.hasText(overview.markdown()) ? overview.markdown() : overview.summary();
            log.info("流式聊天短路在线概览 conversationId={}, items={}, chars={}",
                    conversationId, overview.items().size(), body == null ? 0 : body.length());
            return ChatSseSupport.toSseFlux(body == null ? "" : body, conversationId);
        }

        log.info("流式聊天(Agent progress+token) conversationId={}, agentId={}, rag={}",
                conversationId, agentId, request.enableRag());

        UserContext.Snapshot userSnapshot = UserContext.snapshot();
        return Flux.<ServerSentEvent<String>>create(sink -> {
                    UserContext.restore(userSnapshot);
                    try {
                        java.util.concurrent.atomic.AtomicBoolean tokenStreamed =
                                new java.util.concurrent.atomic.AtomicBoolean(false);
                        ChatResponseVO vo = agentChatPort.run(
                                agentId,
                                request.message(),
                                conversationId,
                                request.enableRag(),
                                request.maxTokens(),
                                request.temperature(),
                                event -> {
                                    try {
                                        String json = TRACE_MAPPER.writeValueAsString(event);
                                        sink.next(ServerSentEvent.<String>builder()
                                                .event("progress")
                                                .data(json)
                                                .build());
                                    } catch (Exception e) {
                                        log.warn("序列化 progress 失败: {}", e.getMessage());
                                    }
                                },
                                chunk -> {
                                    tokenStreamed.set(true);
                                    sink.next(ServerSentEvent.<String>builder()
                                            .event("message")
                                            .data(chunk)
                                            .build());
                                });
                        // 完整轨迹（兼容旧客户端）
                        if (vo.traces() != null && !vo.traces().isEmpty()) {
                            try {
                                String json = TRACE_MAPPER.writeValueAsString(vo.traces());
                                sink.next(ServerSentEvent.<String>builder()
                                        .event("trace")
                                        .data(json)
                                        .build());
                            } catch (Exception ignored) {
                                // ignore
                            }
                        }
                        // 含 Tools 的路径无 token：结束后伪分片；真流式路径已推送 message
                        if (!tokenStreamed.get()) {
                            String content = vo.content() == null ? "" : vo.content();
                            for (String chunk : ChatSseSupport.splitForSse(content)) {
                                sink.next(ServerSentEvent.<String>builder()
                                        .event("message")
                                        .data(chunk)
                                        .build());
                            }
                        }
                        sink.next(ServerSentEvent.<String>builder()
                                .event("done")
                                .data(conversationId)
                                .build());
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    } finally {
                        UserContext.clear();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> log.error("流式响应失败: {}", error.getMessage(), error))
                .timeout(Duration.ofMinutes(2));
    }

    @Override
    public ChatResponseVO structuredChat(ChatStructuredRequestDTO request) {
        String conversationId = resolveConversationId(request.conversationId());
        long agentId = agentChatPort.resolveAgentId(request.agentId());

        // 在线状态概览：直接查库组装，避免模型吐出超长 JSON / 空表
        if (request.type() == ChatStructuredRequestDTO.StructuredChatType.COMPARE
                && looksLikeOnlineOverview(request.message())) {
            return buildOnlineStatusResponse(conversationId).withAgentId(agentId);
        }

        int maxTokens = request.maxTokens() != null ? request.maxTokens() : STRUCTURED_DEFAULT_MAX_TOKENS;
        Double temperature = request.temperature() != null ? request.temperature() : 0.2;

        try {
            return doStructuredChat(request, conversationId, maxTokens, temperature, STRUCTURED_MAX_ITEMS)
                    .withAgentId(agentId);
        } catch (Exception first) {
            if (!isJsonTruncation(first)) {
                log.error("结构化对话失败: {}", first.getMessage(), first);
                throw new AiException("结构化输出失败: " + first.getMessage());
            }
            log.warn("结构化 JSON 疑似截断，缩减条目后重试: {}", first.getMessage());
            try {
                return doStructuredChat(request, conversationId, maxTokens, temperature, 8)
                        .withAgentId(agentId);
            } catch (Exception second) {
                log.warn("结构化重试仍失败，降级为纯文本: {}", second.getMessage());
                return fallbackTextStructured(request, conversationId, maxTokens, temperature, second)
                        .withAgentId(agentId);
            }
        }
    }

    private ChatResponseVO doStructuredChat(
            ChatStructuredRequestDTO request,
            String conversationId,
            int maxTokens,
            Double temperature,
            int maxItems) {
        long agentId = agentChatPort.resolveAgentId(request.agentId());
        String systemHint = structuredSystemHint(request.type(), maxItems);
        ChatClient client = buildCapabilityScopedClient(agentId, systemHint, maxTokens, temperature);
        var spec = client.prompt()
                .user(request.message() + "\n\n【硬性要求】结构化 items/points 最多 " + maxItems
                        + " 条；多余数据只在 summary 中用文字概括；输出完整合法 JSON。")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        Object structured = switch (request.type()) {
            case COMPARE -> spec.call().entity(StationCompareResult.class);
            case TREND -> spec.call().entity(TrendAnalysisResult.class);
            case ALARM -> spec.call().entity(AlarmSummary.class);
        };

        structured = trimStructured(structured, maxItems);
        String content = summaryOf(request.type(), structured);
        return ChatResponseVO.of(content, aiModelConfigService.currentChatModel(), conversationId, structured);
    }

    private ChatResponseVO fallbackTextStructured(
            ChatStructuredRequestDTO request,
            String conversationId,
            int maxTokens,
            Double temperature,
            Exception cause) {
        try {
            long agentId = agentChatPort.resolveAgentId(request.agentId());
            ChatClient client = buildCapabilityScopedClient(
                    agentId,
                    """
                            请用中文简洁回答用户问题，可调用工具取数。
                            不要输出 JSON，用 Markdown 表格（最多 15 行）+ 文字摘要。
                            """,
                    maxTokens,
                    temperature);
            String content = client.prompt()
                    .user(request.message())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return ChatResponseVO.of(
                    content == null ? "结构化解析失败，已降级为文本回复。" : content,
                    aiModelConfigService.currentChatModel(),
                    conversationId,
                    null);
        } catch (Exception e) {
            log.error("结构化降级文本也失败: {}", e.getMessage(), e);
            throw new AiException("结构化输出失败: " + cause.getMessage());
        }
    }

    /**
     * 按 Agent 能力装配 Tools，避免全局 chatClient 旁路挂载全量工具。
     */
    private ChatClient buildCapabilityScopedClient(
            long agentId, String systemHint, int maxTokens, Double temperature) {
        List<String> capabilities = agentChatPort.resolveCapabilities(agentId);
        List<ToolCallback> tools = chatClientSupport.resolveTools(capabilities, null, null, agentId);
        var builder = OpenAiChatOptions.builder().model(aiModelConfigService.currentChatModel());
        builder.maxTokens(maxTokens);
        if (temperature != null) {
            builder.temperature(temperature);
        }
        log.info("结构化对话按能力装配 agentId={}, caps={}, tools={}",
                agentId, capabilities, tools.size());
        return chatClientSupport.buildClient(systemHint, tools, builder.build(), true);
    }

    private ChatResponseVO buildOnlineStatusResponse(String conversationId) {
        TerminalOverviewPort.Overview overview =
                terminalOverviewPort.build(TerminalOverviewPort.DEFAULT_DETAIL_LIMIT);
        String body = StringUtils.hasText(overview.markdown()) ? overview.markdown() : overview.summary();
        log.info("在线概览直出 total={}, items={}", overview.total(), overview.items().size());
        return ChatResponseVO.of(
                body == null ? "" : body,
                aiModelConfigService.currentChatModel(),
                conversationId,
                overview.structured());
    }

    private static String structuredSystemHint(ChatStructuredRequestDTO.StructuredChatType type, int maxItems) {
        return switch (type) {
            case COMPARE -> """
                    请根据用户描述调用工具取数，输出多站对比结构化结果。
                    字段：element、summary、items(stationAddress, observeTime, value, remark)。
                    硬性限制：items 最多 %d 条；禁止输出超长列表；完整数量写在 summary。
                    """.formatted(maxItems);
            case TREND -> """
                    请根据用户描述调用工具取历史数据，输出趋势分析结构化结果。
                    字段：stationAddress、element、startTime、endTime、sampleCount、min、max、avg、sum、trend、summary、points。
                    硬性限制：points 最多 %d 个采样点；过多请抽样。
                    """.formatted(maxItems);
            case ALARM -> """
                    请根据用户描述调用告警工具，输出告警摘要结构化结果。
                    字段：level、totalCount、summary、items(stationAddress, element, currentValue, threshold, observeTime, message)。
                    硬性限制：items 最多 %d 条。
                    """.formatted(maxItems);
        };
    }

    private static Object trimStructured(Object structured, int maxItems) {
        if (structured instanceof StationCompareResult r && r.items() != null && r.items().size() > maxItems) {
            return new StationCompareResult(r.element(), r.summary(), r.items().subList(0, maxItems));
        }
        if (structured instanceof TrendAnalysisResult r && r.points() != null && r.points().size() > maxItems) {
            return new TrendAnalysisResult(
                    r.stationAddress(), r.element(), r.startTime(), r.endTime(), r.sampleCount(),
                    r.min(), r.max(), r.avg(), r.sum(), r.trend(), r.summary(),
                    r.points().subList(0, maxItems));
        }
        if (structured instanceof AlarmSummary r && r.items() != null && r.items().size() > maxItems) {
            return new AlarmSummary(r.level(), r.totalCount(), r.summary(), r.items().subList(0, maxItems));
        }
        return structured;
    }

    private static String summaryOf(ChatStructuredRequestDTO.StructuredChatType type, Object structured) {
        return switch (type) {
            case COMPARE -> structured instanceof StationCompareResult r ? r.summary() : "对比完成";
            case TREND -> structured instanceof TrendAnalysisResult r ? r.summary() : "趋势分析完成";
            case ALARM -> structured instanceof AlarmSummary r ? r.summary() : "告警摘要完成";
        };
    }

    private static boolean looksLikeOnlineOverview(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        boolean online = m.contains("在线") || m.contains("offline") || m.contains("online");
        boolean scope = m.contains("全部") || m.contains("所有") || m.contains("概览")
                || m.contains("列表") || m.contains("状态");
        boolean station = m.contains("站") || m.contains("遥测") || m.contains("终端") || m.contains("terminal");
        return online && (scope || station);
    }

    private static boolean isJsonTruncation(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            String name = cur.getClass().getName();
            if (name.contains("UnexpectedEndOfInput")
                    || name.contains("JsonEOF")
                    || name.contains("JsonParse")
                    || (msg != null && (msg.contains("Unexpected end-of-input")
                    || msg.contains("closing quote")
                    || msg.contains("Unexpected end of input")))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    @Override
    public String ping() {
        return chatClient.prompt()
                .user("请只回复 OK")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "ping-" + UUID.randomUUID()))
                .call()
                .content();
    }

    public static Flux<ServerSentEvent<String>> toSseFlux(String content, String conversationId) {
        return ChatSseSupport.toSseFlux(content, conversationId);
    }

    public static Flux<ServerSentEvent<String>> toSseFluxWithTraces(
            String content,
            String conversationId,
            List<AgentTraceEvent> traces) {
        return ChatSseSupport.toSseFluxWithTraces(content, conversationId, traces);
    }

    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();

    private static String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
    }
}
