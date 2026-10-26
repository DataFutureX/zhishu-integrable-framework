package cn.datafuturex.zhishu.gateway.provider.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供应商连通性测试的地址与上游类型判定回归测试。
 * <p>
 * 锁死两条易错规则：Ollama 走原生 {@code /api/tags}（需剥掉 OpenAI 兼容的 {@code /v1} 后缀），
 * 以及「是否 Ollama」由标识/名称/BaseURL 任一命中即成立（大小写不敏感）。
 */
class ModelProviderUpstreamUrlTest {

    @Test
    @DisplayName("OpenAI 兼容供应商拼 /models 并去掉尾部斜杠")
    void openAiCompatibleModelsPath() {
        assertEquals("https://api.deepseek.com/v1/models",
                ModelProviderServiceImpl.upstreamModelsUrl("https://api.deepseek.com/v1", false));
        assertEquals("https://api.deepseek.com/v1/models",
                ModelProviderServiceImpl.upstreamModelsUrl("https://api.deepseek.com/v1/", false));
        assertEquals("http://localhost:8000/models",
                ModelProviderServiceImpl.upstreamModelsUrl("http://localhost:8000", false));
    }

    @Test
    @DisplayName("Ollama 走原生 /api/tags，并剥掉误填的 /v1 后缀")
    void ollamaTagsPath() {
        assertEquals("http://localhost:11434/api/tags",
                ModelProviderServiceImpl.upstreamModelsUrl("http://localhost:11434", true));
        assertEquals("http://localhost:11434/api/tags",
                ModelProviderServiceImpl.upstreamModelsUrl("http://localhost:11434/v1", true));
        assertEquals("http://localhost:11434/api/tags",
                ModelProviderServiceImpl.upstreamModelsUrl("http://localhost:11434/v1/", true));
    }

    @Test
    @DisplayName("Ollama 判定：标识 / 名称 / BaseURL 任一命中即可，且大小写不敏感")
    void ollamaDetection() {
        assertTrue(ModelProviderServiceImpl.isOllamaProvider("ollama", null, null));
        assertTrue(ModelProviderServiceImpl.isOllamaProvider("OLLAMA", null, null));
        assertTrue(ModelProviderServiceImpl.isOllamaProvider("custom", "本地 Ollama", null));
        assertTrue(ModelProviderServiceImpl.isOllamaProvider(null, null, "http://ollama:11434"));
        assertFalse(ModelProviderServiceImpl.isOllamaProvider("dashscope", "阿里百炼",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertFalse(ModelProviderServiceImpl.isOllamaProvider(null, null, null));
    }
}
