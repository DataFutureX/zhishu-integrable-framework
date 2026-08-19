package com.datafuturex.assistant.modelconfig.service.impl;

import com.datafuturex.assistant.platform.ai.AiConfig;
import com.datafuturex.assistant.modelconfig.domain.AiModelConfigEntity;
import com.datafuturex.assistant.modelconfig.dto.AiModelConfigUpdateDTO;
import com.datafuturex.assistant.modelconfig.vo.AiModelConfigVO;
import com.datafuturex.assistant.modelconfig.mapper.AiModelConfigMapper;
import com.datafuturex.assistant.modelconfig.api.ModelConfigPort;
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
        entity.setBaseUrl(StringUtils.hasText(envBaseUrl) ? envBaseUrl : entity.getBaseUrl());
        entity.setApiKeyMasked(maskApiKey(envApiKey));
        entity.setUpdateTime(LocalDateTime.now());
        aiModelConfigMapper.updateById(entity);
        log.info("已更新 AI 模型配置: chatModel={}, temperature={}, maxTokens={}",
                entity.getChatModel(), entity.getTemperature(), entity.getMaxTokens());
        // 清缓存后返回最新
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
        entity.setBaseUrl(envBaseUrl);
        entity.setApiKeyMasked(maskApiKey(envApiKey));
        entity.setRemark("系统自动初始化");
        entity.setUpdateTime(LocalDateTime.now());
        aiModelConfigMapper.insert(entity);
        log.info("已初始化 ai_model_config 默认行");
        return entity;
    }

    private AiModelConfigVO toVo(AiModelConfigEntity entity) {
        return new AiModelConfigVO(
                entity.getChatModel(),
                entity.getEmbeddingModel(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getTopP(),
                entity.getEnableRagDefault(),
                entity.getMemoryWindowSize(),
                StringUtils.hasText(entity.getBaseUrl()) ? entity.getBaseUrl() : envBaseUrl,
                StringUtils.hasText(entity.getApiKeyMasked()) ? entity.getApiKeyMasked() : maskApiKey(envApiKey),
                entity.getRemark(),
                CHAT_MODEL_OPTIONS,
                EMBEDDING_MODEL_OPTIONS,
                entity.getUpdateTime());
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
