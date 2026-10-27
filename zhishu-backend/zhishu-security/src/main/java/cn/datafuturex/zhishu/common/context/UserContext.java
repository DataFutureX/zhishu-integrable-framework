package cn.datafuturex.zhishu.common.context;

/**
 * 当前请求用户上下文（由 JWT 远程校验成功后注入）。
 * <p>
 * 流式对话会切到 {@code boundedElastic} 等线程，需用 {@link #snapshot()}/{@link #restore(Snapshot)}
 * 显式传递，避免 Tool 执行时丢失用户身份。
 */
public final class UserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 请求上下文快照（含调用者 token，供内部模型网关调用透传身份）。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param token    当前请求的 Bearer token
     */
    public record Snapshot(String userId, String username, String token) {
        public boolean isEmpty() {
            return (userId == null || userId.isBlank()) && (username == null || username.isBlank());
        }
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 记录当前请求的 Bearer token（仅宿主内部使用）。
     *
     * @param token token 原文
     */
    public static void setToken(String token) {
        TOKEN.set(token);
    }

    public static String getToken() {
        return TOKEN.get();
    }

    public static Snapshot snapshot() {
        return new Snapshot(USER_ID.get(), USERNAME.get(), TOKEN.get());
    }

    public static void restore(Snapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }
        if (snapshot.userId() != null && !snapshot.userId().isBlank()) {
            USER_ID.set(snapshot.userId());
        }
        if (snapshot.username() != null && !snapshot.username().isBlank()) {
            USERNAME.set(snapshot.username());
        }
        if (snapshot.token() != null && !snapshot.token().isBlank()) {
            TOKEN.set(snapshot.token());
        }
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        TOKEN.remove();
    }
}
