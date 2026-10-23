package cn.datafuturex.zhishu.ai.agent.service.impl;

import cn.datafuturex.zhishu.ai.agent.domain.entity.AiAgentEntity;
import cn.datafuturex.zhishu.ai.agent.mapper.AiAgentMapper;
import cn.datafuturex.zhishu.ai.gatewayclient.api.AgentModelUsagePort;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AgentModelUsagePort} 实现：按 {@code ai_agent.model} 统计绑定关系。
 * <p>
 * 放在 Agent 模块内，使表结构与查询细节不外泄给模型网关。
 */
@Component
@RequiredArgsConstructor
public class AgentModelUsagePortImpl implements AgentModelUsagePort {

    private static final int NAME_LIMIT = 20;

    private final AiAgentMapper aiAgentMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public int countAgentsBoundTo(String gatewayModelId) {
        Long count = aiAgentMapper.selectCount(new LambdaQueryWrapper<AiAgentEntity>()
                .eq(AiAgentEntity::getModel, gatewayModelId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listAgentNamesBoundTo(String gatewayModelId) {
        List<AiAgentEntity> rows = aiAgentMapper.selectList(new LambdaQueryWrapper<AiAgentEntity>()
                .select(AiAgentEntity::getName)
                .eq(AiAgentEntity::getModel, gatewayModelId)
                .orderByAsc(AiAgentEntity::getId)
                .last("LIMIT " + NAME_LIMIT));
        return rows.stream().map(AiAgentEntity::getName).toList();
    }
}
