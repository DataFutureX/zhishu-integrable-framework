package cn.datafuturex.zhishu.gateway.catalog;

import cn.datafuturex.zhishu.ai.modelconfig.api.ModelUpstreamPort;

/**
 * 目录视图：目录行 + 已解密的上游端点。
 *
 * @param entity   目录实体
 * @param upstream 上游端点
 */
public record GatewayModelView(
        GatewayModelEntity entity,
        ModelUpstreamPort.UpstreamEndpoint upstream
) {

    /**
     * 对外模型 id。
     *
     * @return model_id
     */
    public String modelId() {
        return entity.getModelId();
    }

    /**
     * 上游供应商 ID。
     *
     * @return provider id
     */
    public Long providerId() {
        return entity.getProviderId();
    }

    /**
     * 供应商真实模型名。
     *
     * @return upstream model
     */
    public String upstreamModel() {
        return entity.getUpstreamModel();
    }

    /**
     * 是否网关默认模型。
     *
     * @return true=默认
     */
    public boolean isDefault() {
        return Boolean.TRUE.equals(entity.getIsDefault());
    }
}
