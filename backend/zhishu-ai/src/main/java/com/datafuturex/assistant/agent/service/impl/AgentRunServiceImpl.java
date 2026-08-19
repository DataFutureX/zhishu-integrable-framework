package com.datafuturex.assistant.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafuturex.assistant.agent.domain.entity.AiAgentRunEntity;
import com.datafuturex.assistant.agent.mapper.AiAgentRunMapper;
import com.datafuturex.assistant.shared.trace.AgentTraceEvent;
import com.datafuturex.assistant.agent.service.AgentRunService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunServiceImpl implements AgentRunService {

    private final AiAgentRunMapper aiAgentRunMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiAgentRunEntity start(Long agentId, String conversationId) {
        AiAgentRunEntity run = new AiAgentRunEntity();
        run.setAgentId(agentId);
        run.setConversationId(conversationId);
        run.setStatus("RUNNING");
        run.setCreateTime(LocalDateTime.now());
        run.setUpdateTime(LocalDateTime.now());
        aiAgentRunMapper.insert(run);
        return run;
    }

    @Override
    public void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces) {
        if (runId == null) {
            return;
        }
        AiAgentRunEntity run = aiAgentRunMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setStatus(status);
        run.setCurrentNode(currentNode);
        try {
            run.setStateJson(objectMapper.writeValueAsString(traces == null ? List.of() : traces));
        } catch (Exception e) {
            log.warn("序列化 run traces 失败: {}", e.getMessage());
            run.setStateJson("[]");
        }
        run.setUpdateTime(LocalDateTime.now());
        aiAgentRunMapper.updateById(run);
    }

    @Override
    public List<AiAgentRunEntity> recent(Long agentId, int limit) {
        int lim = Math.max(1, Math.min(limit, 50));
        return aiAgentRunMapper.selectList(new LambdaQueryWrapper<AiAgentRunEntity>()
                .eq(AiAgentRunEntity::getAgentId, agentId)
                .orderByDesc(AiAgentRunEntity::getId)
                .last("LIMIT " + lim));
    }
}
