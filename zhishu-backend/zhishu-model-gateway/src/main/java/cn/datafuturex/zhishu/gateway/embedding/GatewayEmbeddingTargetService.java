package cn.datafuturex.zhishu.gateway.embedding;

import cn.datafuturex.zhishu.ai.modelconfig.api.AiEmbeddingTargetPort;
import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.RefreshableOpenAiModels;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.provider.runtime.ModelRuntimeCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 向量模型端口实现：读 {@code model_type=EMBEDDING} 的默认模型行 + 供应商连接。
 * <p>
 * 向量不走网关推理面，这里只解析「直连上游」所需信息；构建结果按指纹缓存在
 * {@link ModelRuntimeCache}，模型行变更后统一失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayEmbeddingTargetService implements AiEmbeddingTargetPort {

    private static final String MODEL_TYPE_EMBEDDING = "EMBEDDING";

    private static final String STATUS_ENABLED = "ENABLED";

    private final GatewayModelMapper gatewayModelMapper;

    private final ModelUpstreamPort modelUpstreamPort;

    private final ModelRuntimeCache runtimeCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public EmbeddingModel defaultEmbeddingModel() {
        return runtimeCache.lazyEmbeddingModel(this::requireEmbeddingModel);
    }

    /**
     * 解析并缓存默认向量模型。
     *
     * @return EmbeddingModel
     */
    private EmbeddingModel requireEmbeddingModel() {
        return findEmbeddingTarget()
                .map(this::cachedEmbeddingModel)
                .orElseThrow(() -> new AiException(
                        "未配置默认向量模型：请在「模型网关 · 模型设置」中添加 EMBEDDING 模型并设为默认"));
    }

    /**
     * 当前默认的可用向量目标。
     *
     * @return 目标；无启用默认行或供应商不可用时为 empty
     */
    private Optional<EmbeddingTarget> findEmbeddingTarget() {
        GatewayModelEntity entity = gatewayModelMapper.selectOne(
                new LambdaQueryWrapper<GatewayModelEntity>()
                        .eq(GatewayModelEntity::getModelType, MODEL_TYPE_EMBEDDING)
                        .eq(GatewayModelEntity::getIsDefault, true)
                        .eq(GatewayModelEntity::getStatus, STATUS_ENABLED)
                        .last("LIMIT 1"));
        if (entity == null) {
            return Optional.empty();
        }
        return modelUpstreamPort.resolve(entity.getProviderId())
                .filter(ModelUpstreamPort.UpstreamEndpoint::usable)
                .map(upstream -> new EmbeddingTarget(
                        entity.getModelId(),
                        entity.getUpstreamModel(),
                        upstream.baseUrl(),
                        upstream.apiKey()));
    }

    private EmbeddingModel cachedEmbeddingModel(EmbeddingTarget target) {
        String fingerprint = String.join("|",
                String.valueOf(target.modelId()),
                String.valueOf(target.baseUrl()),
                String.valueOf(target.upstreamModel()),
                String.valueOf(target.apiKey() == null ? 0 : target.apiKey().hashCode()));
        return runtimeCache.embeddingModel(fingerprint, () -> RefreshableOpenAiModels.embeddingModel(
                target.baseUrl(), target.apiKey(), target.upstreamModel()));
    }

    /**
     * 向量目标快照（模块内部使用，不对外暴露）。
     *
     * @param modelId       网关模型 id
     * @param upstreamModel 供应商真实向量模型名
     * @param baseUrl       供应商 Base URL
     * @param apiKey        解密后的 Key
     */
    private record EmbeddingTarget(String modelId, String upstreamModel, String baseUrl, String apiKey) {
    }
}
