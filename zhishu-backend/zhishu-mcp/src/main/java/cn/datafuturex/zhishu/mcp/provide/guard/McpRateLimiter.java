package cn.datafuturex.zhishu.mcp.provide.guard;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对外 MCP 调用的 RPM 限流（60 秒滑动窗口）。
 * <p>
 * 分桶键来自 {@link cn.datafuturex.zhishu.mcp.provide.security.McpCallerContext.Caller#rateLimitKey()}：
 * Client Key 用 {@code client:{id}}，平台 JWT 用 {@code user:{username}}。
 * 状态仅存进程内存，多实例部署时实际上限会随实例数放大。
 */
@Component
public class McpRateLimiter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * 尝试占用一个窗口额度。
     *
     * @param rateLimitKey 分桶键，空则拒绝（视为未认证）
     * @param rpmLimit     每分钟上限，&lt;=0 回落到 60
     * @return true 表示未超限
     */
    public boolean tryAcquire(String rateLimitKey, int rpmLimit) {
        if (rateLimitKey == null || rateLimitKey.isBlank()) {
            return false;
        }
        int limit = rpmLimit <= 0 ? 60 : rpmLimit;
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        Deque<Long> queue = windows.computeIfAbsent(rateLimitKey, key -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
