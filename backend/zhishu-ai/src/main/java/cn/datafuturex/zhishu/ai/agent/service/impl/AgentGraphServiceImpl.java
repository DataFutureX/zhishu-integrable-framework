package cn.datafuturex.zhishu.ai.agent.service.impl;

import cn.datafuturex.zhishu.ai.agent.domain.dto.GraphSaveDTO;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.enums.WorkflowType;
import cn.datafuturex.zhishu.ai.agent.graph.GraphValidationResult;
import cn.datafuturex.zhishu.ai.agent.graph.WorkflowGraph;
import cn.datafuturex.zhishu.ai.agent.graph.WorkflowGraphCompiler;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentMapper;
import cn.datafuturex.zhishu.ai.agent.service.AgentDefinitionService;
import cn.datafuturex.zhishu.ai.agent.service.AgentGraphService;
import cn.datafuturex.zhishu.ai.agent.support.AgentJsonUtils;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentGraphServiceImpl implements AgentGraphService {

    private final AgentDefinitionService agentDefinitionService;
    private final AiAgentMapper aiAgentMapper;
    private final WorkflowGraphCompiler workflowGraphCompiler;

    @Override
    public WorkflowGraph getGraph(Long agentId) {
        AiAgentEntity entity = agentDefinitionService.requireEntity(agentId);
        List<String> caps = AgentJsonUtils.parseCapabilities(entity.getCapabilities());
        return workflowGraphCompiler.resolveGraph(
                entity.getWorkflowType(),
                entity.getWorkflowConfig(),
                caps,
                entity.getSystemPrompt());
    }

    @Override
    @Transactional
    public WorkflowGraph saveGraph(Long agentId, GraphSaveDTO dto) {
        AiAgentEntity entity = agentDefinitionService.requireEntity(agentId);
        WorkflowGraph graph = new WorkflowGraph();
        graph.setVersion(dto.version() == null ? 1 : dto.version());
        graph.setNodes(dto.nodes());
        graph.setEdges(dto.edges());
        GraphValidationResult vr = workflowGraphCompiler.validate(graph);
        if (!vr.valid()) {
            throw new BusinessException("Graph 校验失败: " + String.join("; ", vr.errors()));
        }
        entity.setWorkflowType(WorkflowType.GRAPH.name());
        entity.setWorkflowConfig(workflowGraphCompiler.toJson(graph));
        entity.setUpdateTime(LocalDateTime.now());
        aiAgentMapper.updateById(entity);
        return graph;
    }

    @Override
    public GraphValidationResult validate(WorkflowGraph graph) {
        return workflowGraphCompiler.validate(graph);
    }

    @Override
    public WorkflowGraph compileTemplate(String workflowType, Long agentId) {
        WorkflowType type = WorkflowType.require(workflowType);
        if (type == WorkflowType.GRAPH) {
            throw new BusinessException("GRAPH 不是可编译模板");
        }
        String systemPrompt = "你是助手，请用中文回答。";
        List<String> caps = List.of("RAG", "MCP_TOOLS");
        if (agentId != null) {
            AiAgentEntity entity = agentDefinitionService.requireEntity(agentId);
            systemPrompt = entity.getSystemPrompt();
            caps = AgentJsonUtils.parseCapabilities(entity.getCapabilities());
        }
        return workflowGraphCompiler.compileTemplate(type, caps, systemPrompt);
    }
}
