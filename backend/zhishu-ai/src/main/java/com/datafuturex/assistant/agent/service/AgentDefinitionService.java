package com.datafuturex.assistant.agent.service;

import com.datafuturex.assistant.agent.domain.dto.AgentCreateDTO;
import com.datafuturex.assistant.agent.domain.dto.AgentUpdateDTO;
import com.datafuturex.assistant.agent.domain.entity.AiAgentEntity;
import com.datafuturex.assistant.agent.domain.vo.AgentVO;
import com.datafuturex.assistant.agent.domain.vo.CapabilityVO;
import com.datafuturex.assistant.agent.domain.vo.WorkflowTemplateVO;

import java.util.List;

public interface AgentDefinitionService {

    List<AgentVO> list(String status);

    AgentVO get(Long id);

    AiAgentEntity requireEntity(Long id);

    AiAgentEntity requireEnabled(Long id);

    AiAgentEntity requireDefault();

    AgentVO create(AgentCreateDTO dto);

    AgentVO update(Long id, AgentUpdateDTO dto);

    void delete(Long id);

    void setDefault(Long id);

    List<CapabilityVO> listCapabilities();

    List<WorkflowTemplateVO> listWorkflowTemplates();
}
