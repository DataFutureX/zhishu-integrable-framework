package cn.datafuturex.zhishu.mcp.provide.security;

import java.util.Set;

/**
 * 当前对外 MCP 调用身份（请求线程）。
 * <p>
 * 由 {@link McpEndpointAuthFilter} 在鉴权成功后写入，{@link cn.datafuturex.zhishu.mcp.provide.guard.McpGuardedToolCallback}
 * 读取；请求结束必须 {@link #clear()}，否则线程池复用会串号。
 */
public final class McpCallerContext {

    private static final ThreadLocal<Caller> HOLDER = new ThreadLocal<>();

    private McpCallerContext() {
    }

    /** 凭据类型 */
    public enum CallerType {

        /** 外部 Client API Key（{@code Bearer wxmcp_*}） */
        CLIENT_KEY,

        /** 平台登录态 JWT */
        PLATFORM_JWT
    }

    /**
     * 调用方快照。
     *
     * @param type             凭据类型
     * @param clientId         {@code ai_mcp_client.id}；JWT 调用为 null
     * @param name             展示名（Client 名称或用户名）
     * @param boundUserId      绑定用户 id（复用工程 ACL）
     * @param username         用户名
     * @param allowedToolNames 允许调用的 Tool 名集合
     * @param rpmLimit         每分钟调用上限
     */
    public record Caller(
            CallerType type,
            Long clientId,
            String name,
            Long boundUserId,
            String username,
            Set<String> allowedToolNames,
            int rpmLimit
    ) {

        /** 限流分桶键：Client 维度或用户维度 */
        public String rateLimitKey() {
            return type == CallerType.CLIENT_KEY ? "client:" + clientId : "user:" + username;
        }
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
