package cn.datafuturex.zhishu.ai.modelconfig.runtime;

import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.credential.BearerTokenCredential;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * per-provider OpenAI 兼容 Chat / Embedding 客户端构建器。
 * <p>
 * 每个供应商独立构建一组 ChatModel + EmbeddingModel，由 ModelProviderRegistry 管理缓存。
 */
@Slf4j
public final class RefreshableOpenAiModels {

    private RefreshableOpenAiModels() {
    }

    /**
     * 构建 ChatModel。
     *
     * @param baseUrl     OpenAI 兼容 Base URL
     * @param apiKey      解密后的 API Key
     * @param chatModel   对话模型名
     * @param temperature 温度
     * @param maxTokens   最大 Token
     * @param topP        Top P
     * @return 构建好的 ChatModel
     */
    public static ChatModel chatModel(
            String baseUrl, String apiKey, String chatModel,
            Double temperature, Integer maxTokens, Double topP) {
        requireApiKey(apiKey);
        String effectiveUrl = effectiveBaseUrl(baseUrl);
        log.info("构建 ChatModel baseUrl={}, model={}", effectiveUrl, chatModel);
        OpenAIClient sync = buildSyncClient(effectiveUrl, apiKey);
        OpenAIClientAsync async = buildAsyncClient(effectiveUrl, apiKey);
        var optBuilder = OpenAiChatOptions.builder().model(chatModel);
        if (temperature != null) {
            optBuilder.temperature(temperature);
        }
        if (maxTokens != null) {
            optBuilder.maxTokens(maxTokens);
        }
        if (topP != null) {
            optBuilder.topP(topP);
        }
        return OpenAiChatModel.builder()
                .openAiClient(sync)
                .openAiClientAsync(async)
                .options(optBuilder.build())
                .build();
    }

    /**
     * 构建 EmbeddingModel。
     *
     * @param baseUrl        OpenAI 兼容 Base URL
     * @param apiKey         解密后的 API Key
     * @param embeddingModel 向量模型名
     * @return 构建好的 EmbeddingModel
     */
    public static EmbeddingModel embeddingModel(String baseUrl, String apiKey, String embeddingModel) {
        requireApiKey(apiKey);
        String effectiveUrl = effectiveBaseUrl(baseUrl);
        log.info("构建 EmbeddingModel baseUrl={}, model={}", effectiveUrl, embeddingModel);
        OpenAIClient sync = buildSyncClient(effectiveUrl, apiKey);
        return OpenAiEmbeddingModel.builder()
                .openAiClient(sync)
                .options(OpenAiEmbeddingOptions.builder()
                        .model(embeddingModel != null ? embeddingModel : "text-embedding-v3")
                        .build())
                .build();
    }

    private static void requireApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiException("未配置模型 API Key，请在「模型设置」中填写并保存");
        }
    }

    private static String effectiveBaseUrl(String baseUrl) {
        return StringUtils.hasText(baseUrl)
                ? trimSlash(baseUrl)
                : "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    private static OpenAIClient buildSyncClient(String baseUrl, String apiKey) {
        return OpenAiSetup.setupSyncClient(
                baseUrl, apiKey, BearerTokenCredential.create(apiKey),
                null, null, null, false, false, null,
                Duration.ofMinutes(5), 2, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of());
    }

    private static OpenAIClientAsync buildAsyncClient(String baseUrl, String apiKey) {
        return OpenAiSetup.setupAsyncClient(
                baseUrl, apiKey, BearerTokenCredential.create(apiKey),
                null, null, null, false, false, null,
                Duration.ofMinutes(5), 2, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of());
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
}
