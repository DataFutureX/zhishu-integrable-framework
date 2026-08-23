package cn.datafuturex.zhishu.ai.modelconfig.config;

import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.modelconfig.runtime.RefreshableOpenAiModels;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 以库内加密密钥构造运行时 LLM，覆盖环境变量明文 Key。
 */
@Configuration
public class DynamicOpenAiConfig {

    @Bean
    @Primary
    public ChatModel runtimeChatModel(ModelConfigPort modelConfigPort) {
        return RefreshableOpenAiModels.chatModel(modelConfigPort);
    }

    @Bean
    @Primary
    public EmbeddingModel runtimeEmbeddingModel(ModelConfigPort modelConfigPort) {
        return RefreshableOpenAiModels.embeddingModel(modelConfigPort);
    }
}
