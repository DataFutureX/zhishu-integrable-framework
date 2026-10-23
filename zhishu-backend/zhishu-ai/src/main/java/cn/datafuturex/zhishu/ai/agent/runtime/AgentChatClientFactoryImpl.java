package cn.datafuturex.zhishu.ai.agent.runtime;

import cn.datafuturex.zhishu.ai.agent.api.AgentChatClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AgentChatClientFactory} 实现：委托 Agent 运行时的 {@code ChatClientSupport}。
 * <p>
 * 只做端口到内部装配器的转接，本身不含业务逻辑，因此可以安全地暴露给编排型模块。
 */
@Component
@RequiredArgsConstructor
public class AgentChatClientFactoryImpl implements AgentChatClientFactory {

    private final ChatClientSupport chatClientSupport;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ToolCallback> resolveTools(List<String> capabilities, Long agentId) {
        return chatClientSupport.resolveTools(capabilities, null, null, agentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ChatClient buildClient(
            String systemPrompt, List<ToolCallback> tools, OpenAiChatOptions options, boolean enableMemory) {
        return chatClientSupport.buildClient(systemPrompt, tools, options, enableMemory);
    }
}
