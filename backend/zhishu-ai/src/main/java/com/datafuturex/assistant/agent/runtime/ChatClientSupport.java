package com.datafuturex.assistant.agent.runtime;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.agent.registry.ToolCapabilityRegistry;
import com.datafuturex.assistant.agent.support.AgentTimeContext;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
import com.datafuturex.assistant.shared.mcp.ExternalToolPort;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ChatClient 装配与调用工具（供模板引擎与 Graph 执行器复用）。
 */
@Component
@RequiredArgsConstructor
public class ChatClientSupport {

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final ModelConfigPort aiModelConfigService;
    private final ExternalToolPort externalToolPort;

    public OpenAiChatOptions buildOptions(AiAgentEntity entity) {
        String model = StringUtils.hasText(entity.getModel())
                ? entity.getModel().trim()
                : aiModelConfigService.currentChatModel();
        Integer maxTokens = entity.getMaxTokens() != null
                ? entity.getMaxTokens()
                : aiModelConfigService.currentMaxTokens();
        Double temperature = toDouble(entity.getTemperature());
        if (temperature == null) {
            temperature = aiModelConfigService.currentTemperature();
        }
        var builder = OpenAiChatOptions.builder().model(model);
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (temperature != null) {
            builder.temperature(temperature);
        }
        return builder.build();
    }

    public ChatClient buildClient(
            String systemPrompt,
            List<ToolCallback> tools,
            OpenAiChatOptions options,
            boolean enableMemory) {
        // 每次调用注入服务器当前时间与日报/月报/年报默认周期，避免模型臆造「今日」
        String effectivePrompt = AgentTimeContext.appendBlock(systemPrompt);
        var builder = ChatClient.builder(chatModel)
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
     * 含 Tools 时请传 onToken=null，避免 Spring AI 2.0 流式+Tool 聚合缺陷。
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

    public String resolveModelName(AiAgentEntity entity) {
        return StringUtils.hasText(entity.getModel())
                ? entity.getModel()
                : aiModelConfigService.currentChatModel();
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

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
