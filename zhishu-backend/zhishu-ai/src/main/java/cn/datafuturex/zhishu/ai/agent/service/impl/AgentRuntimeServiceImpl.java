package cn.datafuturex.zhishu.ai.agent.service.impl;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.agent.runtime.AgentEngine;
import cn.datafuturex.zhishu.ai.agent.runtime.AgentEngineSelector;
import cn.datafuturex.zhishu.ai.agent.runtime.AgentRuntimeRequest;
import cn.datafuturex.zhishu.ai.agent.runtime.AgentRuntimeResult;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.agent.service.AgentDefinitionService;
import cn.datafuturex.zhishu.ai.agent.service.AgentRunService;
import cn.datafuturex.zhishu.ai.agent.service.AgentRuntimeService;
import cn.datafuturex.zhishu.ai.agent.support.AgentJsonUtils;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRuntimeServiceImpl implements AgentRuntimeService {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentEngineSelector agentEngineSelector;
    private final AgentRunService agentRunService;

    @Override
    public ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature) {
        return run(agent, message, conversationId, enableRag, maxTokens, temperature, null);
    }

    @Override
    public ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature,
                              Consumer<AgentTraceEvent> onProgress) {
        return run(agent, message, conversationId, enableRag, maxTokens, temperature, onProgress, null);
    }

    @Override
    public ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                              Boolean enableRag, Integer maxTokens, Double temperature,
                              Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken) {
        String cid = StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
        AiAgentEntity effective = applyRuntimeOverrides(agent, maxTokens, temperature);
        boolean enableMemory = Boolean.TRUE.equals(effective.getEnableMemory());
        List<Long> documentIds = AgentJsonUtils.parseDocumentIds(effective.getDocumentIds());

        AgentEngine engine = agentEngineSelector.select();
        AiAgentRunEntity run = agentRunService.start(agent.getId(), cid);
        AgentRuntimeRequest request = new AgentRuntimeRequest(
                effective, message, cid, enableRag, enableMemory, maxTokens, temperature,
                documentIds, onProgress, onToken);
        try {
            AgentRuntimeResult result = engine.execute(request);
            String lastNode = lastNodeName(result.traces());
            agentRunService.complete(run.getId(), "SUCCESS", lastNode, result.traces());
            return ChatResponseVO.of(
                    result.content(),
                    result.model(),
                    result.conversationId(),
                    null,
                    agent.getId(),
                    result.traces());
        } catch (AiException e) {
            agentRunService.complete(run.getId(), "FAILED", null, List.of(
                    AgentTraceEvent.of("NODE_END", "error", e.getMessage(), null)));
            throw e;
        } catch (Exception e) {
            agentRunService.complete(run.getId(), "FAILED", null, List.of(
                    AgentTraceEvent.of("NODE_END", "error", e.getMessage(), null)));
            log.error("Agent 执行失败 agentId={}, code={}: {}", agent.getId(), agent.getCode(), e.getMessage(), e);
            throw new AiException("智能体执行失败: " + e.getMessage());
        }
    }

    @Override
    public ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                                String trialConversationId, Boolean enableMemory) {
        return trial(agentId, message, enableRag, trialConversationId, enableMemory, null, null);
    }

    @Override
    public ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                                String trialConversationId, Boolean enableMemory,
                                Consumer<AgentTraceEvent> onProgress) {
        return trial(agentId, message, enableRag, trialConversationId, enableMemory, onProgress, null);
    }

    @Override
    public ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                                String trialConversationId, Boolean enableMemory,
                                Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken) {
        AiAgentEntity agent = agentDefinitionService.requireEntity(agentId);
        AiAgentEntity trialAgent = copyAgent(agent);
        // 试运行默认关闭记忆，避免污染正式会话；显式 true 时可用独立 conversationId 多轮
        trialAgent.setEnableMemory(Boolean.TRUE.equals(enableMemory));
        String cid = StringUtils.hasText(trialConversationId)
                ? trialConversationId.trim()
                : UUID.randomUUID().toString();
        return run(trialAgent, message, cid, enableRag, null, null, onProgress, onToken);
    }

    private static String lastNodeName(List<AgentTraceEvent> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        for (int i = traces.size() - 1; i >= 0; i--) {
            AgentTraceEvent e = traces.get(i);
            if (e != null && StringUtils.hasText(e.name())) {
                return e.name();
            }
        }
        return null;
    }

    private AiAgentEntity applyRuntimeOverrides(AiAgentEntity source, Integer maxTokens, Double temperature) {
        if (maxTokens == null && temperature == null) {
            return source;
        }
        AiAgentEntity copy = copyAgent(source);
        if (maxTokens != null) {
            copy.setMaxTokens(maxTokens);
        }
        if (temperature != null) {
            copy.setTemperature(java.math.BigDecimal.valueOf(temperature));
        }
        return copy;
    }

    private static AiAgentEntity copyAgent(AiAgentEntity source) {
        AiAgentEntity copy = new AiAgentEntity();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setSystemPrompt(source.getSystemPrompt());
        copy.setModel(source.getModel());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setCapabilities(source.getCapabilities());
        copy.setWorkflowType(source.getWorkflowType());
        copy.setWorkflowConfig(source.getWorkflowConfig());
        copy.setDocumentIds(source.getDocumentIds());
        copy.setEnableMemory(source.getEnableMemory());
        copy.setModelProviderId(source.getModelProviderId());
        copy.setStatus(source.getStatus());
        return copy;
    }
}
