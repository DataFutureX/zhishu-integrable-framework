package cn.datafuturex.zhishu.ai.modelconfig.config;

import cn.datafuturex.zhishu.ai.modelconfig.runtime.ModelProviderRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 运行时 LLM 配置 —— 委托 {@link ModelProviderRegistry}。
 * <p>
 * ChatModel 从默认模型设置获取；EmbeddingModel 全局唯一，始终从默认模型设置获取。
 * 使用懒加载代理，避免 Bean 创建阶段数据库迁移尚未执行。
 */
@Configuration
public class DynamicOpenAiConfig {

    @Bean
    @Primary
    public ChatModel runtimeChatModel(ModelProviderRegistry registry) {
        return registry.createLazyDefaultChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel runtimeEmbeddingModel(ModelProviderRegistry registry) {
        return registry.createLazyDefaultEmbeddingModel();
    }
}
