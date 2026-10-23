package cn.datafuturex.zhishu.ai.gatewayclient;

import cn.datafuturex.zhishu.ai.modelconfig.api.AiChatTargetPort;
import cn.datafuturex.zhishu.ai.modelconfig.api.AiEmbeddingTargetPort;
import cn.datafuturex.zhishu.ai.shared.exception.AiException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 运行时 LLM 配置 —— ChatModel 统一走模型网关，embedding 仍直连上游。
 * <p>
 * 供应商与模型行均住在模型网关模块，本类只通过 {@code AiChatTargetPort} / {@code AiEmbeddingTargetPort}
 * 取运行时模型；{@code zhishu.gateway.internal.enabled=false} 时回退直连上游。
 * 端口返回懒加载代理，避免 Bean 创建阶段数据库迁移尚未执行。
 */
@Configuration
public class DynamicOpenAiConfig {

    @Bean
    @Primary
    public ChatModel runtimeChatModel(ObjectProvider<AiChatTargetPort> chatTargetPort,
                                      GatewayChatModelRegistry gatewayRegistry) {
        if (gatewayRegistry.enabled()) {
            return new GatewayBackedChatModel(gatewayRegistry);
        }
        AiChatTargetPort port = chatTargetPort.getIfAvailable();
        if (port == null) {
            throw new AiException("未装配模型网关模块：无法解析对话模型，请确认 zhishu-model-gateway 已引入且 zhishu.gateway.enabled 未被排除");
        }
        return port.defaultChatModel();
    }

    @Bean
    @Primary
    public EmbeddingModel runtimeEmbeddingModel(ObjectProvider<AiEmbeddingTargetPort> embeddingTargetPort) {
        AiEmbeddingTargetPort port = embeddingTargetPort.getIfAvailable();
        if (port == null) {
            throw new AiException("未装配模型网关模块：无法解析向量模型，请确认 zhishu-model-gateway 已引入");
        }
        return port.defaultEmbeddingModel();
    }
}
