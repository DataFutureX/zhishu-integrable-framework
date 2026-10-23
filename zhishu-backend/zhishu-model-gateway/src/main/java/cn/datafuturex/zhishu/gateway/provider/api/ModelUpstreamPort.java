package cn.datafuturex.zhishu.gateway.provider.api;

import java.util.Optional;

/**
 * 上游模型端点端口 —— 供模型网关取「供应商连接 + 解密后的 Key」
 * <p>
 * 网关只负责协议转发，不持有自己的供应商配置；此处是唯一读取入口。
 */
public interface ModelUpstreamPort {

    /**
     * 解析指定模型设置的上游端点。
     * <p>
     * 供应商不存在或已禁用时返回 {@link Optional#empty()}；API Key 未配置时
     * {@link UpstreamEndpoint#apiKey()} 为空串（由调用方决定如何提示）。
     *
     * @param providerId 模型设置 ID
     * @return 上游端点
     */
    Optional<UpstreamEndpoint> resolve(Long providerId);

    /**
     * 上游连接快照（v2：只含连接信息，模型与生成参数在模型行上）。
     *
     * @param providerId   供应商 ID
     * @param providerKey  程序标识（dashscope / deepseek / openai …）
     * @param providerName 展示名称
     * @param baseUrl      OpenAI 兼容 Base URL（已去尾斜杠）
     * @param apiKey       解密后的明文 Key，可能为空串
     */
    record UpstreamEndpoint(
            Long providerId,
            String providerKey,
            String providerName,
            String baseUrl,
            String apiKey
    ) {
        /**
         * 是否具备调用上游的必要条件。
         *
         * @return Key 与 Base URL 都已配置返回 true
         */
        public boolean usable() {
            return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
        }
    }
}
