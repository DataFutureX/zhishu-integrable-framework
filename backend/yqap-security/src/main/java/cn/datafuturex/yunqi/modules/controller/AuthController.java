package cn.datafuturex.yunqi.modules.controller;

import cn.datafuturex.yunqi.api.spi.AuthAuditApi;
import cn.datafuturex.yunqi.common.ClientIpUtils;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.config.CaptchaProperties;
import cn.datafuturex.yunqi.config.LoginCryptoProperties;
import cn.datafuturex.yunqi.config.security.JwtUtil;
import cn.datafuturex.yunqi.modules.dto.CaptchaResponseDTO;
import cn.datafuturex.yunqi.modules.dto.CaptchaVerifyRequestDTO;
import cn.datafuturex.yunqi.modules.dto.CaptchaVerifyResponseDTO;
import cn.datafuturex.yunqi.modules.dto.LoginRequestDTO;
import cn.datafuturex.yunqi.modules.dto.LoginResponseDTO;
import cn.datafuturex.yunqi.modules.dto.PublicKeyResponseDTO;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.service.CaptchaService;
import cn.datafuturex.yunqi.modules.service.UserService;
import cn.datafuturex.yunqi.security.DecryptedCredentials;
import cn.datafuturex.yunqi.security.LoginAttemptService;
import cn.datafuturex.yunqi.security.LoginCryptoService;
import cn.datafuturex.yunqi.security.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 认证控制器
 *
 * @author YunQi Application Platform Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录认证相关接口")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final CaptchaService captchaService;
    private final CaptchaProperties captchaProperties;
    private final LoginCryptoService loginCryptoService;
    private final LoginCryptoProperties loginCryptoProperties;
    private final LoginAttemptService loginAttemptService;
    private final AuthAuditApi authAuditApi;
    private final TokenBlacklistService tokenBlacklistService;

    @GetMapping("/public-key")
    @Operation(summary = "获取登录公钥", description = "返回 RSA 公钥，前端用于加密用户名和密码")
    public Result<PublicKeyResponseDTO> getPublicKey() {
        return Result.success(loginCryptoService.createPublicKey());
    }

    @GetMapping("/captcha")
    @Operation(summary = "获取滑动验证码", description = "返回背景图、滑块图及验证码标识")
    public Result<CaptchaResponseDTO> getCaptcha() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/captcha/verify")
    @Operation(summary = "校验滑动验证码", description = "校验滑动位置，成功后返回 captchaToken 供登录使用")
    public Result<CaptchaVerifyResponseDTO> verifyCaptcha(@RequestBody CaptchaVerifyRequestDTO request) {
        if (request.captchaId() == null || request.slideX() == null) {
            return Result.error("验证码参数不完整");
        }
        return Result.success(captchaService.verify(request.captchaId(), request.slideX()));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "根据 RSA 加密的用户名、密码及滑动验证码令牌获取 JWT Token")
    public Result<LoginResponseDTO> login(@RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        String clientIp = ClientIpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        if (captchaProperties.isEnabled()) {
            if (request.captchaToken() == null || !captchaService.consumeToken(request.captchaToken())) {
                log.warn("登录失败: 滑动验证码无效");
                authAuditApi.recordLogin(null, clientIp, userAgent, false, "请先完成滑动验证");
                return Result.error("请先完成滑动验证");
            }
        }

        String username;
        String password;
        try {
            var credentials = resolveCredentials(request);
            username = credentials.username();
            password = credentials.password();
        } catch (IllegalArgumentException e) {
            log.warn("登录失败: {}", e.getMessage());
            authAuditApi.recordLogin(null, clientIp, userAgent, false, "登录凭证无效或已过期");
            return Result.error("登录凭证无效或已过期");
        }

        Optional<String> lockMessage = loginAttemptService.checkLocked(username);
        if (lockMessage.isPresent()) {
            log.warn("登录失败: 用户 {} 处于锁定状态", username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, lockMessage.get());
            return Result.error(lockMessage.get());
        }

        UserEntity user = userService.findByUsername(username)
                .orElse(null);

        if (user == null) {
            log.warn("登录失败: 用户不存在, username={}", username);
            String failureMessage = loginAttemptService.recordFailure(username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, failureMessage);
            return Result.error(failureMessage);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败: 用户已禁用, username={}", username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, "账号已被禁用");
            return Result.error("账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("登录失败: 密码错误, username={}", username);
            String failureMessage = loginAttemptService.recordFailure(username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, failureMessage);
            return Result.error(failureMessage);
        }

        loginAttemptService.clearFailures(username);

        String token = jwtUtil.generateToken(user.getUsername());
        long expiration = jwtUtil.getExpirationFromToken(token);
        if (expiration <= 0) {
            expiration = System.currentTimeMillis() + jwtUtil.getConfiguredExpirationMillis();
        }

        log.info("用户 {} 登录成功", user.getUsername());
        authAuditApi.recordLogin(user.getUsername(), clientIp, userAgent, true, null);

        return Result.success(new LoginResponseDTO(token, expiration));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "将当前 Token 加入黑名单使其立即失效")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        String token = extractBearerToken(httpRequest);
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.revokeToken(token);
            log.info("用户退出登录");
        }
        return Result.success();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private DecryptedCredentials resolveCredentials(LoginRequestDTO request) {
        if (loginCryptoProperties.isEnabled()) {
            return loginCryptoService.decryptAndConsume(
                    request.keyId(), request.username(), request.password());
        }
        if (request.username() == null || request.password() == null) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        return new DecryptedCredentials(request.username(), request.password());
    }
}
