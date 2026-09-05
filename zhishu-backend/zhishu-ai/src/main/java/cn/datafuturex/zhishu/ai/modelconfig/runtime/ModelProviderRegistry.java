package cn.datafuturex.zhishu.ai.modelconfig.runtime;

import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.ModelProviderMapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型设置运行时注册表 —— 按 providerId 缓存 ChatModel，全局唯一 EmbeddingModel。
 * <p>
 * 内部使用 fingerprint + double-check-locking 机制，配置变更后调用 {@link #evict(Long)} 清除缓存。
 */
@Component
@Slf4j
public class ModelProviderRegistry {

    private final ModelProviderMapper modelProviderMapper;
    private final ModelConfigProperties modelConfigProperties;

    @Value("${spring.ai.openai.api-key:}")
    private String envApiKey;

    /** providerId -> ChatModel 缓存 */
    private final ConcurrentHashMap<Long, ChatSnapshot> chatCache = new ConcurrentHashMap<>();

    /** EmbeddingModel 全局缓存（仅默认供应商） */
    private volatile EmbeddingSnapshot embeddingCache;

    public ModelProviderRegistry(ModelProviderMapper modelProviderMapper,
                                 ModelConfigProperties modelConfigProperties) {
        this.modelProviderMapper = modelProviderMapper;
        this.modelConfigProperties = modelConfigProperties;
    }

    /**
     * 解析指定供应商的 ChatModel（含生成参数），未命中缓存则构建。
     *
     * @param providerId 模型设置 ID
     * @return ChatModel 实例
     */
    public ChatModel resolve(Long providerId) {
        if (providerId == null) {
            return resolveDefault();
        }
        ChatSnapshot snap = chatCache.get(providerId);
        String fp = fingerprint(providerId);
        if (snap != null && fp.equals(snap.fingerprint)) {
            return snap.chatModel;
        }
        synchronized (chatCache) {
            snap = chatCache.get(providerId);
            if (snap != null && fp.equals(snap.fingerprint)) {
                return snap.chatModel;
            }
            ModelProviderEntity entity = loadProvider(providerId);
            ChatModel model = buildChatModel(entity);
            chatCache.put(providerId, new ChatSnapshot(fp, model));
            log.info("已为 provider#{} 构建 ChatModel model={}", providerId, entity.getChatModel());
            return model;
        }
    }

    /**
     * 解析默认模型设置的 ChatModel。
     *
     * @return 默认 ChatModel 实例
     */
    public ChatModel resolveDefault() {
        ModelProviderEntity def = loadDefaultProvider();
        return resolve(def.getId());
    }

    /**
     * 始终从默认模型设置构建 EmbeddingModel（全局唯一实例）。
     *
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel resolveEmbeddingModel() {
        ModelProviderEntity def = loadDefaultProvider();
        String fp = embeddingFingerprint(def);
        EmbeddingSnapshot snap = embeddingCache;
        if (snap != null && fp.equals(snap.fingerprint)) {
            return snap.embeddingModel;
        }
        synchronized (this) {
            snap = embeddingCache;
            if (snap != null && fp.equals(snap.fingerprint)) {
                return snap.embeddingModel;
            }
            String apiKey = resolveApiKey(def);
            EmbeddingModel model = RefreshableOpenAiModels.embeddingModel(
                    def.getBaseUrl(), apiKey, def.getEmbeddingModel());
            embeddingCache = new EmbeddingSnapshot(fp, model);
            log.info("已为默认模型设置构建 EmbeddingModel model={}", def.getEmbeddingModel());
            return model;
        }
    }

    /**
     * 模型设置变更后清除缓存。
     *
     * @param providerId 模型设置 ID
     */
    public void evict(Long providerId) {
        chatCache.remove(providerId);
        if (providerId != null) {
            ModelProviderEntity entity = modelProviderMapper.selectById(providerId);
            if (entity != null && Boolean.TRUE.equals(entity.getIsDefault())) {
                embeddingCache = null;
            }
        }
        log.info("已清除 provider#{} 的缓存", providerId);
    }

    /**
     * 获取指定供应商的对话模型名。
     *
     * @param providerId 模型设置 ID
     * @return 模型名
     */
    public String resolveChatModelName(Long providerId) {
        if (providerId == null) {
            return loadDefaultProvider().getChatModel();
        }
        ModelProviderEntity entity = modelProviderMapper.selectById(providerId);
        if (entity == null) {
            return loadDefaultProvider().getChatModel();
        }
        return entity.getChatModel();
    }

    // ---- 内部方法 ----

    private ChatModel buildChatModel(ModelProviderEntity entity) {
        String apiKey = resolveApiKey(entity);
        Double temperature = entity.getTemperature() != null ? entity.getTemperature().doubleValue() : 0.7;
        Double topP = entity.getTopP() != null ? entity.getTopP().doubleValue() : 0.9;
        return RefreshableOpenAiModels.chatModel(
                entity.getBaseUrl(), apiKey, entity.getChatModel(),
                temperature, entity.getMaxTokens(), topP);
    }

    private String resolveApiKey(ModelProviderEntity entity) {
        if (StringUtils.hasText(entity.getApiKeyEnc())) {
            return decryptApiKey(entity.getApiKeyEnc());
        }
        return StringUtils.hasText(envApiKey) ? envApiKey.trim() : null;
    }

    private String decryptApiKey(String enc) {
        String wrapKey = modelConfigProperties.getCryptoKey();
        if (!StringUtils.hasText(wrapKey)) {
            wrapKey = "zhishu-dev-model-key-wrap";
        }
        return McpCrypto.decrypt(enc, wrapKey);
    }

    /**
     * 获取模型设置实体（供日志打印等用途）。
     * 返回结果包含是否回退到默认的信息。
     *
     * @param providerId 模型设置 ID，null 表示默认
     * @return 模型设置实体，不存在返回默认
     */
    public ModelProviderEntity getProvider(Long providerId) {
        return loadProvider(providerId);
    }

    /**
     * 获取模型设置的 Base URL（实际调用地址）。
     *
     * @param providerId 模型设置 ID，null 表示默认
     * @return 实际调用的 Base URL
     */
    public String resolveBaseUrl(Long providerId) {
        ModelProviderEntity entity = loadProvider(providerId);
        if (entity == null) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String url = entity.getBaseUrl();
        return (url != null && !url.isBlank()) ? url.trim() : "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    private ModelProviderEntity loadProvider(Long providerId) {
        if (providerId == null) {
            log.debug("providerId 为 null，使用默认模型设置");
            return loadDefaultProvider();
        }
        ModelProviderEntity entity = modelProviderMapper.selectById(providerId);
        if (entity == null) {
            log.warn("provider#{} 不存在，回退到默认", providerId);
            return loadDefaultProvider();
        }
        if (!"ENABLED".equalsIgnoreCase(entity.getStatus())) {
            log.warn("provider#{} 已禁用(id={}, name={})，回退到默认", providerId, entity.getId(), entity.getName());
            return loadDefaultProvider();
        }
        log.debug("加载 provider: id={}, name={}, baseUrl={}, chatModel={}", 
                entity.getId(), entity.getName(), entity.getBaseUrl(), entity.getChatModel());
        return entity;
    }

    private ModelProviderEntity loadDefaultProvider() {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelProviderEntity>()
                .eq(ModelProviderEntity::getIsDefault, true)
                .last("LIMIT 1");
        ModelProviderEntity def = modelProviderMapper.selectOne(wrapper);
        if (def != null) {
            return def;
        }
        // 回退：取第一条
        def = modelProviderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelProviderEntity>()
                        .orderByAsc(ModelProviderEntity::getId)
                        .last("LIMIT 1"));
        if (def == null) {
            throw new AiException("未配置任何模型设置，请在「模型设置」中创建");
        }
        return def;
    }

    private String fingerprint(Long providerId) {
        ModelProviderEntity e = modelProviderMapper.selectById(providerId);
        if (e == null) {
            return "null-" + providerId;
        }
        return e.getId() + "|" + e.getBaseUrl() + "|" + e.getChatModel()
                + "|" + (e.getApiKeyEnc() != null ? e.getApiKeyEnc().hashCode() : "nokey")
                + "|" + e.getTemperature() + "|" + e.getMaxTokens() + "|" + e.getTopP();
    }

    private String embeddingFingerprint(ModelProviderEntity def) {
        return def.getId() + "|emb|" + def.getBaseUrl()
                + "|" + def.getEmbeddingModel()
                + "|" + (def.getApiKeyEnc() != null ? def.getApiKeyEnc().hashCode() : "nokey");
    }

    /**
     * 创建懒加载 ChatModel 代理，首次调用时才解析默认供应商。
     * 供 DynamicOpenAiConfig 在 Bean 创建阶段使用，避免启动时数据库未就绪。
     *
     * @return 懒加载 ChatModel
     */
    public ChatModel createLazyDefaultChatModel() {
        return new LazyDefaultChatModel();
    }

    /**
     * 创建懒加载 EmbeddingModel 代理，首次调用时才解析默认供应商。
     *
     * @return 懒加载 EmbeddingModel
     */
    public EmbeddingModel createLazyDefaultEmbeddingModel() {
        return new LazyDefaultEmbeddingModel();
    }

    private record ChatSnapshot(String fingerprint, ChatModel chatModel) {}
    private record EmbeddingSnapshot(String fingerprint, EmbeddingModel embeddingModel) {}

    /**
     * 懒加载 ChatModel 代理，首次 call/stream 时解析默认供应商。
     */
    private final class LazyDefaultChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return resolveDefault().call(prompt);
        }

        @Override
        public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
            return resolveDefault().stream(prompt);
        }

        @Override
        public ChatOptions getOptions() {
            return resolveDefault().getOptions();
        }

        @Override
        @Deprecated
        public ChatOptions getDefaultOptions() {
            return getOptions();
        }
    }

    /**
     * 懒加载 EmbeddingModel 代理，首次调用时解析默认供应商。
     */
    private final class LazyDefaultEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return resolveEmbeddingModel().call(request);
        }

        @Override
        public float[] embed(String text) {
            return resolveEmbeddingModel().embed(text);
        }

        @Override
        public float[] embed(Document document) {
            return resolveEmbeddingModel().embed(document);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return resolveEmbeddingModel().embed(texts);
        }

        @Override
        public int dimensions() {
            try {
                return resolveEmbeddingModel().dimensions();
            } catch (Exception e) {
                return 1024;
            }
        }
    }
}
