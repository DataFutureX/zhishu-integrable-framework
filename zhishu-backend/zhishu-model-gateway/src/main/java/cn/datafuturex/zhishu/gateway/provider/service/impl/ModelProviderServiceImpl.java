package cn.datafuturex.zhishu.gateway.provider.service.impl;

import cn.datafuturex.zhishu.ai.shared.crypto.CredentialCrypto;
import cn.datafuturex.zhishu.gateway.provider.api.ModelProviderPort;
import cn.datafuturex.zhishu.ai.gatewayclient.config.ModelConfigProperties;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderTestDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.gateway.provider.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.gateway.provider.mapper.ModelProviderMapper;
import cn.datafuturex.zhishu.gateway.provider.runtime.ModelRuntimeCache;
import cn.datafuturex.zhishu.gateway.provider.vo.ModelProviderVO;
import cn.datafuturex.zhishu.gateway.provider.vo.UpstreamModelInfo;
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

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 供应商服务实现：只维护连接与凭据（模型与生成参数在模型网关侧维护）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelProviderServiceImpl implements ModelProviderPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int HTTP_TIMEOUT_SECONDS = 15;
    private static final String OPENAI_MODELS_PATH = "/models";
    private static final String OLLAMA_TAGS_PATH = "/api/tags";

    private final ModelProviderMapper modelProviderMapper;
    private final ModelRuntimeCache modelRuntimeCache;
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
            entity.setApiKeyEnc(CredentialCrypto.encrypt(plain, wrapKey()));
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
            entity.setApiKeyEnc(CredentialCrypto.encrypt(plain, wrapKey()));
            entity.setApiKeyMasked(maskApiKey(plain));
        }
        modelProviderMapper.updateById(entity);
        modelRuntimeCache.evictAll();
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
        modelRuntimeCache.evictAll();
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
        modelRuntimeCache.evictAll();
        return toVo(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String testConnection(Long id) {
        ModelProviderEntity entity = requireEntity(id);
        return probeConnection(
                entity.getProviderKey(),
                entity.getName(),
                entity.getBaseUrl(),
                resolveApiKey(entity),
                "id=" + id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String testConnection(ModelProviderTestDTO dto) {
        String apiKey = StringUtils.hasText(dto.apiKey())
                ? sanitizeApiKey(dto.apiKey())
                : resolveStoredApiKey(dto.providerId());
        String providerKey = StringUtils.hasText(dto.providerKey()) ? dto.providerKey().trim() : "openai-compatible";
        return probeConnection(providerKey, dto.providerKey(), dto.baseUrl(), apiKey,
                "draft providerId=" + dto.providerId());
    }

    /**
     * 统一的探测入口：记录耗时并把异常转成可读文案（不抛异常，便于弹窗直接展示）。
     *
     * @param providerKey 程序标识（参与 Ollama 判定）
     * @param name        展示名称（参与 Ollama 判定，可为空）
     * @param baseUrl     Base URL
     * @param apiKey      明文 Key，可为空（为空则匿名探测）
     * @param scene       日志标识
     * @return 测试结果说明
     */
    private String probeConnection(String providerKey, String name, String baseUrl, String apiKey, String scene) {
        long start = System.nanoTime();
        try {
            List<UpstreamModelInfo> models = fetchUpstreamModels(providerKey, name, baseUrl, apiKey);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            log.info("供应商连通性测试通过 {}, 模型数={}", scene, models.size());
            return "连通成功，耗时 " + elapsed + " ms，上游可用模型 " + models.size() + " 个";
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            log.warn("供应商连通性测试失败 {}: {}", scene, e.getMessage());
            return "连通失败，耗时 " + elapsed + " ms：" + e.getMessage();
        }
    }

    /** 编辑态测试时可用已保存的 Key：留空即表示不修改密钥 */
    private String resolveStoredApiKey(Long providerId) {
        if (providerId == null) {
            return null;
        }
        ModelProviderEntity stored = modelProviderMapper.selectById(providerId);
        return stored == null ? null : resolveApiKey(stored);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpstreamModelInfo> listUpstreamModels(Long id) {
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

    /**
     * 允许无 Key 上游（vLLM / LM Studio / Ollama 等自建服务常不设密钥）：
     * 服务端不做「必须配 Key」的预判，缺 Key 导致的鉴权失败由上游的 401 反馈真实原因。
     */
    private List<UpstreamModelInfo> fetchUpstreamModels(ModelProviderEntity entity) {
        return fetchUpstreamModels(entity.getProviderKey(), entity.getName(), entity.getBaseUrl(),
                resolveApiKey(entity));
    }

    /**
     * 同上，但连接信息来自调用方（保存前试跑时尚未落库）。
     *
     * @param providerKey 程序标识（参与 Ollama 判定，可为空）
     * @param name        展示名称（参与 Ollama 判定，可为空）
     * @param baseUrl     Base URL
     * @param apiKey      明文 Key，可为空（为空则匿名请求，由上游 401 决定是否需要鉴权）
     */
    private List<UpstreamModelInfo> fetchUpstreamModels(String providerKey, String name, String baseUrl,
                                                        String apiKey) {
        boolean ollama = isOllamaProvider(providerKey, name, baseUrl);
        String url = upstreamModelsUrl(baseUrl, ollama);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET();
        if (StringUtils.hasText(apiKey)) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest request = builder.build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                boolean keyless = !ollama && !StringUtils.hasText(apiKey);
                String hint = (response.statusCode() == 401 || response.statusCode() == 403) && keyless
                        ? "（该供应商未配置 API Key，若上游需要鉴权请先填写）" : "";
                throw new BusinessException(response.statusCode(),
                        "上游返回 HTTP " + response.statusCode() + hint);
            }
            JsonNode root = MAPPER.readTree(response.body());
            List<UpstreamModelInfo> models = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            // OpenAI 形状：{"data":[{...}]}；Ollama 形状：{"models":[{"name":...}]}
            collectUpstreamModels(root.path("data"), "id", models, seen);
            collectUpstreamModels(root.path("models"), "name", models, seen);
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "拉取上游模型被中断");
        } catch (HttpTimeoutException e) {
            log.warn("拉取上游模型超时 url={}: {}", url, e.toString());
            throw new BusinessException(502, "连接上游超时：" + url);
        } catch (ConnectException e) {
            log.warn("拉取上游模型连接失败 url={}: {}", url, e.toString());
            throw new BusinessException(502, "无法连接上游：" + url
                    + (ollama ? "（请确认 Ollama 已启动，且从后端所在主机可访问该地址）" : ""));
        } catch (Exception e) {
            log.warn("拉取上游模型失败 url={}: {}", url, e.toString());
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new BusinessException(502, "拉取上游模型失败：" + url + "，" + detail);
        }
    }

    /**
     * 上游模型列表地址：Ollama 的原生接口在根路径 {@code /api/tags}，其余供应商走 OpenAI 兼容的 {@code /models}。
     * Ollama 的 Base URL 常按 OpenAI 兼容方式填成 {@code .../v1}，此处先剥掉版本后缀再拼原生路径。
     */
    static String upstreamModelsUrl(String baseUrl, boolean ollama) {
        String normalized = trimSlash(baseUrl == null ? "" : baseUrl.trim());
        if (!ollama) {
            return normalized + OPENAI_MODELS_PATH;
        }
        if (normalized.length() >= 3
                && normalized.regionMatches(true, normalized.length() - 3, "/v1", 0, 3)) {
            normalized = trimSlash(normalized.substring(0, normalized.length() - 3));
        }
        return normalized + OLLAMA_TAGS_PATH;
    }

    static boolean isOllamaProvider(String providerKey, String name, String baseUrl) {
        return containsOllama(providerKey)
                || containsOllama(name)
                || containsOllama(baseUrl);
    }

    private static boolean containsOllama(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("ollama");
    }

    /**
     * 解析一个数组里的模型条目（容错：字段缺失则为 null）。
     *
     * @param array    模型数组节点
     * @param idField  模型名所在字段（id 或 name）
     * @param out      收集结果
     * @param seen     已收录的模型名（去重用）
     */
    private static void collectUpstreamModels(JsonNode array, String idField,
                                              List<UpstreamModelInfo> out, java.util.Set<String> seen) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            String id = item.path(idField).asText(null);
            if (!StringUtils.hasText(id) || !seen.add(id.trim())) {
                continue;
            }
            JsonNode effort = item.path("effort");
            String systemPromptUpdate = item.path("system_prompt_update").asText(null);
            if (!StringUtils.hasText(systemPromptUpdate)) {
                systemPromptUpdate = item.path("api_capabilities")
                        .path("anthropic_messages").path("system_prompt_update").asText(null);
            }
            String name = item.path("name").asText(null);
            out.add(new UpstreamModelInfo(
                    id.trim(),
                    StringUtils.hasText(name) ? name.trim() : id.trim(),
                    asInteger(item.path("context_window")),
                    asInteger(item.path("max_output_tokens")),
                    asTextList(item.path("input_modalities")),
                    asTextList(effort.path("supported_levels")),
                    effort.path("default_level").asText(null),
                    systemPromptUpdate));
        }
    }

    private static Integer asInteger(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static List<String> asTextList(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            String text = element.asText(null);
            if (StringUtils.hasText(text)) {
                values.add(text.trim());
            }
        }
        return values.isEmpty() ? null : values;
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
            return CredentialCrypto.decrypt(entity.getApiKeyEnc(), wrapKey());
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
