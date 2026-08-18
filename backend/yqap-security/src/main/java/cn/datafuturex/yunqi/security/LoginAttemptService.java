package cn.datafuturex.yunqi.security;

import cn.datafuturex.yunqi.api.dto.LoginSecuritySettingsDTO;
import cn.datafuturex.yunqi.api.spi.LoginSecuritySettingsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 登录重试限制服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCK_MINUTES = 3;

    private final LoginAttemptStore loginAttemptStore;
    private final LoginSecuritySettingsApi loginSecuritySettingsApi;

    public Optional<String> checkLocked(String username) {
        LoginSecuritySettingsDTO settings = normalize(loginSecuritySettingsApi.getSettings());
        if (!settings.enabled()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        return loginAttemptStore.get(username)
                .filter(record -> record.isLocked(now))
                .map(record -> buildLockMessage(record.lockUntilMillis(), now, settings.lockMinutes()));
    }

    public String recordFailure(String username) {
        LoginSecuritySettingsDTO settings = normalize(loginSecuritySettingsApi.getSettings());
        if (!settings.enabled()) {
            return "用户名或密码错误";
        }

        long now = System.currentTimeMillis();
        LoginAttemptStore.AttemptRecord current = loginAttemptStore.get(username).orElse(null);
        if (current != null && current.isLocked(now)) {
            return buildLockMessage(current.lockUntilMillis(), now, settings.lockMinutes());
        }

        int failCount = current == null || current.isLockExpired(now) ? 1 : current.failCount() + 1;
        long lockUntilMillis = 0;
        if (failCount >= settings.maxAttempts()) {
            lockUntilMillis = now + settings.lockMinutes() * 60_000L;
            log.warn("用户 {} 登录失败次数达到上限，锁定 {} 分钟", username, settings.lockMinutes());
        }

        loginAttemptStore.save(username, new LoginAttemptStore.AttemptRecord(failCount, lockUntilMillis));
        if (lockUntilMillis > now) {
            return buildLockMessage(lockUntilMillis, now, settings.lockMinutes());
        }
        return "用户名或密码错误";
    }

    public void clearFailures(String username) {
        loginAttemptStore.remove(username);
    }

    private LoginSecuritySettingsDTO normalize(LoginSecuritySettingsDTO settings) {
        if (settings == null) {
            return new LoginSecuritySettingsDTO(false, DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCK_MINUTES);
        }
        int maxAttempts = settings.maxAttempts() > 0 ? settings.maxAttempts() : DEFAULT_MAX_ATTEMPTS;
        int lockMinutes = settings.lockMinutes() > 0 ? settings.lockMinutes() : DEFAULT_LOCK_MINUTES;
        return new LoginSecuritySettingsDTO(settings.enabled(), maxAttempts, lockMinutes);
    }

    private String buildLockMessage(long lockUntilMillis, long now, int configuredLockMinutes) {
        long remainingMillis = Math.max(lockUntilMillis - now, 60_000L);
        long remainingMinutes = (remainingMillis + 59_999L) / 60_000L;
        return "登录失败次数过多，请" + Math.min(remainingMinutes, configuredLockMinutes) + "分钟后再试";
    }
}
