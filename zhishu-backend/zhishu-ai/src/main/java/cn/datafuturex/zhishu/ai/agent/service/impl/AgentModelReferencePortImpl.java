package cn.datafuturex.zhishu.ai.agent.service.impl;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentMapper;
import cn.datafuturex.zhishu.gateway.port.ModelReferencePort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ModelReferencePort} 的 Agent 侧实现：按 {@code ai_agent.model} 统计绑定关系。
 * <p>
 * 端口由模型网关声明、由本模块实现，使 {@code ai_agent} 的表结构与查询细节不外泄给网关。
 */
@Component
@RequiredArgsConstructor
public class AgentModelReferencePortImpl implements ModelReferencePort {

    private static final int NAME_LIMIT = 20;

    private final AiAgentMapper aiAgentMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public int countReferences(String gatewayModelId) {
        Long count = aiAgentMapper.selectCount(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getModel, gatewayModelId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listReferenceNames(String gatewayModelId) {
        List<AiAgentEntity> rows = aiAgentMapper.selectList(new LambdaQueryWrapper<AiAgentEntity>()
                .select(AiAgentEntity::getName)
                .eq(AiAgentEntity::getModel, gatewayModelId)
                .orderByAsc(AiAgentEntity::getId)
                .last("LIMIT " + NAME_LIMIT));
        return rows.stream().map(AiAgentEntity::getName).toList();
    }
}
