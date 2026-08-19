package com.datafuturex.assistant.agent.api;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.agent.service.AgentDefinitionService;
import com.datafuturex.assistant.agent.service.AgentRuntimeService;
import com.datafuturex.assistant.agent.support.AgentJsonUtils;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.shared.vo.ChatResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentChatPortImpl implements AgentChatPort {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentRuntimeService agentRuntimeService;

    @Override
    public long resolveAgentId(Long agentId) {
        AiAgentEntity agent = agentId != null
                ? agentDefinitionService.requireEnabled(agentId)
                : agentDefinitionService.requireDefault();
        return agent.getId();
    }

    @Override
    public List<String> resolveCapabilities(long agentId) {
        AiAgentEntity agent = agentDefinitionService.requireEnabled(agentId);
        return AgentJsonUtils.parseCapabilities(agent.getCapabilities());
    }

    @Override
    public ChatResponseVO run(long agentId, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature) {
        AiAgentEntity agent = agentDefinitionService.requireEnabled(agentId);
        return agentRuntimeService.run(agent, message, conversationId, enableRag, maxTokens, temperature);
    }

    @Override
    public ChatResponseVO run(long agentId, String message, String conversationId,
                              Boolean enableRag, Boolean enableMemory,
                              Integer maxTokens, Double temperature) {
        // 校验启用态后走 trial 路径，强制 enableMemory（简报等批处理默认 false）
        agentDefinitionService.requireEnabled(agentId);
        return agentRuntimeService.trial(agentId, message, enableRag, conversationId, enableMemory);
    }

    @Override
    public ChatResponseVO run(long agentId, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature,
                              Consumer<AgentTraceEvent> onProgress) {
        return run(agentId, message, conversationId, enableRag, maxTokens, temperature, onProgress, null);
    }

    @Override
    public ChatResponseVO run(long agentId, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature,
                              Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken) {
        AiAgentEntity agent = agentDefinitionService.requireEnabled(agentId);
        return agentRuntimeService.run(
                agent, message, conversationId, enableRag, maxTokens, temperature, onProgress, onToken);
    }
}
