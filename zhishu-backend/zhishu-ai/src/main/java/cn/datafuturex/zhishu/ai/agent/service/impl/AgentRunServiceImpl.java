package cn.datafuturex.zhishu.ai.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentRunEntity;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentRunMapper;
import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.agent.service.AgentRunService;
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
        return start(agentId, conversationId, null, null, null, null, "CHAT");
    }

    @Override
    public AiAgentRunEntity start(Long agentId, String conversationId,
                                  String userMessage, String modelName,
                                  String workflowType, String userId, String runType) {
        AiAgentRunEntity run = new AiAgentRunEntity();
        run.setAgentId(agentId);
        run.setConversationId(conversationId);
        run.setStatus("RUNNING");
        run.setUserMessage(userMessage);
        run.setModelName(modelName);
        run.setWorkflowType(workflowType);
        run.setUserId(userId);
        run.setRunType(runType != null ? runType : "CHAT");
        run.setCreateTime(LocalDateTime.now());
        run.setUpdateTime(LocalDateTime.now());
        aiAgentRunMapper.insert(run);
        return run;
    }

    @Override
    public void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces) {
        complete(runId, status, currentNode, traces, null, null, null, null, null, null);
    }

    @Override
    public void complete(Long runId, String status, String currentNode, List<AgentTraceEvent> traces,
                         Long durationMs, String responseSummary, String errorMessage,
                         Long ttftMs, Long tpotMs, Integer tokenCount) {
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
        // 监控字段
        run.setDurationMs(durationMs);
        run.setResponseSummary(responseSummary);
        run.setErrorMessage(errorMessage);
        if (ttftMs != null && ttftMs >= 0) {
            run.setTtftMs(ttftMs);
        }
        if (tpotMs != null && tpotMs >= 0) {
            run.setTpotMs(tpotMs);
        }
        if (tokenCount != null && tokenCount > 0) {
            run.setTokenCount(tokenCount);
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
