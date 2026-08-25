package cn.datafuturex.zhishu.ai.mcp.support;

import cn.datafuturex.zhishu.ai.mcp.service.McpCallLogService;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

/**
 * 对外 MCP 调用守卫：鉴权上下文、能力白名单、限流、审计。
 */
public final class McpGuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final McpCallLogService callLogService;
    private final McpRateLimiter rateLimiter;

    public McpGuardedToolCallback(
            ToolCallback delegate,
            McpCallLogService callLogService,
            McpRateLimiter rateLimiter) {
        this.delegate = delegate;
        this.callLogService = callLogService;
        this.rateLimiter = rateLimiter;
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
        McpCallerContext.Caller caller = McpCallerContext.get();
        String toolName = delegate.getToolDefinition().name();
        if (caller == null) {
            return "{\"error\":\"未认证的 MCP 调用\"}";
        }
        Set<String> allowed = caller.allowedToolNames();
        if (allowed != null && !allowed.contains(toolName)) {
            callLogService.recordOut(caller.clientId(), toolName, false, "工具未授权", 0);
            return "{\"error\":\"当前 MCP Client 未授权工具: " + toolName + "\"}";
        }
        if (!rateLimiter.tryAcquire(caller.clientId(), caller.rpmLimit())) {
            callLogService.recordOut(caller.clientId(), toolName, false, "超过 RPM 限制", 0);
            return "{\"error\":\"超过调用频率限制\"}";
        }
        UserContext.Snapshot previous = UserContext.snapshot();
        UserContext.setUserId(caller.boundUserId() == null ? null : String.valueOf(caller.boundUserId()));
        UserContext.setUsername(caller.boundUsername());
        long start = System.currentTimeMillis();
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            callLogService.recordOut(caller.clientId(), toolName, true, null,
                    (int) (System.currentTimeMillis() - start));
            return result;
        } catch (RuntimeException e) {
            callLogService.recordOut(caller.clientId(), toolName, false, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        } finally {
            UserContext.restore(previous);
        }
    }
}
