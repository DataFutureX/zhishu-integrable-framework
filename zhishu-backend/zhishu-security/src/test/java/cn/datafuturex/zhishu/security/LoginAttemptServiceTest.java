package cn.datafuturex.zhishu.security;

import cn.datafuturex.zhishu.api.dto.LoginSecuritySettingsDTO;
import cn.datafuturex.zhishu.api.spi.LoginSecuritySettingsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录重试限制服务单元测试
 */
class LoginAttemptServiceTest {

    private LoginAttemptStore loginAttemptStore;
    private LoginSecuritySettingsApi loginSecuritySettingsApi;
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptStore = new LoginAttemptStore();
        loginSecuritySettingsApi = mock(LoginSecuritySettingsApi.class);
        loginAttemptService = new LoginAttemptService(loginAttemptStore, loginSecuritySettingsApi);
    }

    @Test
    @DisplayName("未开启限制时不应锁定用户")
    void testDisabledNoLock() {
        when(loginSecuritySettingsApi.getSettings())
                .thenReturn(new LoginSecuritySettingsDTO(false, 5, 3));

        assertTrue(loginAttemptService.checkLocked("admin").isEmpty());
        assertEquals("用户名或密码错误", loginAttemptService.recordFailure("admin"));
        assertTrue(loginAttemptService.checkLocked("admin").isEmpty());
    }

    @Test
    @DisplayName("失败次数达到上限后应锁定用户")
    void testLockAfterMaxFailures() {
        when(loginSecuritySettingsApi.getSettings())
                .thenReturn(new LoginSecuritySettingsDTO(true, 3, 3));

        assertEquals("用户名或密码错误", loginAttemptService.recordFailure("admin"));
        assertEquals("用户名或密码错误", loginAttemptService.recordFailure("admin"));
        String message = loginAttemptService.recordFailure("admin");

        assertTrue(message.contains("登录失败次数过多"));
        assertTrue(loginAttemptService.checkLocked("admin").isPresent());
    }

    @Test
    @DisplayName("登录成功后应清除失败记录")
    void testClearFailures() {
        when(loginSecuritySettingsApi.getSettings())
                .thenReturn(new LoginSecuritySettingsDTO(true, 5, 3));

        loginAttemptService.recordFailure("admin");
        loginAttemptService.clearFailures("admin");

        assertTrue(loginAttemptService.checkLocked("admin").isEmpty());
    }
}
