package cn.datafuturex.zhishu.gateway.port;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 向量模型端口 —— 由模型网关实现，供 AI 内核取默认向量模型。
 * <p>
 * 向量不经网关推理面（仍直连上游），但配置来源统一为 {@code ai_gateway_model} 的
 * {@code model_type=EMBEDDING} 默认行；供应商表不再保存向量模型名。
 */
public interface EmbeddingTargetPort {

    /**
     * 默认向量模型客户端。
     * <p>
     * 返回懒加载代理：首次调用时才解析，避免 Bean 创建阶段数据库尚未就绪；
     * 未配置可用向量模型时抛出 {@link ModelTargetException}。
     *
     * @return EmbeddingModel
     */
    EmbeddingModel defaultEmbeddingModel();
}
