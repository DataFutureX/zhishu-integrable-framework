package cn.datafuturex.zhishu.ai.modelconfig.service.impl;

import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.modelconfig.domain.AiModelConfigEntity;
import cn.datafuturex.zhishu.ai.modelconfig.dto.AiModelConfigUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.AiModelConfigMapper;
import cn.datafuturex.zhishu.ai.modelconfig.vo.AiModelConfigVO;
import cn.datafuturex.zhishu.ai.platform.ai.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelConfigServiceImpl implements ModelConfigPort {

    public static final String CACHE_NAME = "ai-model-config";
    public static final Long SINGLETON_ID = 1L;

    private static final List<String> CHAT_MODEL_OPTIONS = List.of(
            "qwen-plus",
            "qwen-turbo",
            "qwen-max",
            "qwen-long",
            "qwen2.5-72b-instruct",
            "deepseek-v3",
            "deepseek-r1");

    private static final List<String> EMBEDDING_MODEL_OPTIONS = List.of(
            "qwen3.7-text-embedding",
            "text-embedding-v3",
            "text-embedding-v2");

    private final AiModelConfigMapper aiModelConfigMapper;
    private final CacheManager cacheManager;
    private final ModelConfigProperties modelConfigProperties;

    @Value("${spring.ai.openai.base-url:}")
    private String envBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String envApiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String envChatModel;

    @Value("${spring.ai.openai.embedding.options.model:qwen3.7-text-embedding}")
    private String envEmbeddingModel;

    @Override
    @Cacheable(value = CACHE_NAME, key = "'vo'")
    public AiModelConfigVO getConfig() {
        return toVo(getOrInitEntity());
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public AiModelConfigVO updateConfig(AiModelConfigUpdateDTO dto) {
        AiModelConfigEntity entity = getOrInitEntity();
        entity.setChatModel(dto.chatModel().trim());
        entity.setEmbeddingModel(dto.embeddingModel().trim());
        entity.setTemperature(scale(dto.temperature(), 2));
        entity.setMaxTokens(dto.maxTokens());
        entity.setTopP(scale(dto.topP(), 2));
        entity.setEnableRagDefault(dto.enableRagDefault());
        entity.setMemoryWindowSize(dto.memoryWindowSize());
        entity.setRemark(dto.remark());
        if (dto.baseUrl() != null) {
            String url = dto.baseUrl().trim();
            entity.setBaseUrl(StringUtils.hasText(url) ? trimSlash(url) : null);
        }
        if (shouldUpdateApiKey(dto.apiKey())) {
            String plain = sanitizeApiKey(dto.apiKey());
            entity.setApiKeyEnc(McpCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }
        entity.setUpdateTime(LocalDateTime.now());
        aiModelConfigMapper.updateById(entity);
        log.info("已更新 AI 模型配置: chatModel={}, embeddingModel={}, apiKeyConfigured={}",
                entity.getChatModel(), entity.getEmbeddingModel(), StringUtils.hasText(entity.getApiKeyEnc()));
        if (cacheManager.getCache(CACHE_NAME) != null) {
            cacheManager.getCache(CACHE_NAME).clear();
        }
        return toVo(entity);
    }

    @Override
    public String currentChatModel() {
        AiModelConfigEntity entity = cachedEntity();
        return StringUtils.hasText(entity.getChatModel()) ? entity.getChatModel() : AiConfig.DEFAULT_MODEL;
    }

    @Override
    public Double currentTemperature() {
        AiModelConfigEntity entity = cachedEntity();
        return entity.getTemperature() == null ? 0.7 : entity.getTemperature().doubleValue();
    }

    @Override
    public Integer currentMaxTokens() {
        AiModelConfigEntity entity = cachedEntity();
        return entity.getMaxTokens() == null ? 2000 : entity.getMaxTokens();
    }

    @Override
    public boolean currentEnableRagDefault() {
        AiModelConfigEntity entity = cachedEntity();
        return Boolean.TRUE.equals(entity.getEnableRagDefault());
    }

    @Override
    public String currentEmbeddingModel() {
        AiModelConfigEntity entity = cachedEntity();
        return StringUtils.hasText(entity.getEmbeddingModel())
                ? entity.getEmbeddingModel()
                : "qwen3.7-text-embedding";
    }

    @Override
    public String currentBaseUrl() {
        AiModelConfigEntity entity = cachedEntity();
        if (StringUtils.hasText(entity.getBaseUrl())) {
            return trimSlash(entity.getBaseUrl());
        }
        if (StringUtils.hasText(envBaseUrl)) {
            return trimSlash(envBaseUrl);
        }
        return trimSlash(modelConfigProperties.getDefaultBaseUrl());
    }

    @Override
    public String currentApiKey() {
        AiModelConfigEntity entity = cachedEntity();
        if (StringUtils.hasText(entity.getApiKeyEnc())) {
            return sanitizeApiKey(McpCrypto.decrypt(entity.getApiKeyEnc(), wrapKey()));
        }
        return sanitizeApiKey(envApiKey);
    }

    @Override
    public boolean hasApiKey() {
        return StringUtils.hasText(currentApiKey());
    }

    private AiModelConfigEntity cachedEntity() {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            AiModelConfigEntity cached = cache.get("entity", AiModelConfigEntity.class);
            if (cached != null) {
                return cached;
            }
        }
        AiModelConfigEntity entity = getOrInitEntity();
        if (cache != null) {
            cache.put("entity", entity);
        }
        return entity;
    }

    private AiModelConfigEntity getOrInitEntity() {
        AiModelConfigEntity entity = aiModelConfigMapper.selectById(SINGLETON_ID);
        if (entity != null) {
            return entity;
        }
        entity = new AiModelConfigEntity();
        entity.setId(SINGLETON_ID);
        entity.setChatModel(StringUtils.hasText(envChatModel) ? envChatModel : AiConfig.DEFAULT_MODEL);
        entity.setEmbeddingModel(StringUtils.hasText(envEmbeddingModel) ? envEmbeddingModel : "qwen3.7-text-embedding");
        entity.setTemperature(BigDecimal.valueOf(0.70).setScale(2, RoundingMode.HALF_UP));
        entity.setMaxTokens(2000);
        entity.setTopP(BigDecimal.valueOf(0.90).setScale(2, RoundingMode.HALF_UP));
        entity.setEnableRagDefault(false);
        entity.setMemoryWindowSize(AiConfig.MEMORY_WINDOW_SIZE);
        entity.setBaseUrl(StringUtils.hasText(envBaseUrl) ? trimSlash(envBaseUrl) : modelConfigProperties.getDefaultBaseUrl());
        if (StringUtils.hasText(envApiKey)) {
            entity.setApiKeyEnc(McpCrypto.encrypt(envApiKey.trim(), wrapKey()));
            entity.setApiKeyMasked(maskApiKey(envApiKey));
        }
        entity.setRemark("系统自动初始化");
        entity.setUpdateTime(LocalDateTime.now());
        aiModelConfigMapper.insert(entity);
        log.info("已初始化 ai_model_config 默认行");
        return entity;
    }

    private AiModelConfigVO toVo(AiModelConfigEntity entity) {
        boolean configured = StringUtils.hasText(entity.getApiKeyEnc()) || StringUtils.hasText(envApiKey);
        String masked = StringUtils.hasText(entity.getApiKeyMasked())
                ? entity.getApiKeyMasked()
                : maskApiKey(envApiKey);
        String baseUrl = StringUtils.hasText(entity.getBaseUrl()) ? entity.getBaseUrl() : envBaseUrl;
        return new AiModelConfigVO(
                entity.getChatModel(),
                entity.getEmbeddingModel(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getTopP(),
                entity.getEnableRagDefault(),
                entity.getMemoryWindowSize(),
                StringUtils.hasText(baseUrl) ? baseUrl : modelConfigProperties.getDefaultBaseUrl(),
                masked,
                configured,
                entity.getRemark(),
                CHAT_MODEL_OPTIONS,
                EMBEDDING_MODEL_OPTIONS,
                entity.getUpdateTime());
    }

    private String wrapKey() {
        String key = modelConfigProperties.getCryptoKey();
        return StringUtils.hasText(key) ? key : "zhishu-dev-model-key-wrap";
    }

    private static boolean shouldUpdateApiKey(String apiKey) {
        String sanitized = sanitizeApiKey(apiKey);
        return StringUtils.hasText(sanitized) && !sanitized.contains("****");
    }

    /** 去掉引号、Bearer 前缀与空白，避免粘贴导致 401 */
    static String sanitizeApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.regionMatches(true, 0, "Bearer ", 0, 7)) {
            key = key.substring(7).trim();
        }
        return key.replaceAll("\\s+", "");
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String v = url.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static BigDecimal scale(BigDecimal v, int scale) {
        return v == null ? null : v.setScale(scale, RoundingMode.HALF_UP);
    }

    private static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
