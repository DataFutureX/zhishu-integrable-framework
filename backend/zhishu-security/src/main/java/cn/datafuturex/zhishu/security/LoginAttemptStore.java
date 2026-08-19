package cn.datafuturex.zhishu.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数内存存储（单节点部署）
 */
@Component
public class LoginAttemptStore {

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * 获取登录尝试记录
     */
    public Optional<AttemptRecord> get(String username) {
        cleanupExpiredLocks();
        return Optional.ofNullable(attempts.get(username));
    }

    /**
     * 保存登录尝试记录
     */
    public void save(String username, AttemptRecord record) {
        cleanupExpiredLocks();
        attempts.put(username, record);
    }

    /**
     * 移除登录尝试记录
     */
    public void remove(String username) {
        attempts.remove(username);
    }

    private void cleanupExpiredLocks() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue().isLockExpired(now));
    }

    /**
     * 登录尝试记录
     */
    public record AttemptRecord(int failCount, long lockUntilMillis) {

        boolean isLocked(long now) {
            return lockUntilMillis > now;
        }

        boolean isLockExpired(long now) {
            return lockUntilMillis > 0 && lockUntilMillis <= now && failCount > 0;
        }
    }
}
