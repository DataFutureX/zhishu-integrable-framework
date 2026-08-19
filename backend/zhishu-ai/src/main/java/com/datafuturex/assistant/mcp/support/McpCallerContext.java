package com.datafuturex.assistant.mcp.support;

import java.util.Set;

/**
 * 当前对外 MCP 调用身份（请求线程）。
 */
public final class McpCallerContext {

    private static final ThreadLocal<Caller> HOLDER = new ThreadLocal<>();

    private McpCallerContext() {
    }

    public record Caller(
            Long clientId,
            String name,
            Long boundUserId,
            String boundUsername,
            Set<String> allowedToolNames,
            int rpmLimit
    ) {
    }

    public static void set(Caller caller) {
        HOLDER.set(caller);
    }

    public static Caller get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
