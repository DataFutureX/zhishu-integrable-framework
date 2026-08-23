package cn.datafuturex.zhishu.ai.agent.service;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;

import java.util.List;

public interface AgentRunService {

    AiAgentRunEntity start(Long agentId, String conversationId);

    void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces);

    List<AiAgentRunEntity> recent(Long agentId, int limit);
}
