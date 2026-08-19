package cn.datafuturex.zhishu.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 黑名单内存存储（单节点；按 Token 吊销 + 按用户签发时间截止）
 */
@Component
public class TokenBlacklistStore {

    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();
    private final Map<String, Long> userInvalidBefore = new ConcurrentHashMap<>();

    public void revokeToken(String tokenHash, long expireAtMillis) {
        cleanup();
        revokedTokens.put(tokenHash, expireAtMillis);
    }

    public boolean isTokenRevoked(String tokenHash) {
        cleanup();
        Long expireAt = revokedTokens.get(tokenHash);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    public void invalidateUserTokens(String username, long invalidBeforeMillis) {
        cleanup();
        userInvalidBefore.merge(username, invalidBeforeMillis, Math::max);
    }

    public Long getUserInvalidBefore(String username) {
        cleanup();
        return userInvalidBefore.get(username);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(e -> e.getValue() <= now);
        userInvalidBefore.entrySet().removeIf(e -> e.getValue() + 86400000L < now);
    }
}
