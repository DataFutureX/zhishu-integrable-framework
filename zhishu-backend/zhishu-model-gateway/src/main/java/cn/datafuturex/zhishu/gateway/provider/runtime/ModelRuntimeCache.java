package cn.datafuturex.zhishu.gateway.provider.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 模型运行时缓存与懒加载代理（模型网关侧）。
 * <p>
 * 模型名与生成参数一律来自 {@code ai_gateway_model} 模型行；本类只负责按指纹缓存已构建的
 * Spring AI 客户端，并在 Bean 创建阶段提供懒解析代理（首次调用才查库）。
 * 供应商/模型变更后统一 {@link #evictAll()}，构建成本很低。
 */
@Component
@Slf4j
public class ModelRuntimeCache {

    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();

    private final Map<String, EmbeddingModel> embeddingModels = new ConcurrentHashMap<>();

    /**
     * 按指纹取（或构建）ChatModel。
     *
     * @param fingerprint 连接与参数指纹
     * @param factory     构建函数
     * @return ChatModel
     */
    public ChatModel chatModel(String fingerprint, Supplier<ChatModel> factory) {
        return chatModels.computeIfAbsent(fingerprint, key -> {
            ChatModel created = factory.get();
            log.debug("[网关运行时] 构建 ChatModel fingerprint={}", key);
            return created;
        });
    }

    /**
     * 按指纹取（或构建）EmbeddingModel。
     *
     * @param fingerprint 连接与模型名指纹
     * @param factory     构建函数
     * @return EmbeddingModel
     */
    public EmbeddingModel embeddingModel(String fingerprint, Supplier<EmbeddingModel> factory) {
        return embeddingModels.computeIfAbsent(fingerprint, key -> {
            EmbeddingModel created = factory.get();
            log.debug("[网关运行时] 构建 EmbeddingModel fingerprint={}", key);
            return created;
        });
    }

    /**
     * 懒解析默认 ChatModel，供 {@code @Primary} Bean 在启动阶段使用。
     *
     * @param resolver 实际解析逻辑
     * @return 懒加载 ChatModel
     */
    public ChatModel lazyChatModel(Supplier<ChatModel> resolver) {
        return new LazyChatModel(resolver);
    }

    /**
     * 懒解析默认 EmbeddingModel，供 {@code @Primary} Bean 在启动阶段使用。
     *
     * @param resolver 实际解析逻辑
     * @return 懒加载 EmbeddingModel
     */
    public EmbeddingModel lazyEmbeddingModel(Supplier<EmbeddingModel> resolver) {
        return new LazyEmbeddingModel(resolver);
    }

    /**
     * 清空全部已构建的模型客户端。
     */
    public void evictAll() {
        chatModels.clear();
        embeddingModels.clear();
        log.info("[网关运行时] 已清空模型运行时缓存");
    }

    /**
     * 懒加载 ChatModel 代理：首次调用才解析。
     */
    private static final class LazyChatModel implements ChatModel {

        private final Supplier<ChatModel> resolver;

        private LazyChatModel(Supplier<ChatModel> resolver) {
            this.resolver = resolver;
        }

        private ChatModel delegate() {
            return resolver.get();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return delegate().call(prompt);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return delegate().stream(prompt);
        }

        @Override
        public ChatOptions getOptions() {
            return delegate().getOptions();
        }
    }

    /**
     * 懒加载 EmbeddingModel 代理：首次调用才解析。
     */
    private static final class LazyEmbeddingModel implements EmbeddingModel {

        private final Supplier<EmbeddingModel> resolver;

        private LazyEmbeddingModel(Supplier<EmbeddingModel> resolver) {
            this.resolver = resolver;
        }

        private EmbeddingModel delegate() {
            return resolver.get();
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return delegate().call(request);
        }

        @Override
        public float[] embed(String text) {
            return delegate().embed(text);
        }

        @Override
        public float[] embed(Document document) {
            return delegate().embed(document);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return delegate().embed(texts);
        }

        @Override
        public int dimensions() {
            try {
                return delegate().dimensions();
            } catch (Exception e) {
                // 模型尚未配置或被禁用时回退常用维度，避免向量库初始化失败
                return 1024;
            }
        }
    }
}
