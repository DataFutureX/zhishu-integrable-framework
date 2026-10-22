package cn.datafuturex.zhishu.ai.gatewayclient;

import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * 网关默认模型 ChatModel：每次调用按当前请求身份解析真实的 ChatModel。
 * <p>
 * 供 {@code @Primary ChatModel} 使用，使控制台既有 ChatClient 自动经模型网关转发。
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayBackedChatModel implements ChatModel {

    private final GatewayChatModelRegistry registry;

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate().call(prompt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate().stream(prompt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatOptions getOptions() {
        try {
            return delegate().getOptions();
        } catch (Exception e) {
            log.debug("[网关客户端] 默认模型选项解析失败: {}", e.getMessage());
            return OpenAiChatOptions.builder().build();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public ChatOptions getDefaultOptions() {
        return getOptions();
    }

    private ChatModel delegate() {
        try {
            return registry.resolve(null, null);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("模型网关调用失败: " + e.getMessage());
        }
    }
}
