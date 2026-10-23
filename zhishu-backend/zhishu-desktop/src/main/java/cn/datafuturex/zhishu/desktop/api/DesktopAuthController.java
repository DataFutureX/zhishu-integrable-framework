package cn.datafuturex.zhishu.desktop.api;

import cn.datafuturex.zhishu.api.spi.AuthAuditApi;
import cn.datafuturex.zhishu.common.ClientIpUtils;
import cn.datafuturex.zhishu.common.Result;
import cn.datafuturex.zhishu.config.LoginCryptoProperties;
import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.desktop.dto.DesktopLoginRequestDTO;
import cn.datafuturex.zhishu.desktop.dto.DesktopLoginResultDTO;
import cn.datafuturex.zhishu.desktop.dto.DesktopResult;
import cn.datafuturex.zhishu.modules.dto.PublicKeyResponseDTO;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.service.UserService;
import cn.datafuturex.zhishu.security.DecryptedCredentials;
import cn.datafuturex.zhishu.security.LoginAttemptService;
import cn.datafuturex.zhishu.security.LoginCryptoService;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import cn.datafuturex.zhishu.security.sso.LoginChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 桌面端认证接口。
 * <p>
 * 流程：
 * <ol>
 *   <li>{@code GET /desktop/v1/auth/public-key} — 获取 RSA 公钥及 keyId</li>
 *   <li>{@code POST /desktop/v1/auth/login}      — 提交加密凭证，返回 JWT Token</li>
 *   <li>{@code POST /desktop/v1/auth/logout}     — 携带 Bearer Token 吊销会话</li>
 * </ol>
 * 与控制台 {@code /api/v1/auth} 的唯一区别是<b>不启用滑动验证码</b>。
 * 通过 {@code zhishu.desktop.enabled=false} 可整体关闭。
 */
@Tag(name = "Desktop Auth", description = "桌面端登录认证接口")
@Slf4j
@ConditionalOnProperty(name = "zhishu.desktop.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/desktop/v1/auth")
@RequiredArgsConstructor
public class DesktopAuthController {

    private final LoginCryptoService loginCryptoService;
    private final LoginCryptoProperties loginCryptoProperties;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final AuthAuditApi authAuditApi;
    private final TokenBlacklistService tokenBlacklistService;

    // ─── 获取登录公钥 ────────────────────────────────────────────────────────

    /**
     * 获取 RSA 登录公钥（无需鉴权）。
     * <p>
     * 桌面端在发起登录前调用，获取一次性公钥及对应的 {@code keyId}，
     * 随后用公钥加密用户名和密码，连同 {@code keyId} 一并传入登录接口。
     */
    @Operation(summary = "获取登录公钥",
            description = "返回 RSA 公钥（PEM）与 keyId，桌面端据此加密用户名和密码。")
    @GetMapping("/public-key")
    public DesktopResult<PublicKeyResponseDTO> getPublicKey() {
        PublicKeyResponseDTO dto = loginCryptoService.createPublicKey();
        return DesktopResult.ok(dto);
    }

    // ─── 桌面端登录 ──────────────────────────────────────────────────────────

