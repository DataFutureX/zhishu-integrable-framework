package cn.datafuturex.zhishu.mcp.provide.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RPM 滑动窗口限流：按分桶键独立计数。
 */
class McpRateLimiterTest {

    @Test
    @DisplayName("超过上限即拒绝，且不同桶互不影响")
    void enforcesLimitPerBucket() {
        McpRateLimiter limiter = new McpRateLimiter();

        assertThat(limiter.tryAcquire("client:1", 2)).isTrue();
        assertThat(limiter.tryAcquire("client:1", 2)).isTrue();
        assertThat(limiter.tryAcquire("client:1", 2)).isFalse();
        // 另一个调用方不受影响
        assertThat(limiter.tryAcquire("user:admin", 2)).isTrue();
    }

    @Test
    @DisplayName("空键拒绝（视为未认证）")
    void blankKeyRejected() {
        McpRateLimiter limiter = new McpRateLimiter();

        assertThat(limiter.tryAcquire(null, 60)).isFalse();
        assertThat(limiter.tryAcquire("  ", 60)).isFalse();
    }

    @Test
    @DisplayName("非正数上限回落到 60")
    void nonPositiveLimitFallsBackToSixty() {
        McpRateLimiter limiter = new McpRateLimiter();

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("client:9", 0)).isTrue();
        }
        assertThat(limiter.tryAcquire("client:9", 0)).isFalse();
    }
}
