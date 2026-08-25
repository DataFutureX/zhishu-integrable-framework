package cn.datafuturex.zhishu.captcha;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑动验证码内存存储（单节点部署）
 */
@Component
public class CaptchaStore {

    private final Map<String, CaptchaSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, VerifyToken> verifyTokens = new ConcurrentHashMap<>();

    /**
     * 保存验证码会话
     */
    public void saveSession(String captchaId, int targetX, long expireAt) {
        cleanupExpired();
        sessions.put(captchaId, new CaptchaSession(targetX, expireAt));
    }

    /**
     * 获取并移除验证码会话（一次性使用）
     *
     * @return 目标 X 轴位置
     */
    public Optional<Integer> consumeSessionTargetX(String captchaId) {
        cleanupExpired();
        CaptchaSession session = sessions.remove(captchaId);
        if (session == null || session.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(session.targetX());
    }

    /**
     * 生成并保存验证通过令牌
     */
    public String createVerifyToken(long expireAt) {
        cleanupExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        verifyTokens.put(token, new VerifyToken(expireAt));
        return token;
    }

    /**
     * 消费验证通过令牌（一次性使用）
     */
    public boolean consumeVerifyToken(String token) {
        cleanupExpired();
        VerifyToken verifyToken = verifyTokens.remove(token);
        return verifyToken != null && !verifyToken.isExpired();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        verifyTokens.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record CaptchaSession(int targetX, long expireAt) {
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now > expireAt;
        }
    }

    private record VerifyToken(long expireAt) {
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now > expireAt;
        }
    }
}
