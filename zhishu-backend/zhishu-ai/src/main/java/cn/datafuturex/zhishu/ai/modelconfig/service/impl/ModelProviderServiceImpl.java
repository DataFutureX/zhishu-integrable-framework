package cn.datafuturex.zhishu.ai.modelconfig.service.impl;

import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelProviderPort;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.ModelProviderMapper;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.RefreshableOpenAiModels;
import cn.datafuturex.zhishu.ai.modelconfig.vo.ModelProviderVO;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型设置服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelProviderServiceImpl implements ModelProviderPort {

    private final ModelProviderMapper modelProviderMapper;
    private final ModelProviderRegistry modelProviderRegistry;
    private final ModelConfigProperties modelConfigProperties;

    @Override
    public List<ModelProviderVO> list() {
        var entities = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProviderEntity>()
                        .orderByDesc(ModelProviderEntity::getIsDefault)
                        .orderByAsc(ModelProviderEntity::getSortOrder)
                        .orderByAsc(ModelProviderEntity::getId));
        return entities.stream().map(this::toVo).toList();
    }

    @Override
    public ModelProviderVO get(Long id) {
        return toVo(requireEntity(id));
    }

    @Override
    @Transactional
    public ModelProviderVO create(ModelProviderCreateDTO dto) {
        // 检查 providerKey 唯一性
        Long exists = modelProviderMapper.selectCount(
                new LambdaQueryWrapper<ModelProviderEntity>()
                        .eq(ModelProviderEntity::getProviderKey, dto.providerKey().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("程序标识已存在: " + dto.providerKey());
        }

        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setName(dto.name().trim());
        entity.setProviderKey(dto.providerKey().trim());
        entity.setBaseUrl(trimSlash(dto.baseUrl().trim()));
        entity.setChatModel(dto.chatModel().trim());
        entity.setEmbeddingModel(StringUtils.hasText(dto.embeddingModel()) ? dto.embeddingModel().trim() : null);
        entity.setTemperature(scale(dto.temperature(), 2));
        entity.setMaxTokens(dto.maxTokens());
        entity.setTopP(scale(dto.topP(), 2));
        entity.setIsDefault(false);
        entity.setStatus("ENABLED");
        entity.setSortOrder(0);
        entity.setRemark(dto.remark());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        // 处理 API Key
        if (StringUtils.hasText(dto.apiKey())) {
            String plain = sanitizeApiKey(dto.apiKey());
            entity.setApiKeyEnc(McpCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }

        modelProviderMapper.insert(entity);
        log.info("创建模型设置 id={}, name={}, providerKey={}",
                entity.getId(), entity.getName(), entity.getProviderKey());
        return toVo(entity);
    }

    @Override
    @Transactional
    public ModelProviderVO update(Long id, ModelProviderUpdateDTO dto) {
        ModelProviderEntity entity = requireEntity(id);
        entity.setName(dto.name().trim());
        entity.setBaseUrl(trimSlash(dto.baseUrl().trim()));
        entity.setChatModel(dto.chatModel().trim());
        entity.setEmbeddingModel(StringUtils.hasText(dto.embeddingModel()) ? dto.embeddingModel().trim() : null);
        entity.setTemperature(scale(dto.temperature(), 2));
        entity.setMaxTokens(dto.maxTokens());
        entity.setTopP(scale(dto.topP(), 2));
        entity.setRemark(dto.remark());
        if (dto.sortOrder() != null) {
            entity.setSortOrder(dto.sortOrder());
        }
        if (StringUtils.hasText(dto.status())) {
            entity.setStatus(dto.status().trim().toUpperCase());
        }
        entity.setUpdateTime(LocalDateTime.now());

        // 处理 API Key
        if (shouldUpdateApiKey(dto.apiKey())) {
            String plain = sanitizeApiKey(dto.apiKey());
            entity.setApiKeyEnc(McpCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }

        modelProviderMapper.updateById(entity);
        modelProviderRegistry.evict(id);
        log.info("更新模型设置 id={}, name={}", entity.getId(), entity.getName());
        return toVo(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            throw new BusinessException("默认模型设置不可删除");
        }
        modelProviderMapper.deleteById(id);
        modelProviderRegistry.evict(id);
        log.info("删除模型设置 id={}, name={}", id, entity.getName());
    }

    @Override
    public String testConnection(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        String apiKey = resolveApiKey(entity);
        if (!StringUtils.hasText(apiKey)) {
            return "未配置 API Key，无法测试连通性";
        }
        try {
            ChatModel testModel = RefreshableOpenAiModels.chatModel(
                    entity.getBaseUrl(), apiKey, entity.getChatModel(),
                    0.1, 10, 0.9);
            var response = testModel.call(
                    new org.springframework.ai.chat.prompt.Prompt("请只回复 OK"));
            String content = response.getResult() != null ? response.getResult().getOutput().getText() : null;
            log.info("连通性测试 provider#{} 回复: {}", id, content);
            return "连通成功，模型回复: " + (content != null ? content.trim() : "(空)");
        } catch (Exception e) {
            log.warn("连通性测试失败 provider#{}: {}", id, e.getMessage());
            return "连通失败: " + e.getMessage();
        }
    }

    @Override
    public ModelProviderVO getDefault() {
        ModelProviderEntity def = modelProviderMapper.selectOne(
                new LambdaQueryWrapper<ModelProviderEntity>()
                        .eq(ModelProviderEntity::getIsDefault, true)
                        .last("LIMIT 1"));
        if (def == null) {
            def = modelProviderMapper.selectOne(
                    new LambdaQueryWrapper<ModelProviderEntity>()
                            .orderByAsc(ModelProviderEntity::getId)
                            .last("LIMIT 1"));
        }
        if (def == null) {
            throw new BusinessException("未配置任何模型设置");
        }
        return toVo(def);
    }

    // ---- 内部方法 ----

    private ModelProviderEntity requireEntity(Long id) {
        ModelProviderEntity entity = modelProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "模型设置不存在: " + id);
        }
        return entity;
    }

    private String resolveApiKey(ModelProviderEntity entity) {
        if (StringUtils.hasText(entity.getApiKeyEnc())) {
            return McpCrypto.decrypt(entity.getApiKeyEnc(), wrapKey());
        }
        return null;
    }

    private String wrapKey() {
        String key = modelConfigProperties.getCryptoKey();
        return StringUtils.hasText(key) ? key : "zhishu-dev-model-key-wrap";
    }

    private ModelProviderVO toVo(ModelProviderEntity entity) {
        boolean configured = StringUtils.hasText(entity.getApiKeyEnc());
        return new ModelProviderVO(
                entity.getId(),
                entity.getName(),
                entity.getProviderKey(),
                entity.getBaseUrl(),
                entity.getApiKeyMasked(),
                configured,
                entity.getChatModel(),
                entity.getEmbeddingModel(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getTopP(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getRemark(),
                entity.getUpdateTime());
    }

    private static boolean shouldUpdateApiKey(String apiKey) {
        String sanitized = sanitizeApiKey(apiKey);
        return StringUtils.hasText(sanitized) && !sanitized.contains("****");
    }

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
