package cn.datafuturex.zhishu.ai.modelconfig.api;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Optional;

/**
 * 对话（chat）目标端口 —— 由模型网关侧实现，供回退模式（直连上游）按模型行构建 ChatModel。
 */
public interface AiChatTargetPort {

    /**
     * 端口是否启用。
     *
     * @return true=可按模型行解析
     */
    boolean enabled();

    /**
     * 按模型 id 解析对话目标。
     *
     * @param modelId 网关模型 id
     * @return 目标；模型不存在/停用/上游不可用时返回空
     */
    Optional<Target> resolveByModelId(String modelId);

    /**
     * 默认对话模型客户端（仅在回退模式二使用）。
     * <p>
     * 返回懒加载代理：首次调用时才解析上游连接，Bean 构造阶段不访问数据库。
     *
     * @return ChatModel
     */
    ChatModel defaultChatModel();

    /**
     * 默认对话模型名（日志与监控展示用，不抛异常）。
     *
     * @return 模型名；无法解析时返回 null
     */
    String describeDefaultChatModelName();

    /**
     * 对话目标快照。
     *
     * @param modelId       网关模型 id
     * @param upstreamModel 供应商真实模型名
     * @param baseUrl       供应商 Base URL
     * @param apiKey        解密后的 Key
     * @param temperature   默认温度
     * @param topP          默认 Top P
     * @param maxTokens     默认最大输出
     */
    record Target(String modelId, String upstreamModel, String baseUrl, String apiKey,
                  Double temperature, Double topP, Integer maxTokens) {
    }
}
