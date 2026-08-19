package cn.datafuturex.zhishu.security.sso;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO Ticket jti 一次性消费存储（单节点内存；多实例需外置 Redis）
 */
@Component
public class SsoJtiStore {

    private final Map<String, Long> usedJtis = new ConcurrentHashMap<>();

    /**
     * 原子消费 jti：首次写入返回 true，已存在返回 false
     *
     * @param iss           伙伴 issuer
     * @param jti           ticket jti
     * @param ttlSeconds    记录存活秒数
     */
    public boolean tryConsume(String iss, String jti, long ttlSeconds) {
        cleanup();
        String key = iss + ":" + jti;
        long expireAt = System.currentTimeMillis() + Math.max(ttlSeconds, 1) * 1000L;
        Long existing = usedJtis.putIfAbsent(key, expireAt);
        return existing == null;
    }

    /** 测试用：清空 */
    void clear() {
        usedJtis.clear();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        usedJtis.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
