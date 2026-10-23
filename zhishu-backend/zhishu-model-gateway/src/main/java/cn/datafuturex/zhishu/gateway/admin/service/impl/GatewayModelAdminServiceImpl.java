package cn.datafuturex.zhishu.gateway.admin.service.impl;

import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort;
import cn.datafuturex.zhishu.gateway.provider.api.ModelUpstreamPort.UpstreamEndpoint;
import cn.datafuturex.zhishu.gateway.provider.runtime.ModelRuntimeCache;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelBatchImportDTO;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.mapper.AgentModelRefMapper;
import cn.datafuturex.zhishu.gateway.admin.service.GatewayModelAdminService;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayModelAdminVO;
import cn.datafuturex.zhishu.gateway.catalog.GatewayCatalogService;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelEntity;
import cn.datafuturex.zhishu.gateway.catalog.GatewayModelMapper;
import cn.datafuturex.zhishu.gateway.upstream.UpstreamHttpClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 模型设置管理实现：供应商下的 CHAT / EMBEDDING 模型，生成参数归模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayModelAdminServiceImpl implements GatewayModelAdminService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_EMBEDDING = "EMBEDDING";

    private final GatewayModelMapper gatewayModelMapper;
    private final ModelUpstreamPort modelUpstreamPort;
    private final GatewayCatalogService catalogService;
    private final AgentModelRefMapper agentModelRefMapper;
    private final UpstreamHttpClient upstreamHttpClient;
    private final ModelRuntimeCache modelRuntimeCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GatewayModelAdminVO> list(String modelType, Long providerId) {
        LambdaQueryWrapper<GatewayModelEntity> wrapper = new LambdaQueryWrapper<GatewayModelEntity>()
                .orderByAsc(GatewayModelEntity::getModelType)
                .orderByAsc(GatewayModelEntity::getSortOrder)
                .orderByAsc(GatewayModelEntity::getId);
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(GatewayModelEntity::getModelType, modelType.trim().toUpperCase());
        }
        if (providerId != null) {
            wrapper.eq(GatewayModelEntity::getProviderId, providerId);
        }
        List<GatewayModelEntity> rows = gatewayModelMapper.selectList(wrapper);
        List<GatewayModelAdminVO> result = new ArrayList<>(rows.size());
        for (GatewayModelEntity row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GatewayModelAdminVO> listAvailable() {
        List<GatewayModelAdminVO> all = list(TYPE_CHAT, null);
        List<GatewayModelAdminVO> result = new ArrayList<>();
        for (GatewayModelAdminVO vo : all) {
            if (STATUS_ENABLED.equalsIgnoreCase(vo.status()) && Boolean.TRUE.equals(vo.upstreamUsable())) {
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayModelAdminVO get(Long id) {
        return toVO(requireEntity(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayModelAdminVO create(GatewayModelUpsertDTO dto) {
        validateProvider(dto.providerId());
        String modelId = dto.modelId().trim();
        GatewayModelEntity existing = gatewayModelMapper.selectOne(
                new LambdaQueryWrapper<GatewayModelEntity>()
                        .eq(GatewayModelEntity::getModelId, modelId)
                        .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(400, "模型 id 已存在: " + modelId);
        }
        validateJson(dto.clientRequirements(), "client_requirements");
        validateJson(dto.extraParams(), "extra_params");

        GatewayModelEntity entity = new GatewayModelEntity();
        apply(entity, dto, modelId);
        requireUpstreamUnique(entity.getProviderId(), entity.getModelType(), entity.getUpstreamModel(), null);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(dto.isDefault())) {
            clearDefault(entity.getModelType(), null);
        }
        gatewayModelMapper.insert(entity);
        afterChange(entity);
        log.info("[网关] 新建模型 model_id={}, provider#{}, type={}",
                modelId, dto.providerId(), entity.getModelType());
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayModelAdminVO update(Long id, GatewayModelUpsertDTO dto) {
        GatewayModelEntity entity = requireEntity(id);
        validateProvider(dto.providerId());
        validateJson(dto.clientRequirements(), "client_requirements");
        validateJson(dto.extraParams(), "extra_params");
        if (StringUtils.hasText(dto.modelId()) && !entity.getModelId().equals(dto.modelId().trim())) {
            throw new BusinessException(400, "model_id 创建后不可修改");
        }
        if (StringUtils.hasText(dto.modelType())
                && !normalizeType(entity.getModelType()).equalsIgnoreCase(dto.modelType().trim())) {
            throw new BusinessException(400, "模型类型创建后不可修改");
        }
        apply(entity, dto, entity.getModelId());
        requireUpstreamUnique(entity.getProviderId(), entity.getModelType(), entity.getUpstreamModel(), id);
        entity.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(dto.isDefault())) {
            clearDefault(normalizeType(entity.getModelType()), id);
        }
        gatewayModelMapper.updateById(entity);
        afterChange(entity);
        log.info("[网关] 修改模型 id={}, model_id={}", id, entity.getModelId());
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        GatewayModelEntity entity = requireEntity(id);
        if (TYPE_CHAT.equalsIgnoreCase(entity.getModelType())) {
            int refs = agentModelRefMapper.countByModel(entity.getModelId());
            if (refs > 0) {
                List<String> names = agentModelRefMapper.listNamesByModel(entity.getModelId());
                throw new BusinessException(400, "该模型已被 " + refs + " 个 Agent 引用（"
                        + String.join("、", names) + "），请先调整 Agent 绑定");
            }
        }
        gatewayModelMapper.deleteById(id);
        afterChange(entity);
        log.info("[网关] 删除模型 id={}, model_id={}", id, entity.getModelId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GatewayModelAdminVO markDefault(Long id) {
        GatewayModelEntity entity = requireEntity(id);
        clearDefault(normalizeType(entity.getModelType()), id);
        entity.setIsDefault(Boolean.TRUE);
        entity.setUpdateTime(LocalDateTime.now());
        gatewayModelMapper.updateById(entity);
        afterChange(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayModelAdminVO changeStatus(Long id, boolean enabled) {
        GatewayModelEntity entity = requireEntity(id);
        entity.setStatus(enabled ? STATUS_ENABLED : STATUS_DISABLED);
        entity.setUpdateTime(LocalDateTime.now());
        gatewayModelMapper.updateById(entity);
        afterChange(entity);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String test(Long id) {
        GatewayModelEntity entity = requireEntity(id);
        Optional<UpstreamEndpoint> upstream = modelUpstreamPort.resolve(entity.getProviderId());
        if (upstream.isEmpty()) {
            return "上游供应商不存在或已禁用，无法测试";
        }
        if (!upstream.get().usable()) {
            return "上游未配置 API Key，无法测试";
        }
        if (TYPE_EMBEDDING.equalsIgnoreCase(entity.getModelType())) {
            return "向量模型不提供对话测试，请直接在知识库入库/检索中验证";
        }
        byte[] body = buildTestBody(entity);
        long start = System.nanoTime();
        try {
            HttpResponse<byte[]> response = upstreamHttpClient.post(
                    upstream.get().baseUrl() + "/chat/completions", upstream.get().apiKey(), body);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            if (response.statusCode() >= 400) {
                return "上游返回 HTTP " + response.statusCode() + "，耗时 " + elapsed + " ms："
                        + truncate(new String(response.body(), StandardCharsets.UTF_8), 300);
            }
            JsonNode json = MAPPER.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            String reply = StringUtils.hasText(content) ? content.trim() : "(空)";
            return "连通成功，耗时 " + elapsed + " ms，模型回复: " + truncate(reply, 200);
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            log.warn("[网关] 模型测试失败 id={}: {}", id, e.getMessage());
            return "连通失败，耗时 " + elapsed + " ms：" + e.getMessage();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<GatewayModelAdminVO> batchImport(GatewayModelBatchImportDTO dto) {
        Optional<UpstreamEndpoint> upstream = modelUpstreamPort.resolve(dto.providerId());
        if (upstream.isEmpty()) {
            throw new BusinessException(400, "上游供应商不存在或已禁用: " + dto.providerId());
        }
        String modelType = StringUtils.hasText(dto.modelType())
                ? dto.modelType().trim().toUpperCase() : TYPE_CHAT;
        List<GatewayModelBatchImportDTO.Item> items = dto.resolvedItems();
        if (items.isEmpty()) {
            throw new BusinessException(400, "未选择要导入的上游模型");
        }
        String prefix = "gw-" + slug(upstream.get().providerKey()) + "-";
        List<GatewayModelAdminVO> created = new ArrayList<>();
        int skipped = 0;
        for (GatewayModelBatchImportDTO.Item item : items) {
            String model = item.upstreamModel().trim();
            if (existsUpstream(dto.providerId(), modelType, model)) {
                // 同一供应商下已有该上游模型，不重复建行
                skipped++;
                continue;
            }
            String modelId = uniqueModelId(prefix + slug(model));
            GatewayModelEntity entity = new GatewayModelEntity();
            entity.setModelId(modelId);
            entity.setDisplayName(StringUtils.hasText(item.displayName()) ? item.displayName().trim() : model);
            entity.setProviderId(dto.providerId());
            entity.setUpstreamModel(model);
            entity.setModelType(modelType);
            // 优先用上游 /models 返回的元数据，缺失时按保守默认值
            entity.setInputModalities(StringUtils.hasText(joinCsv(item.inputModalities()))
                    ? joinCsv(item.inputModalities()) : "text");
            entity.setSystemPromptUpdate(StringUtils.hasText(item.systemPromptUpdate())
                    ? item.systemPromptUpdate().trim() : "in-history");
            entity.setContextWindow(item.contextWindow());
            entity.setMaxOutputTokens(item.maxOutputTokens());
            entity.setReasoningEfforts(trimLength(joinCsv(item.reasoningEfforts()), 64));
            entity.setDefaultReasoningEffort(trimLength(item.defaultReasoningEffort(), 16));
            entity.setIsDefault(Boolean.FALSE);
            entity.setStatus(STATUS_ENABLED);
            entity.setSortOrder(0);
            entity.setRemark("从上游批量导入");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            gatewayModelMapper.insert(entity);
            created.add(toVO(entity));
        }
        catalogService.invalidate();
        log.info("[网关] 批量导入模型 provider#{} type={} 新增 {} 条，已存在跳过 {} 条",
                dto.providerId(), modelType, created.size(), skipped);
        return created;
    }

    /**
     * 将字符串列表拼为逗号分隔值（空集合返回 null）。
     *
     * @param values 列表
     * @return 逗号分隔串或 null
     */
    private static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                cleaned.add(value.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /**
     * 按列宽限制长度，避免上游元数据超出字典定义。
     *
     * @param value   原值
     * @param maxChars 最大字符数
     * @return 截断后的值或 null
     */
    private static String trimLength(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    // ---- 内部方法 ----

    private void apply(GatewayModelEntity entity, GatewayModelUpsertDTO dto, String modelId) {
        entity.setModelId(modelId);
        entity.setDisplayName(dto.displayName().trim());
        entity.setProviderId(dto.providerId());
        entity.setUpstreamModel(dto.upstreamModel().trim());
        entity.setModelType(StringUtils.hasText(dto.modelType())
                ? dto.modelType().trim().toUpperCase()
                : (StringUtils.hasText(entity.getModelType()) ? entity.getModelType() : TYPE_CHAT));
        entity.setContextWindow(dto.contextWindow());
        entity.setMaxOutputTokens(dto.maxOutputTokens());
        entity.setInputModalities(StringUtils.hasText(dto.inputModalities()) ? dto.inputModalities().trim() : "text");
        entity.setSystemPromptUpdate(StringUtils.hasText(dto.systemPromptUpdate())
                ? dto.systemPromptUpdate().trim() : "in-history");
        entity.setReasoningEfforts(trimToNull(dto.reasoningEfforts()));
        entity.setDefaultReasoningEffort(trimToNull(dto.defaultReasoningEffort()));
        entity.setClientRequirements(trimToNull(dto.clientRequirements()));
        entity.setExtraParams(trimToNull(dto.extraParams()));
        entity.setTemperature(dto.temperature());
        entity.setTopP(dto.topP());
        entity.setMaxTokens(dto.maxTokens());
        entity.setIsDefault(Boolean.TRUE.equals(dto.isDefault()));
        entity.setStatus(StringUtils.hasText(dto.status()) ? dto.status().trim().toUpperCase() : STATUS_ENABLED);
        entity.setSortOrder(dto.sortOrder() == null ? 0 : dto.sortOrder());
        entity.setRemark(trimToNull(dto.remark()));
    }

    private void afterChange(GatewayModelEntity entity) {
        catalogService.invalidate();
        if (TYPE_EMBEDDING.equalsIgnoreCase(normalizeType(entity.getModelType()))) {
            modelRuntimeCache.evictAll();
        }
    }

    private static String normalizeType(String modelType) {
        return StringUtils.hasText(modelType) ? modelType.toUpperCase() : TYPE_CHAT;
    }

    /**
     * 同一供应商 + 类型 + 上游模型名不得重复（避免同一模型多行导致默认/额度统计歧义）。
     *
     * @param providerId    供应商 id
     * @param modelType     模型类型
     * @param upstreamModel 上游模型名
     * @param excludeId     排除的自身 id（修改时传入），新建传 null
     */
    private void requireUpstreamUnique(Long providerId, String modelType, String upstreamModel, Long excludeId) {
        if (existsUpstream(providerId, normalizeType(modelType), upstreamModel, excludeId)) {
            throw new BusinessException(400, "该供应商下已存在 " + normalizeType(modelType)
                    + " 模型：" + upstreamModel);
        }
    }

    /**
     * 判断上游模型名是否已在该供应商的指定类型下存在。
     *
     * @param providerId    供应商 id
     * @param modelType     模型类型
     * @param upstreamModel 上游模型名
     * @return true=已存在
     */
    private boolean existsUpstream(Long providerId, String modelType, String upstreamModel) {
        return existsUpstream(providerId, modelType, upstreamModel, null);
    }

    /**
     * 判断上游模型名是否已在该供应商的指定类型下存在。
     *
     * @param providerId    供应商 id
     * @param modelType     模型类型
     * @param upstreamModel 上游模型名
     * @param excludeId     排除的自身 id，无则传 null
     * @return true=已存在
     */
    private boolean existsUpstream(Long providerId, String modelType, String upstreamModel, Long excludeId) {
        if (providerId == null || !StringUtils.hasText(upstreamModel)) {
            return false;
        }
        LambdaQueryWrapper<GatewayModelEntity> wrapper = new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getProviderId, providerId)
                .eq(GatewayModelEntity::getModelType, normalizeType(modelType))
                .eq(GatewayModelEntity::getUpstreamModel, upstreamModel.trim());
        if (excludeId != null) {
            wrapper.ne(GatewayModelEntity::getId, excludeId);
        }
        return gatewayModelMapper.selectCount(wrapper) > 0;
    }

    private GatewayModelEntity requireEntity(Long id) {
        GatewayModelEntity entity = gatewayModelMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "模型不存在: " + id);
        }
        return entity;
    }

    private void validateProvider(Long providerId) {
        if (modelUpstreamPort.resolve(providerId).isEmpty()) {
            throw new BusinessException(400, "上游供应商不存在或已禁用: " + providerId);
        }
    }

    private void validateJson(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            if (!MAPPER.readTree(json).isObject()) {
                throw new BusinessException(400, field + " 必须是 JSON 对象");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, field + " 不是合法 JSON");
        }
    }

    private void clearDefault(String modelType, Long exceptId) {
        LambdaUpdateWrapper<GatewayModelEntity> wrapper = new LambdaUpdateWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getModelType, modelType)
                .eq(GatewayModelEntity::getIsDefault, true)
                .set(GatewayModelEntity::getIsDefault, false)
                .set(GatewayModelEntity::getUpdateTime, LocalDateTime.now());
        if (exceptId != null) {
            wrapper.ne(GatewayModelEntity::getId, exceptId);
        }
        gatewayModelMapper.update(null, wrapper);
    }

    private String uniqueModelId(String base) {
        String candidate = base;
        int suffix = 2;
        while (gatewayModelMapper.selectCount(new LambdaQueryWrapper<GatewayModelEntity>()
                .eq(GatewayModelEntity::getModelId, candidate)) > 0) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private GatewayModelAdminVO toVO(GatewayModelEntity entity) {
        Optional<UpstreamEndpoint> upstream = modelUpstreamPort.resolve(entity.getProviderId());
        return new GatewayModelAdminVO(
                entity.getId(),
                entity.getModelId(),
                entity.getDisplayName(),
                entity.getProviderId(),
                upstream.map(UpstreamEndpoint::providerName).orElse(null),
                upstream.map(UpstreamEndpoint::providerKey).orElse(null),
                entity.getUpstreamModel(),
                entity.getModelType(),
                entity.getContextWindow(),
                entity.getMaxOutputTokens(),
                entity.getInputModalities(),
                entity.getSystemPromptUpdate(),
                entity.getReasoningEfforts(),
                entity.getDefaultReasoningEffort(),
                entity.getClientRequirements(),
                entity.getExtraParams(),
                entity.getTemperature(),
                entity.getTopP(),
                entity.getMaxTokens(),
                entity.getIsDefault(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getRemark(),
                upstream.map(UpstreamEndpoint::usable).orElse(false),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private byte[] buildTestBody(GatewayModelEntity entity) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", entity.getUpstreamModel());
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", "请只回复 OK");
        body.put("max_tokens", 16);
        body.put("stream", false);
        try {
            return MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new BusinessException(500, "构造测试请求失败");
        }
    }

    private static String slug(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "model";
        }
        String slug = raw.trim().toLowerCase().replaceAll("[^a-z0-9.]+", "-");
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isEmpty() ? "model" : slug;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
