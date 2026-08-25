package cn.datafuturex.zhishu.modules.controller;

import cn.datafuturex.zhishu.api.spi.AuthAuditApi;
import cn.datafuturex.zhishu.config.CaptchaProperties;
import cn.datafuturex.zhishu.config.LoginCryptoProperties;
import cn.datafuturex.zhishu.config.SecurityProperties;
import cn.datafuturex.zhishu.config.security.CommittedResponseSecurityExceptionFilter;
import cn.datafuturex.zhishu.config.security.JwtAuthenticationFilter;
import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.config.security.RestAccessDeniedHandler;
import cn.datafuturex.zhishu.config.security.RestAuthenticationEntryPoint;
import cn.datafuturex.zhishu.config.security.SecurityConfig;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.service.CaptchaService;
import cn.datafuturex.zhishu.modules.service.PermissionService;
import cn.datafuturex.zhishu.modules.service.UserService;
import cn.datafuturex.zhishu.security.LoginAttemptService;
import cn.datafuturex.zhishu.security.LoginCryptoService;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import cn.datafuturex.zhishu.config.SsoProperties;
import cn.datafuturex.zhishu.security.sso.SsoExchangeService;
import cn.datafuturex.zhishu.security.sso.SsoRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证控制器单元测试
 *
 * @author YunQi Application Platform Team
 */
@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityProperties.class,
        CommittedResponseSecurityExceptionFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CaptchaService captchaService;

    @MockitoBean
    private CaptchaProperties captchaProperties;

    @MockitoBean
    private LoginCryptoService loginCryptoService;

    @MockitoBean
    private LoginCryptoProperties loginCryptoProperties;

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private AuthAuditApi authAuditApi;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private SsoExchangeService ssoExchangeService;

    @MockitoBean
    private SsoProperties ssoProperties;

    @MockitoBean
    private SsoRateLimiter ssoRateLimiter;

    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new UserEntity();
        adminUser.setUsername("admin");
        adminUser.setPassword("encoded_password");
        adminUser.setStatus(1);

        when(captchaProperties.isEnabled()).thenReturn(false);
        when(loginCryptoProperties.isEnabled()).thenReturn(false);
        when(loginAttemptService.checkLocked(anyString())).thenReturn(Optional.empty());
        when(loginAttemptService.recordFailure(anyString())).thenReturn("用户名或密码错误");
        when(userService.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(userService.findByUsername("wrong_user")).thenReturn(Optional.empty());
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("测试用户登录成功 - 返回 JWT Token")
    void testLoginSuccess() throws Exception {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        String mockToken = "mock.jwt.token.here";
        when(jwtUtil.generateToken("admin")).thenReturn(mockToken);
        when(jwtUtil.getExpirationFromToken(mockToken)).thenReturn(System.currentTimeMillis() + 86400000L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.expiration").exists());
    }

    @Test
    @DisplayName("测试登录失败 - 用户已禁用")
    void testLoginFailureWhenDisabled() throws Exception {
        adminUser.setStatus(0);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("测试用户登录失败 - 用户名错误")
    void testLoginFailureWithWrongUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wrong_user\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("测试用户登录失败 - 密码错误")
    void testLoginFailureWithWrongPassword() throws Exception {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong_password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("测试登录请求缺少密码")
    void testLoginWithInvalidRequestFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录凭证无效或已过期"));
    }

    @Test
    @DisplayName("测试登录失败 - 未通过滑动验证码")
    void testLoginFailureWithoutCaptchaToken() throws Exception {
        when(captchaProperties.isEnabled()).thenReturn(true);
        when(captchaService.consumeToken(anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请先完成滑动验证"));
    }

    @Test
    @DisplayName("测试登录失败 - 账户被锁定")
    void testLoginFailureWhenLocked() throws Exception {
        when(loginAttemptService.checkLocked("admin"))
                .thenReturn(Optional.of("登录失败次数过多，请3分钟后再试"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请3分钟后再试"));
    }
}
