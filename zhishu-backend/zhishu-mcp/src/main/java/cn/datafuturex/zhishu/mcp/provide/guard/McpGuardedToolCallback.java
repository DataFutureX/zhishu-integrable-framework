package cn.datafuturex.zhishu.mcp.provide.guard;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.provide.security.McpCallerContext;
import cn.datafuturex.zhishu.common.context.UserContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 对外 MCP 调用守卫：鉴权上下文 → 能力白名单 → RPM 限流 → 身份切换 → 审计。
 * <p>
 * provide 平面装配的<b>所有</b> Tool（含模块内置的系统 Tool）都必须经此包装，
 * 避免出现「未受白名单与审计约束的裸 Tool」。
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
            return "{\"error\":\"unauthenticated\"}";
        }
        // 审计写入是异步的，身份必须在当前线程从 caller 取好再传递
        String actor = actor(caller);
        Set<String> allowed = caller.allowedToolNames();
        if (allowed != null && !allowed.contains(toolName)) {
            callLogService.recordServe(caller.clientId(), actor, toolName, false, "工具未授权", 0);
            return "{\"error\":\"当前调用方未授权工具: " + toolName + "\"}";
        }
        if (!rateLimiter.tryAcquire(caller.rateLimitKey(), caller.rpmLimit())) {
            callLogService.recordServe(caller.clientId(), actor, toolName, false, "超过 RPM 限制", 0);
            return "{\"error\":\"超过调用频率限制\"}";
        }
        UserContext.Snapshot previous = UserContext.snapshot();
        UserContext.setUserId(caller.boundUserId() == null
                ? caller.username()
                : String.valueOf(caller.boundUserId()));
        UserContext.setUsername(caller.username());
        long start = System.currentTimeMillis();
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            callLogService.recordServe(caller.clientId(), actor, toolName, true, null,
                    (int) (System.currentTimeMillis() - start));
            return result;
        } catch (RuntimeException e) {
            callLogService.recordServe(caller.clientId(), actor, toolName, false, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        } finally {
            UserContext.restore(previous);
        }
    }

    /**
     * 审计归属用户：优先用 Client 绑定用户名，缺失时回落到绑定用户 id。
     */
    private static String actor(McpCallerContext.Caller caller) {
        if (StringUtils.hasText(caller.username())) {
            return caller.username();
        }
        return caller.boundUserId() == null ? null : String.valueOf(caller.boundUserId());
    }
}
