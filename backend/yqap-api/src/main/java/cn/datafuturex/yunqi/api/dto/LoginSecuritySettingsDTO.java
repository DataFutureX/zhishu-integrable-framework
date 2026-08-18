package cn.datafuturex.yunqi.api.dto;

/**
 * 登录安全策略快照
 */
public record LoginSecuritySettingsDTO(
        boolean enabled,
        int maxAttempts,
        int lockMinutes
) {
}
