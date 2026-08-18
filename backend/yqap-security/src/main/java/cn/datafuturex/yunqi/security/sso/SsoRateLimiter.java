package cn.datafuturex.yunqi.security.sso;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO 换票简易限流（按客户端 IP，滑动窗口）
 */
@Component
public class SsoRateLimiter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * @return true 表示允许；false 表示超限
     */
    public boolean tryAcquire(String clientIp, int windowSeconds, int maxRequests) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "unknown";
        }
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(windowSeconds, 1) * 1000L;
        int limit = Math.max(maxRequests, 1);

        Deque<Long> deque = windows.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
