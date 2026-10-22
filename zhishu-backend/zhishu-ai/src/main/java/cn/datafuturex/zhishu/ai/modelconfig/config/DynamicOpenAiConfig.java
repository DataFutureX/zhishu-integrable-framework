package cn.datafuturex.zhishu.ai.modelconfig.config;

import cn.datafuturex.zhishu.ai.gatewayclient.GatewayBackedChatModel;
import cn.datafuturex.zhishu.ai.gatewayclient.GatewayChatModelRegistry;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 运行时 LLM 配置 —— ChatModel 统一走模型网关，embedding 仍直连上游。
 * <p>
 * {@code zhishu.gateway.internal.enabled=false} 时回退直连上游（默认 provider）。
 * 使用懒加载代理，避免 Bean 创建阶段数据库迁移尚未执行。
 */
@Configuration
public class DynamicOpenAiConfig {

    @Bean
    @Primary
    public ChatModel runtimeChatModel(ModelProviderRegistry registry,
                                      GatewayChatModelRegistry gatewayRegistry) {
        if (gatewayRegistry.enabled()) {
            return new GatewayBackedChatModel(gatewayRegistry);
        }
        return registry.createLazyDefaultChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel runtimeEmbeddingModel(ModelProviderRegistry registry) {
        return registry.createLazyDefaultEmbeddingModel();
    }
}