    /**
     * 桌面端登录（无需 X-Desktop-Key，不校验滑动验证码）。
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li><b>RSA 加密</b>（{@code yunqi.login-crypto.enabled=true}）：
     *       username / password 为 Base64 密文，keyId 必填</li>
     *   <li><b>明文</b>（加密关闭时）：直接传明文用户名与密码</li>
     * </ul>
     * 登录成功后返回 JWT Token 及用户基本信息，桌面端后续请求携带
     * {@code Authorization: Bearer <token>} 即可访问需鉴权的接口。
     */
    @Operation(summary = "桌面端登录",
            description = "提交 RSA 加密（或明文）的用户名、密码与 keyId，验证通过后返回 JWT Token。不校验滑动验证码。")
    @PostMapping("/login")
    public DesktopResult<DesktopLoginResultDTO> login(
            @RequestBody DesktopLoginRequestDTO request,
            HttpServletRequest httpRequest) {

        String clientIp = ClientIpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 1. 解密凭证
        String username;
        String password;
        try {
            DecryptedCredentials credentials = resolveCredentials(request);
            username = credentials.username();
            password = credentials.password();
        } catch (IllegalArgumentException e) {
            log.warn("[Desktop登录失败] 凭证解密异常: {}", e.getMessage());
            authAuditApi.recordLogin(null, clientIp, userAgent, false,
                    "登录凭证无效或已过期", LoginChannel.DESKTOP);
            return DesktopResult.error(400, "登录凭证无效或已过期");
        }

        // 2. 检查账号锁定状态
        Optional<String> lockMessage = loginAttemptService.checkLocked(username);
        if (lockMessage.isPresent()) {
            log.warn("[Desktop登录失败] 用户锁定: username={}", username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false,
                    lockMessage.get(), LoginChannel.DESKTOP);
            return DesktopResult.error(423, lockMessage.get());
        }

        // 3. 查询用户
        UserEntity user = userService.findByUsername(username).orElse(null);
        if (user == null) {
            String msg = loginAttemptService.recordFailure(username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, msg, LoginChannel.DESKTOP);
            return DesktopResult.error(401, msg);
        }

        // 4. 账号状态校验
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("[Desktop登录失败] 账号禁用: username={}", username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false,
                    "账号已被禁用", LoginChannel.DESKTOP);
            return DesktopResult.error(403, "账号已被禁用，请联系管理员");
        }

        // 5. 密码比对
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[Desktop登录失败] 密码错误: username={}", username);
            String msg = loginAttemptService.recordFailure(username);
            authAuditApi.recordLogin(username, clientIp, userAgent, false, msg, LoginChannel.DESKTOP);
            return DesktopResult.error(401, msg);
        }

        // 6. 登录成功，签发 JWT
        loginAttemptService.clearFailures(username);
        String token = jwtUtil.generateToken(user.getUsername());
        long expiration = jwtUtil.getExpirationFromToken(token);
        if (expiration <= 0) {
            expiration = System.currentTimeMillis() + jwtUtil.getConfiguredExpirationMillis();
        }

        log.info("[Desktop登录成功] username={}", user.getUsername());
        authAuditApi.recordLogin(user.getUsername(), clientIp, userAgent, true, null, LoginChannel.DESKTOP);

        DesktopLoginResultDTO result = new DesktopLoginResultDTO(
                token,
                expiration,
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole()
        );
        return DesktopResult.ok(result);
    }

    // ─── 桌面端退出登录 ──────────────────────────────────────────────────────

    /**
     * 桌面端退出登录。
     * <p>
     * 从请求头 {@code Authorization: Bearer <token>} 中提取当前 JWT，
     * 将其加入黑名单使其立即失效。无需 {@code X-Desktop-Key}。
     */
    @Operation(summary = "退出登录",
            description = "将当前 JWT Token 加入黑名单使其立即失效，后续使用该 Token 的请求将被拒绝。")
    @PostMapping("/logout")
    public DesktopResult<Void> logout(HttpServletRequest httpRequest) {
        String token = extractBearerToken(httpRequest);
        if (StringUtils.hasText(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            tokenBlacklistService.revokeToken(token);
            log.info("[Desktop退出登录] username={}", username);
        } else {
            log.debug("[Desktop退出登录] 未携带有效 Bearer Token，跳过吊销");
        }
        return DesktopResult.ok(null);
    }

    // ─── 私有辅助方法 ────────────────────────────────────────────────────────

    private DecryptedCredentials resolveCredentials(DesktopLoginRequestDTO request) {
        if (loginCryptoProperties.isEnabled()) {
            // RSA 加密模式：解密用户名和密码
            return loginCryptoService.decryptAndConsume(
                    request.keyId(), request.username(), request.password());
        }
        // 明文模式
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        return new DecryptedCredentials(request.username(), request.password());
    }

    private String extractBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
