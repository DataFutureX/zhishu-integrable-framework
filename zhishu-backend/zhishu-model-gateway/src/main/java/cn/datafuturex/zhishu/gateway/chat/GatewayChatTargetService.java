package cn.datafuturex.zhishu.gateway.chat;

import cn.datafuturex.zhishu.ai.modelconfig.api.AiChatTargetPort;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 对话目标实现：供回退模式（直连上游）按模型行构建 ChatModel。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayChatTargetService implements AiChatTargetPort {

    private static final String MODEL_TYPE_CHAT = "CHAT";

    private final GatewayModelMapper gatewayModelMapper;
    private final ModelUpstreamPort modelUpstreamPort;
    private final GatewayProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Target> resolveByModelId(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        GatewayModelEntity entity = gatewayModelMapper.selectOne(
                new LambdaQueryWrapper<GatewayModelEntity>()
                        .eq(GatewayModelEntity::getModelId, modelId.trim())
                        .eq(GatewayModelEntity::getModelType, MODEL_TYPE_CHAT)
                        .eq(GatewayModelEntity::getStatus, "ENABLED")
                        .last("LIMIT 1"));
        if (entity == null) {
            return Optional.empty();
        }
        return modelUpstreamPort.resolve(entity.getProviderId())
                .filter(ModelUpstreamPort.UpstreamEndpoint::usable)
                .map(upstream -> new Target(
                        entity.getModelId(),
                        entity.getUpstreamModel(),
                        upstream.baseUrl(),
                        upstream.apiKey(),
                        toDouble(entity.getTemperature()),
                        toDouble(entity.getTopP()),
                        entity.getMaxTokens()));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
