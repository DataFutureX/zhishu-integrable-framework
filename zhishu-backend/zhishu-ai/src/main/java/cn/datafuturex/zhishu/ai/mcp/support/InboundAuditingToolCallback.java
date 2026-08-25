package cn.datafuturex.zhishu.ai.mcp.support;

import cn.datafuturex.zhishu.ai.mcp.service.McpCallLogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class InboundAuditingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final McpCallLogService callLogService;
    private final Long upstreamId;
    private final Long agentId;

    public InboundAuditingToolCallback(
            ToolCallback delegate,
            McpCallLogService callLogService,
            Long upstreamId,
            Long agentId) {
        this.delegate = delegate;
        this.callLogService = callLogService;
        this.upstreamId = upstreamId;
        this.agentId = agentId;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            callLogService.recordIn(upstreamId, agentId, name, true, null,
                    (int) (System.currentTimeMillis() - start));
            return result;
        } catch (RuntimeException e) {
            callLogService.recordIn(upstreamId, agentId, name, false, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        }
    }
}
