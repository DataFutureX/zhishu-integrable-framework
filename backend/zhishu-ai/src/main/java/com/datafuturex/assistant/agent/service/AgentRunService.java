package com.datafuturex.assistant.agent.service;

import com.datafuturex.assistant.agent.domain.entity.AiAgentRunEntity;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;

import java.util.List;

public interface AgentRunService {

    AiAgentRunEntity start(Long agentId, String conversationId);

    void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces);

    List<AiAgentRunEntity> recent(Long agentId, int limit);
}
