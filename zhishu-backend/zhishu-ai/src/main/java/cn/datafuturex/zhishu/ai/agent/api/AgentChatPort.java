package cn.datafuturex.zhishu.ai.agent.api;

import cn.datafuturex.zhishu.ai.shared.trace.AgentTraceEvent;
import cn.datafuturex.zhishu.ai.shared.vo.ChatResponseVO;

import java.util.function.Consumer;

/**
 * Chat 模块消费的智能体运行端口（隐藏内部 Entity）。
 */
public interface AgentChatPort {

    /**
     * agentId 为空时解析默认智能体。
     */
    long resolveAgentId(Long agentId);

    /**
     * 解析智能体能力码（大写），供结构化对话按能力装配 Tools。
     */
    java.util.List<String> resolveCapabilities(long agentId);

    ChatResponseVO run(long agentId, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature);

    /**
     * 可显式关闭会话记忆（如简报投递），避免污染正式对话。
     */
    ChatResponseVO run(long agentId, String message, String conversationId,
                       Boolean enableRag, Boolean enableMemory,
                       Integer maxTokens, Double temperature);

    ChatResponseVO run(long agentId, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature,
                       Consumer<AgentTraceEvent> onProgress);

    ChatResponseVO run(long agentId, String message, String conversationId,
                       Boolean enableRag, Integer maxTokens, Double temperature,
                       Consumer<AgentTraceEvent> onProgress, Consumer<String> onToken);
}
