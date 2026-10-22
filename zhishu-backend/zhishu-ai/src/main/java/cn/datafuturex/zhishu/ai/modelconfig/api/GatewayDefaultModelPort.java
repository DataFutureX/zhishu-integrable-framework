package cn.datafuturex.zhishu.ai.modelconfig.api;

import java.util.Optional;

/**
 * 网关默认模型端口 —— 由模型网关内部客户端实现，供模型配置读取「统一默认模型」。
 * <p>
 * 放在 modelconfig 的 {@code api} 包内，避免 modelconfig 反向依赖 gatewayclient。
 */
public interface GatewayDefaultModelPort {

    /**
     * 模型网关是否作为统一模型来源。
     *
     * @return true=启用
     */
    boolean enabled();

    /**
     * 网关默认模型 id。
     *
     * @return 默认模型；未配置或网关不可用返回空
     */
    Optional<String> defaultModelId();
}
