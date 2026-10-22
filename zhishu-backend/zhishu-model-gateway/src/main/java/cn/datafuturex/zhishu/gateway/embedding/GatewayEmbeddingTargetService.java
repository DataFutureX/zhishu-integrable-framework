package cn.datafuturex.zhishu.gateway.embedding;

import cn.datafuturex.zhishu.ai.modelconfig.api.AiEmbeddingTargetPort;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 向量目标实现：读 {@code model_type=EMBEDDING} 的默认模型行 + 供应商连接。
 * <p>
 * 向量不走网关推理面，因此这里只解析「直连上游」所需的信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayEmbeddingTargetService implements AiEmbeddingTargetPort {

    private static final String MODEL_TYPE_EMBEDDING = "EMBEDDING";

    private final GatewayModelMapper gatewayModelMapper;
    private final ModelUpstreamPort modelUpstreamPort;
    private final GatewayProperties properties;

    /**
     * {@inheritDoc}
     * <p>
     * 与网关总开关对齐：即使内部网关转发回退，向量仍以模型行为配置源。
     */
    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Target> currentEmbedding() {
        GatewayModelEntity entity = gatewayModelMapper.selectOne(
                new LambdaQueryWrapper<GatewayModelEntity>()
                        .eq(GatewayModelEntity::getModelType, MODEL_TYPE_EMBEDDING)
                        .eq(GatewayModelEntity::getIsDefault, true)
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
                        upstream.apiKey()));
    }
}
