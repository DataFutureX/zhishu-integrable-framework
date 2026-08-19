package com.datafuturex.assistant.agent.runtime;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;

import java.util.List;
import java.util.function.Consumer;

public record AgentRuntimeRequest(
        AiAgentEntity agent,
        String message,
        String conversationId,
        Boolean enableRag,
        boolean enableMemory,
        Integer maxTokens,
        Double temperature,
        List<Long> documentIds,
        /** 流式场景：节点 / Tool 进度回调（可为 null） */
        Consumer<AgentTraceEvent> onProgress,
        /** 无 Tools 的 LLM 调用：token 级正文回调（可为 null） */
        Consumer<String> onToken
) {
    public AgentRuntimeRequest(
            AiAgentEntity agent,
            String message,
            String conversationId,
            Boolean enableRag,
            boolean enableMemory,
            Integer maxTokens,
            Double temperature,
            List<Long> documentIds) {
        this(agent, message, conversationId, enableRag, enableMemory, maxTokens, temperature,
                documentIds, null, null);
    }

    public AgentRuntimeRequest(
            AiAgentEntity agent,
            String message,
            String conversationId,
            Boolean enableRag,
            boolean enableMemory,
            Integer maxTokens,
            Double temperature,
            List<Long> documentIds,
            Consumer<AgentTraceEvent> onProgress) {
        this(agent, message, conversationId, enableRag, enableMemory, maxTokens, temperature,
                documentIds, onProgress, null);
    }

    public void emitProgress(AgentTraceEvent event) {
        if (onProgress != null && event != null) {
            onProgress.accept(event);
        }
    }

    public void emitToken(String chunk) {
        if (onToken != null && chunk != null && !chunk.isEmpty()) {
            onToken.accept(chunk);
        }
    }

    public boolean streamingTokens() {
        return onToken != null;
    }
}
