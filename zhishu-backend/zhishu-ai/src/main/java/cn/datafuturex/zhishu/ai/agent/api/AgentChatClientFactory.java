package cn.datafuturex.zhishu.ai.agent.api;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Agent 客户端装配工厂 —— 供编排型模块复用「按能力装配工具 + 构建 ChatClient」。
 * <p>
 * 存在的意义：{@code ChatClientSupport} 属于 Agent 模块运行时内部件，
 * Chat 等模块不应直接依赖它，改为依赖本端口，避免把工具注册、轨迹包装等实现细节外泄。
 */
public interface AgentChatClientFactory {

    /**
     * 按能力清单解析工具（含 Agent 绑定的外部 MCP 工具），不记录调用轨迹。
     *
     * @param capabilities 能力标识列表，null 视为空
     * @param agentId      Agent 主键；非空时附加其外部工具
     * @return 工具回调列表
     */
    List<ToolCallback> resolveTools(List<String> capabilities, Long agentId);

    /**
     * 构建 ChatClient（走网关默认模型，按 AGENT 通道记账）。
     *
     * @param systemPrompt 系统提示词
     * @param tools        工具列表
     * @param options      采样参数
     * @param enableMemory 是否启用会话记忆
     * @return ChatClient
     */
    ChatClient buildClient(String systemPrompt, List<ToolCallback> tools, OpenAiChatOptions options, boolean enableMemory);
}
