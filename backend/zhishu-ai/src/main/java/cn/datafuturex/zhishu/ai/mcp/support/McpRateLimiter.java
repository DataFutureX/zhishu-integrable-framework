package cn.datafuturex.zhishu.ai.mcp.support;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpRateLimiter {

    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long clientId, int rpmLimit) {
        if (clientId == null) {
            return false;
        }
        int limit = rpmLimit <= 0 ? 60 : rpmLimit;
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        Deque<Long> q = windows.computeIfAbsent(clientId, id -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
