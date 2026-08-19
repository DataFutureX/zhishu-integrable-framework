package com.datafuturex.assistant.agent.service;

import com.datafuturex.assistant.agent.domain.dto.GraphSaveDTO;
import com.datafuturex.assistant.agent.graph.GraphValidationResult;
import com.datafuturex.assistant.agent.graph.WorkflowGraph;

public interface AgentGraphService {

    WorkflowGraph getGraph(Long agentId);

    WorkflowGraph saveGraph(Long agentId, GraphSaveDTO dto);

    GraphValidationResult validate(WorkflowGraph graph);

    WorkflowGraph compileTemplate(String workflowType, Long agentId);
}
