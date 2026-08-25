package cn.datafuturex.zhishu.biz.systemconfig.spi;

import cn.datafuturex.zhishu.api.dto.LoginSecuritySettingsDTO;
import cn.datafuturex.zhishu.api.spi.LoginSecuritySettingsApi;
import cn.datafuturex.zhishu.biz.systemconfig.service.SystemConfigService;
import cn.datafuturex.zhishu.biz.systemconfig.vo.SystemConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录安全策略查询适配（供 security 模块调用）
 */
@Service
@RequiredArgsConstructor
public class LoginSecuritySettingsApiImpl implements LoginSecuritySettingsApi {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCK_MINUTES = 3;

    private final SystemConfigService systemConfigService;

    @Override
    public LoginSecuritySettingsDTO getSettings() {
        SystemConfigVO config = systemConfigService.getConfig();
        boolean enabled = Boolean.TRUE.equals(config.loginRetryLimitEnabled());
        int maxAttempts = config.loginMaxRetryAttempts() != null
                ? config.loginMaxRetryAttempts() : DEFAULT_MAX_ATTEMPTS;
        int lockMinutes = config.loginLockMinutes() != null
                ? config.loginLockMinutes() : DEFAULT_LOCK_MINUTES;
        return new LoginSecuritySettingsDTO(enabled, maxAttempts, lockMinutes);
    }
}
