package cn.datafuturex.zhishu.ai.agent.runtime;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.registry.ToolCapabilityRegistry;
import cn.datafuturex.zhishu.ai.agent.support.AgentTimeContext;
import cn.datafuturex.zhishu.ai.gatewayclient.GatewayChatModelRegistry;
import cn.datafuturex.zhishu.ai.shared.mcp.ExternalToolPort;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ChatClient 装配与调用工具（供模板引擎与 Graph 执行器复用）。
 * <p>
 * 模型统一由 {@link GatewayChatModelRegistry} 提供：默认经模型网关转发，
 * {@code zhishu.gateway.internal.enabled=false} 时回退直连上游。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatClientSupport {

    private final GatewayChatModelRegistry gatewayChatModelRegistry;
    private final ChatMemory chatMemory;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final ExternalToolPort externalToolPort;

    /** 当前执行步骤标签（供日志输出），由引擎层设置 */
    private static final ThreadLocal<String> STEP_LABEL = new ThreadLocal<>();

    // ---- Token 计时采集（TTFT / TPOT / Token 总数） ----

    /** 单次 LLM 调用的 Token 计时数据 */
    public record TokenTiming(
            long requestStartMs,
            long firstTokenMs,
            long lastTokenMs,
            int tokenCount
    ) {
        public long ttft() {
            return firstTokenMs > 0 ? firstTokenMs - requestStartMs : -1;
        }

        public long tpot() {
            if (tokenCount <= 1) return -1;
            return (lastTokenMs - firstTokenMs) / (tokenCount - 1);
        }
    }

    /** 收集当前线程所有 LLM 调用的 Token 计时 */
    private static final ThreadLocal<List<TokenTiming>> TOKEN_TIMINGS =
            ThreadLocal.withInitial(ArrayList::new);

    /**
     * 设置当前线程的执行步骤标签（会附加到 [LLM请求] 日志）。
     *
     * @param label 步骤名，如“意图澄清”“工具执行”；null 清除
     */
    public static void setStepLabel(String label) {
        STEP_LABEL.set(label);
    }

    /**
     * 读取并清空当前线程的 Token 计时列表。
     *
     * @return 当前线程所有 LLM 调用的 TokenTiming 列表
     */
    public static List<TokenTiming> drainTokenTimings() {
        List<TokenTiming> list = new ArrayList<>(TOKEN_TIMINGS.get());
        TOKEN_TIMINGS.remove();
        return list;
    }

    /** 兆底清理 ThreadLocal，防止内存泄漏。 */
    public static void clearTokenTimings() {
        TOKEN_TIMINGS.remove();
    }

    /**
     * 按 Agent 配置构建 ChatOptions（模型统一取网关 model_id）。
     *
     * @param entity 智能体实体
     * @return OpenAI ChatOptions
     */
    public OpenAiChatOptions buildOptions(AiAgentEntity entity) {
        String model = gatewayChatModelRegistry.resolveModelId(entity.getModel());
        Integer maxTokens = entity.getMaxTokens();
        Double temperature = toDouble(entity.getTemperature());

        var builder = OpenAiChatOptions.builder().model(model);
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.build();
    }

    /**
     * 构建 ChatClient（使用默认模型设置）。
     *
     * @param systemPrompt 系统提示词
     * @param tools        工具列表
     * @param options      ChatOptions
     * @param enableMemory 是否启用记忆
     * @return ChatClient
     */
    public ChatClient buildClient(
            String systemPrompt,
            List<ToolCallback> tools,
            OpenAiChatOptions options,
            boolean enableMemory) {
        return buildClient(null, systemPrompt, tools, options, enableMemory);
    }

    /**
     * 构建 ChatClient（按网关 model_id 路由）。
     *
     * @param gatewayModelId 网关模型 id，空=网关默认模型
     * @param systemPrompt   系统提示词
     * @param tools          工具列表
     * @param options        ChatOptions
     * @param enableMemory   是否启用记忆
     * @return ChatClient
     */
    public ChatClient buildClient(
            String gatewayModelId,
            String systemPrompt,
            List<ToolCallback> tools,
            OpenAiChatOptions options,
            boolean enableMemory) {
        ChatModel chatModel = gatewayChatModelRegistry.resolve(gatewayModelId);
        ChatModel loggingModel = new LoggingChatModel(chatModel, gatewayModelId);
        String effectivePrompt = AgentTimeContext.appendBlock(systemPrompt);
        var builder = ChatClient.builder(loggingModel)
                .defaultSystem(effectivePrompt)
                .defaultOptions(toOptionsBuilder(options));
        if (tools != null && !tools.isEmpty()) {
            builder.defaultToolCallbacks(tools);
        }
        if (enableMemory) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }
        return builder.build();
    }

    public List<ToolCallback> resolveTools(List<String> capabilities, List<AgentTraceEvent> traces) {
        return resolveTools(capabilities, traces, null, null);
    }

    public List<ToolCallback> resolveTools(
            List<String> capabilities,
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress) {
        return resolveTools(capabilities, traces, onProgress, null);
    }

    public List<ToolCallback> resolveTools(
            List<String> capabilities,
            List<AgentTraceEvent> traces,
            Consumer<AgentTraceEvent> onProgress,
            Long agentId) {
        List<ToolCallback> raw = new ArrayList<>(toolCapabilityRegistry.resolveToolCallbacks(
                capabilities == null ? List.of() : capabilities));
        if (agentId != null) {
            raw.addAll(externalToolPort.resolveForAgent(agentId));
        }
        if (traces == null) {
            return raw;
        }
        List<ToolCallback> wrapped = new ArrayList<>(raw.size());
        for (ToolCallback cb : raw) {
            wrapped.add(new TracingToolCallback(cb, traces, onProgress));
        }
        return wrapped;
    }

    public String call(ChatClient client, String userText, String conversationId, boolean withMemory) {
        return callOrStream(client, userText, conversationId, withMemory, null);
    }

    /**
     * 同步 call；若提供 onToken 且客户端未挂 Tools，则走 token 真流式（阻塞收集全文）。
     * 含 Tools 时同步 call + Tool progress。
     */
    public String callOrStream(
            ChatClient client,
            String userText,
            String conversationId,
            boolean withMemory,
            Consumer<String> onToken) {
        var spec = client.prompt().user(userText);
        if (withMemory && StringUtils.hasText(conversationId)) {
            spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }
        if (onToken != null) {
            StringBuilder sb = new StringBuilder();
            spec.stream().content().toStream().forEach(chunk -> {
                if (chunk != null && !chunk.isEmpty()) {
                    sb.append(chunk);
                    onToken.accept(chunk);
                }
            });
            return sb.toString();
        }
        String content = spec.call().content();
        return content == null ? "" : content;
    }

    /**
     * 解析本 Agent 实际使用的模型名（日志/监控用）。
     *
     * @param entity 智能体实体
     * @return 模型名
     */
    public String resolveModelName(AiAgentEntity entity) {
        return gatewayChatModelRegistry.describeModel(entity.getModel());
    }

    private static OpenAiChatOptions.Builder toOptionsBuilder(OpenAiChatOptions options) {
        var builder = OpenAiChatOptions.builder().model(options.getModel());
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        return builder;
    }

    /**
     * ChatModel 日志代理：拦截实际发给模型的 Prompt，打印真实参数。
     */
    private final class LoggingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final String gatewayModelId;

        LoggingChatModel(ChatModel delegate, String gatewayModelId) {
            this.delegate = delegate;
            this.gatewayModelId = gatewayModelId;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            logPrompt(prompt);
            long start = System.currentTimeMillis();
            ChatResponse response = delegate.call(prompt);
            long now = System.currentTimeMillis();
            try {
                int tokens = 1;
                if (response != null && response.getMetadata() != null
                        && response.getMetadata().getUsage() != null) {
                    long completionTokens = response.getMetadata().getUsage().getCompletionTokens();
                    if (completionTokens > 0) {
                        tokens = (int) completionTokens;
                    }
                }
                TOKEN_TIMINGS.get().add(new TokenTiming(start, start, now, tokens));
            } catch (Exception e) {
                log.warn("[监控采集] 同步调用 Token 计时异常，已忽略", e);
            }
            return response;
        }

        @Override
        public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
            logPrompt(prompt);
            long start = System.currentTimeMillis();
            AtomicLong firstToken = new AtomicLong(0);
            AtomicLong lastToken = new AtomicLong(0);
            AtomicInteger count = new AtomicInteger(0);
            AtomicLong completionTokens = new AtomicLong(0);

            return delegate.stream(prompt)
                    .doOnNext(resp -> {
                        try {
                            long now = System.currentTimeMillis();
                            lastToken.set(now);
                            if (count.incrementAndGet() == 1) {
                                firstToken.set(now);
                            }
                            // 尝试从响应元数据获取实际 token 数（部分 provider 在最后一个 chunk 返回）
                            if (resp != null && resp.getMetadata() != null
                                    && resp.getMetadata().getUsage() != null) {
                                long ct = resp.getMetadata().getUsage().getCompletionTokens();
                                if (ct > 0) {
                                    completionTokens.set(ct);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[监控采集] Token 计时异常，已忽略", e);
                        }
                    })
                    .doFinally(signal -> {
                        try {
                            int tokens = completionTokens.get() > 0
                                    ? (int) completionTokens.get()
                                    : count.get();
                            TOKEN_TIMINGS.get().add(new TokenTiming(
                                    start, firstToken.get(), lastToken.get(), tokens));
                        } catch (Exception e) {
                            log.warn("[监控采集] TokenTiming 写入异常，已忽略", e);
                        }
                    });
        }

        @Override
        public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
            return delegate.getOptions();
        }

        private void logPrompt(Prompt prompt) {
            String endpoint = gatewayChatModelRegistry.completionsEndpoint();
            String modelLabel = gatewayChatModelRegistry.describeModel(gatewayModelId);
            String step = STEP_LABEL.get();
            String stepPrefix = step != null ? "[" + step + "] " : "";

            var messages = prompt.getInstructions();
            int msgCount = messages != null ? messages.size() : 0;
            String msgSummary = messages == null ? "[]" : messages.stream()
                    .map(m -> "[" + m.getMessageType() + "] " + truncate(m.getText(), 100))
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));

            var opts = prompt.getOptions();
            if (opts instanceof OpenAiChatOptions o) {
                log.warn("[LLM请求] {}url={}, model={}, messages={}条 {}, temperature={}, maxTokens={}, topP={}",
                        stepPrefix,
                        endpoint,
                        modelLabel,
                        msgCount, msgSummary,
                        o.getTemperature(), o.getMaxTokens(), o.getTopP());
            } else {
                log.warn("[LLM请求] {}url={}, model={}, messages={}条 {}",
                        stepPrefix,
                        endpoint, modelLabel, msgCount, msgSummary);
            }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        text = text.replace("\n", "\\n");
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
