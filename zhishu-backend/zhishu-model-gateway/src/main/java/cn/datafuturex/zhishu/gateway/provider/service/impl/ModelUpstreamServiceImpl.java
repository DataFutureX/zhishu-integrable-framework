package cn.datafuturex.zhishu.gateway.provider.service.impl;

import cn.datafuturex.zhishu.common.crypto.CredentialCrypto;
import cn.datafuturex.zhishu.common.crypto.CredentialProperties;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.gateway.provider.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.gateway.provider.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 上游模型端点实现（模型网关侧）：读 {@code ai_model_provider} 并解密 API Key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelUpstreamServiceImpl implements ModelUpstreamPort {

    private final ModelProviderMapper modelProviderMapper;
    private final CredentialProperties credentialProperties;
    private final GatewayProperties gatewayProperties;

    @Value("${spring.ai.openai.api-key:}")
    private String envApiKey;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UpstreamEndpoint> resolve(Long providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        ModelProviderEntity entity = modelProviderMapper.selectById(providerId);
        if (entity == null) {
            log.warn("模型网关：上游 provider#{} 不存在", providerId);
            return Optional.empty();
        }
        if (!"ENABLED".equalsIgnoreCase(entity.getStatus())) {
            log.debug("模型网关：上游 provider#{} 已禁用，模型不可用", providerId);
            return Optional.empty();
        }
        String baseUrl = StringUtils.hasText(entity.getBaseUrl())
                ? trimTrailingSlash(entity.getBaseUrl().trim())
                : gatewayProperties.getUpstream().getDefaultBaseUrl();
        String apiKey = resolveApiKey(entity);
        return Optional.of(new UpstreamEndpoint(
                entity.getId(),
                entity.getProviderKey(),
                entity.getName(),
                baseUrl,
                apiKey == null ? "" : apiKey));
    }

    /**
     * 解析供应商明文 Key：入库加密值优先，其次环境变量。
     *
     * @param entity 供应商实体
     * @return 明文 Key；未配置时 null
     */
    private String resolveApiKey(ModelProviderEntity entity) {
        if (StringUtils.hasText(entity.getApiKeyEnc())) {
            String wrapKey = credentialProperties.getCryptoKey();
            return CredentialCrypto.decrypt(entity.getApiKeyEnc(),
                    StringUtils.hasText(wrapKey) ? wrapKey : "zhishu-dev-model-key-wrap");
        }
        return StringUtils.hasText(envApiKey) ? envApiKey.trim() : null;
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
