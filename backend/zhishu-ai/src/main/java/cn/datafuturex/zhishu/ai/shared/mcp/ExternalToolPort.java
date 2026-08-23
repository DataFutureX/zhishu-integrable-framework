package cn.datafuturex.zhishu.ai.shared.mcp;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 他方 MCP 工具接入（由 mcp 模块实现，Agent 运行时消费）。
 */
public interface ExternalToolPort {

    List<ToolCallback> resolveForAgent(Long agentId);

    List<Long> listBoundUpstreamIds(Long agentId);

    void bindAgentUpstreams(Long agentId, List<Long> upstreamIds);
}
