package cn.datafuturex.zhishu.ai.modelconfig.service.impl;

import cn.datafuturex.zhishu.ai.modelconfig.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.ModelProviderMapper;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 上游模型端点实现：读 {@code ai_model_provider} 并解密 API Key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelUpstreamService implements ModelUpstreamPort {

    private final ModelProviderMapper modelProviderMapper;
    private final ModelProviderRegistry modelProviderRegistry;
    private final ModelConfigProperties modelConfigProperties;

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
                : modelConfigProperties.getDefaultBaseUrl();
        String apiKey = modelProviderRegistry.resolveApiKey(entity);
        return Optional.of(new UpstreamEndpoint(
                entity.getId(),
                entity.getProviderKey(),
                entity.getName(),
                baseUrl,
                apiKey == null ? "" : apiKey));
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
