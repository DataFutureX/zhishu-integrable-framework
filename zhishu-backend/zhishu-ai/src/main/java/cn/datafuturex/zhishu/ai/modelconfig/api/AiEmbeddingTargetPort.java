package cn.datafuturex.zhishu.ai.modelconfig.api;

import java.util.Optional;

/**
 * 向量（embedding）目标端口 —— 由模型网关侧实现，供给 {@code runtimeEmbeddingModel}。
 * <p>
 * embedding 不经网关推理面，但配置来源已从供应商行迁移到 {@code model_type=EMBEDDING} 的模型行。
 */
public interface AiEmbeddingTargetPort {

    /**
     * 端口是否启用（与 {@code zhishu.gateway.internal.enabled} 对齐）。
     *
     * @return true=按模型行解析
     */
    boolean enabled();

    /**
     * 当前默认向量模型目标。
     *
     * @return 目标；未配置可用向量模型时返回空
     */
    Optional<Target> currentEmbedding();

    /**
     * 向量目标快照。
     *
     * @param modelId       网关模型 id
     * @param upstreamModel 供应商真实向量模型名
     * @param baseUrl       供应商 Base URL
     * @param apiKey        解密后的 Key
     */
    record Target(String modelId, String upstreamModel, String baseUrl, String apiKey) {
    }
}
