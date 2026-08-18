package cn.datafuturex.yunqi.security;

import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录 RSA 私钥内存存储（单节点部署）
 */
@Component
public class LoginKeyStore {

    private final Map<String, KeySession> sessions = new ConcurrentHashMap<>();

    /**
     * 保存私钥会话
     */
    public void save(String keyId, PrivateKey privateKey, long expireAt) {
        cleanupExpired();
        sessions.put(keyId, new KeySession(privateKey, expireAt));
    }

    /**
     * 获取并移除私钥（一次性使用）
     */
    public Optional<PrivateKey> consumePrivateKey(String keyId) {
        cleanupExpired();
        KeySession session = sessions.remove(keyId);
        if (session == null || session.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(session.privateKey());
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record KeySession(PrivateKey privateKey, long expireAt) {
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now > expireAt;
        }
    }
}
