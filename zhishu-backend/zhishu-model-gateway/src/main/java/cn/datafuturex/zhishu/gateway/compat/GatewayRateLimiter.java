package cn.datafuturex.zhishu.gateway.compat;

import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量限流：按「用户 × 模型」计每秒请求数（进程内，单实例语义）。
 * <p>
 * 默认关闭（{@code zhishu.gateway.quota.rate-limit.enabled=false}）；
 * 开启后超限返回 429 {@code rate_limit_exceeded}，由客户端决定是否重试。
 */
@Component
@RequiredArgsConstructor
public class GatewayRateLimiter {

    private final GatewayProperties properties;

    /** key -> 当前秒窗口计数；写入 1 秒后自动过期，近似固定窗口 */
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofSeconds(1))
            .build();

    /**
     * 尝试获取一次配额。
     *
     * @param username 用户名
     * @param modelId  网关模型 id
     * @return true=放行；false=超限
     */
    public boolean tryAcquire(String username, String modelId) {
        var config = properties.getQuota().getRateLimit();
        if (config == null || !config.isEnabled()) {
            return true;
        }
        int qps = Math.max(1, config.getQps());
        AtomicInteger counter = counters.get(username + '|' + modelId, key -> new AtomicInteger());
        return counter.incrementAndGet() <= qps;
    }
}
