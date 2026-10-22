package cn.datafuturex.zhishu.ai.modelconfig.service.impl;

import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelProviderPort;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.ModelProviderMapper;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import cn.datafuturex.zhishu.ai.modelconfig.vo.ModelProviderVO;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商服务实现：只维护连接与凭据（模型与生成参数在模型网关侧维护）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelProviderServiceImpl implements ModelProviderPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    private final ModelProviderMapper modelProviderMapper;
    private final ModelProviderRegistry modelProviderRegistry;
    private final ModelConfigProperties modelConfigProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ModelProviderVO> list() {
        var entities = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProviderEntity>()
                        .orderByDesc(ModelProviderEntity::getIsDefault)
                        .orderByAsc(ModelProviderEntity::getSortOrder)
                        .orderByAsc(ModelProviderEntity::getId));
        return entities.stream().map(this::toVo).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModelProviderVO get(Long id) {
        return toVo(requireEntity(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ModelProviderVO create(ModelProviderCreateDTO dto) {
        Long exists = modelProviderMapper.selectCount(
                new LambdaQueryWrapper<ModelProviderEntity>()
                        .eq(ModelProviderEntity::getProviderKey, dto.providerKey().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException(400, "程序标识已存在: " + dto.providerKey());
        }
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setName(dto.name().trim());
        entity.setProviderKey(dto.providerKey().trim());
        entity.setBaseUrl(trimSlash(dto.baseUrl().trim()));
        entity.setIsDefault(Boolean.TRUE.equals(dto.isDefault()));
        entity.setStatus("ENABLED");
        entity.setSortOrder(dto.sortOrder() == null ? 0 : dto.sortOrder());
        entity.setRemark(dto.remark());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (StringUtils.hasText(dto.apiKey())) {
            String plain = sanitizeApiKey(dto.apiKey());
            entity.setApiKeyEnc(McpCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefault(null);
        }
        modelProviderMapper.insert(entity);
        log.info("创建供应商 id={}, name={}, providerKey={}",
                entity.getId(), entity.getName(), entity.getProviderKey());
        return toVo(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ModelProviderVO update(Long id, ModelProviderUpdateDTO dto) {
        ModelProviderEntity entity = requireEntity(id);
        entity.setName(dto.name().trim());
        entity.setBaseUrl(trimSlash(dto.baseUrl().trim()));
        entity.setRemark(dto.remark());
        if (dto.sortOrder() != null) {
            entity.setSortOrder(dto.sortOrder());
        }
        if (StringUtils.hasText(dto.status())) {
            entity.setStatus(dto.status().trim().toUpperCase());
        }
        if (dto.isDefault() != null) {
            if (Boolean.TRUE.equals(dto.isDefault())) {
                clearDefault(id);
            }
            entity.setIsDefault(dto.isDefault());
        }
        entity.setUpdateTime(LocalDateTime.now());
        if (shouldUpdateApiKey(dto.apiKey())) {
            String plain = sanitizeApiKey(dto.apiKey());
            entity.setApiKeyEnc(McpCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }
        modelProviderMapper.updateById(entity);
        modelProviderRegistry.evict(id);
        log.info("更新供应商 id={}, name={}", entity.getId(), entity.getName());
        return toVo(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            throw new BusinessException(400, "默认供应商不可删除，请先指定其他默认供应商");
        }
        modelProviderMapper.deleteById(id);
        modelProviderRegistry.evict(id);
        log.info("删除供应商 id={}, name={}", id, entity.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ModelProviderVO setDefault(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        clearDefault(id);
        entity.setIsDefault(Boolean.TRUE);
        entity.setUpdateTime(LocalDateTime.now());
        modelProviderMapper.updateById(entity);
        modelProviderRegistry.evict(id);
        return toVo(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String testConnection(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        if (!StringUtils.hasText(resolveApiKey(entity))) {
            return "未配置 API Key，无法测试连通性";
        }
        long start = System.nanoTime();
        try {
            List<String> models = fetchUpstreamModels(entity);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            log.info("供应商连通性测试通过 id={}, 模型数={}", id, models.size());
            return "连通成功，耗时 " + elapsed + " ms，上游可用模型 " + models.size() + " 个";
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            log.warn("供应商连通性测试失败 id={}: {}", id, e.getMessage());
            return "连通失败，耗时 " + elapsed + " ms：" + e.getMessage();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listUpstreamModels(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        try {
            return fetchUpstreamModels(entity);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "拉取上游模型失败: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
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
            throw new BusinessException(404, "未配置任何供应商");
        }
        return toVo(def);
    }

    // ---- 内部方法 ----

    private List<String> fetchUpstreamModels(ModelProviderEntity entity) {
        String apiKey = resolveApiKey(entity);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(400, "未配置 API Key");
        }
        String url = trimSlash(entity.getBaseUrl()) + "/models";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new BusinessException(response.statusCode(),
                        "上游返回 HTTP " + response.statusCode());
            }
            JsonNode root = MAPPER.readTree(response.body());
            List<String> models = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                String id = item.path("id").asText(null);
                if (StringUtils.hasText(id)) {
                    models.add(id);
                }
            }
            for (JsonNode item : root.path("models")) {
                String id = item.path("name").asText(null);
                if (StringUtils.hasText(id)) {
                    models.add(id);
                }
            }
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "拉取上游模型被中断");
        } catch (Exception e) {
            throw new BusinessException(502, e.getMessage() == null ? "上游不可达" : e.getMessage());
        }
    }

    private void clearDefault(Long exceptId) {
        LambdaUpdateWrapper<ModelProviderEntity> wrapper = new LambdaUpdateWrapper<ModelProviderEntity>()
                .eq(ModelProviderEntity::getIsDefault, true)
                .set(ModelProviderEntity::getIsDefault, false)
                .set(ModelProviderEntity::getUpdateTime, LocalDateTime.now());
        if (exceptId != null) {
            wrapper.ne(ModelProviderEntity::getId, exceptId);
        }
        modelProviderMapper.update(null, wrapper);
    }

    private ModelProviderEntity requireEntity(Long id) {
        ModelProviderEntity entity = modelProviderMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "供应商不存在: " + id);
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
        return new ModelProviderVO(
                entity.getId(),
                entity.getName(),
                entity.getProviderKey(),
                entity.getBaseUrl(),
                entity.getApiKeyMasked(),
                StringUtils.hasText(entity.getApiKeyEnc()),
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
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
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
