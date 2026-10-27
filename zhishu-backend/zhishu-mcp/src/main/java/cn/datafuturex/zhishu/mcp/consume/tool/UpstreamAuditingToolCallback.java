package cn.datafuturex.zhishu.mcp.consume.tool;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.common.context.UserContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * consume 平面的上游 Tool 审计包装：记录「本平台调用上游 Tool」（{@code direction=CONSUME}）。
 */
public final class UpstreamAuditingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final McpCallLogService callLogService;
    private final Long upstreamId;
    private final Long agentId;

    public UpstreamAuditingToolCallback(
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
        // 审计写入是异步的，操作用户必须在当前线程取好再传递
        String actor = UserContext.getUserId();
        long start = System.currentTimeMillis();
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            callLogService.recordConsume(upstreamId, agentId, actor, name, true, null,
                    (int) (System.currentTimeMillis() - start));
            return result;
        } catch (RuntimeException e) {
            callLogService.recordConsume(upstreamId, agentId, actor, name, false, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        }
    }
}
