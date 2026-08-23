package cn.datafuturex.zhishu.ai.modelconfig.runtime;

import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.credential.BearerTokenCredential;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 按库内加密配置动态构建 OpenAI 兼容 Chat / Embedding 客户端。
 */
@Slf4j
public final class RefreshableOpenAiModels {

    private RefreshableOpenAiModels() {
    }

    public static ChatModel chatModel(ModelConfigPort port) {
        return new DynamicChatModel(port);
    }

    public static EmbeddingModel embeddingModel(ModelConfigPort port) {
        return new DynamicEmbeddingModel(port);
    }

    private static final class Snapshot {
        private final String fingerprint;
        private final ChatModel chatModel;
        private final EmbeddingModel embeddingModel;

        private Snapshot(String fingerprint, ChatModel chatModel, EmbeddingModel embeddingModel) {
            this.fingerprint = fingerprint;
            this.chatModel = chatModel;
            this.embeddingModel = embeddingModel;
        }
    }

    private static String fingerprint(ModelConfigPort port) {
        return String.valueOf(port.currentApiKey()) + '|'
                + port.currentBaseUrl() + '|'
                + port.currentChatModel() + '|'
                + port.currentEmbeddingModel();
    }

    private static Snapshot build(ModelConfigPort port) {
        String apiKey = port.currentApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AiException("未配置模型 API Key，请在「模型设置」中填写并保存");
        }
        String baseUrl = port.currentBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        log.info("使用模型设置中的 API Key 调用 LLM masked={}, len={}, baseUrl={}",
                mask(apiKey), apiKey.length(), baseUrl);
        OpenAIClient sync = OpenAiSetup.setupSyncClient(
                baseUrl,
                apiKey,
                BearerTokenCredential.create(apiKey),
                null,
                null,
                null,
                false,
                false,
                null,
                Duration.ofSeconds(60),
                2,
                null,
                Map.of(),
                ObservationRegistry.NOOP,
                null,
                List.of());
        OpenAIClientAsync async = OpenAiSetup.setupAsyncClient(
                baseUrl,
                apiKey,
                BearerTokenCredential.create(apiKey),
                null,
                null,
                null,
                false,
                false,
                null,
                Duration.ofSeconds(60),
                2,
                null,
                Map.of(),
                ObservationRegistry.NOOP,
                null,
                List.of());
        ChatModel chat = OpenAiChatModel.builder()
                .openAiClient(sync)
                .openAiClientAsync(async)
                .options(OpenAiChatOptions.builder()
                        .model(port.currentChatModel())
                        .temperature(port.currentTemperature())
                        .maxTokens(port.currentMaxTokens())
                        .build())
                .build();
        EmbeddingModel embedding = OpenAiEmbeddingModel.builder()
                .openAiClient(sync)
                .options(OpenAiEmbeddingOptions.builder()
                        .model(port.currentEmbeddingModel())
                        .build())
                .build();
        log.info("已按模型设置重建 LLM 客户端 baseUrl={}, chatModel={}",
                port.currentBaseUrl(), port.currentChatModel());
        return new Snapshot(fingerprint(port), chat, embedding);
    }

    private static final class Store {
        private volatile Snapshot snapshot;

        private Snapshot ensure(ModelConfigPort port) {
            String fp = fingerprint(port);
            Snapshot current = snapshot;
            if (current != null && fp.equals(current.fingerprint)) {
                return current;
            }
            synchronized (this) {
                current = snapshot;
                if (current != null && fp.equals(current.fingerprint)) {
                    return current;
                }
                Snapshot created = build(port);
                snapshot = created;
                return created;
            }
        }
    }

    private static final Store STORE = new Store();

    private static String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey) || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static final class DynamicChatModel implements ChatModel {
        private final ModelConfigPort port;

        private DynamicChatModel(ModelConfigPort port) {
            this.port = port;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return STORE.ensure(port).chatModel.call(prompt);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return STORE.ensure(port).chatModel.stream(prompt);
        }

        @Override
        public ChatOptions getOptions() {
            return STORE.ensure(port).chatModel.getOptions();
        }

        @Override
        @Deprecated
        public ChatOptions getDefaultOptions() {
            return getOptions();
        }
    }

    private static final class DynamicEmbeddingModel implements EmbeddingModel {
        private final ModelConfigPort port;

        private DynamicEmbeddingModel(ModelConfigPort port) {
            this.port = port;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return STORE.ensure(port).embeddingModel.call(request);
        }

        @Override
        public float[] embed(String text) {
            return STORE.ensure(port).embeddingModel.embed(text);
        }

        @Override
        public float[] embed(Document document) {
            return STORE.ensure(port).embeddingModel.embed(document);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return STORE.ensure(port).embeddingModel.embed(texts);
        }

        @Override
        public int dimensions() {
            try {
                return STORE.ensure(port).embeddingModel.dimensions();
            } catch (Exception e) {
                return 1024;
            }
        }
    }
}
