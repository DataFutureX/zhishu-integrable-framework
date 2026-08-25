package cn.datafuturex.zhishu.ai.agent.service;

import cn.datafuturex.zhishu.ai.agent.domain.dto.AgentCreateDTO;
import cn.datafuturex.zhishu.ai.agent.domain.dto.AgentUpdateDTO;
import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.CapabilityVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.WorkflowTemplateVO;

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
