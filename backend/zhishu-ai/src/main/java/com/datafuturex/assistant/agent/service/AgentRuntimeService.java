package com.datafuturex.assistant.agent.service;

import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.shared.vo.ChatResponseVO;

import java.util.function.Consumer;

public interface AgentRuntimeService {

    /**
     * 使用指定智能体执行一轮对话（同步）。
     */
    ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature);

    /**
     * 同步执行，并在各节点完成时回调进度（供流式 SSE 先行推送意图等）。
     */
    ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature,
                       Consumer<AgentTraceEvent> onProgress);

    /**
     * 同步执行；onProgress 推送节点/Tool 进度，onToken 在无 Tools 的 LLM 阶段推送正文 token。
     */
    ChatResponseVO run(AiAgentEntity agent, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature,
                       Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken);

    /**
     * 试运行。trialConversationId 为空则新建 UUID；enableMemory=false 避免污染正式会话记忆。
     */
    ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                         String trialConversationId, Boolean enableMemory);

    /**
     * 试运行（带进度回调，供 SSE 先行推送）。
     */
    ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                         String trialConversationId, Boolean enableMemory,
                         Consumer<AgentTraceEvent> onProgress);

    /**
     * 试运行（进度 + token 流）。
     */
    ChatResponseVO trial(Long agentId, String message, Boolean enableRag,
                         String trialConversationId, Boolean enableMemory,
                         Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken);
}
