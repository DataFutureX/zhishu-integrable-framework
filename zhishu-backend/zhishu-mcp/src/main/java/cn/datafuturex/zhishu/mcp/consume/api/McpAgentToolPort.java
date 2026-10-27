package cn.datafuturex.zhishu.mcp.consume.api;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * consume 平面唯一对外出口：按智能体解析「已接入的他方 MCP 工具」。
 * <p>
 * 由 {@code zhishu-ai} 的 Agent 运行时消费（原 {@code ai.shared.mcp.ExternalToolPort}，
 * 迁到本模块以确立 {@code ai -> mcp} 的单向依赖）。
 */
public interface McpAgentToolPort {

    /**
     * 解析指定智能体可用的上游 MCP 工具（已完成改名、启停过滤与审计包装）。
     *
     * @param agentId 智能体 id，null 返回空列表
     * @return 工具回调列表；上游不可用时返回空列表（不抛异常，避免拖垮整轮会话）
     */
    List<ToolCallback> resolveForAgent(Long agentId);

    /**
     * 智能体已绑定的上游 id 列表（供控制台回显）。
     */
    List<Long> listBoundUpstreamIds(Long agentId);

    /**
     * 整体替换智能体的上游绑定（{@code upstreamIds} 为空表示解绑全部）。
     */
    void bindAgentUpstreams(Long agentId, List<Long> upstreamIds);
}
