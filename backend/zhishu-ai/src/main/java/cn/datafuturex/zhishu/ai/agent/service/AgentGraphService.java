package cn.datafuturex.zhishu.ai.agent.service;

import cn.datafuturex.zhishu.ai.agent.domain.dto.GraphSaveDTO;
import cn.datafuturex.zhishu.ai.agent.graph.GraphValidationResult;
import cn.datafuturex.zhishu.ai.agent.graph.WorkflowGraph;

public interface AgentGraphService {

    WorkflowGraph getGraph(Long agentId);

    WorkflowGraph saveGraph(Long agentId, GraphSaveDTO dto);

    GraphValidationResult validate(WorkflowGraph graph);

    WorkflowGraph compileTemplate(String workflowType, Long agentId);
}
