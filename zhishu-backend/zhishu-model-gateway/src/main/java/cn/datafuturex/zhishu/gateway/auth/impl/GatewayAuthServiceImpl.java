package cn.datafuturex.zhishu.gateway.auth.impl;

import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.gateway.auth.GatewayAuthService;
import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.service.UserService;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * 网关鉴权实现：知枢 JWT 为主，静态 token 为兜底（无门禁部署）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayAuthServiceImpl implements GatewayAuthService {

    private static final String HEADER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final GatewayProperties properties;

    /** 用户主体缓存：避免热路径每次查库 */
    private final Cache<String, GatewayPrincipal> principalCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    /**
     * {@inheritDoc}
     */
    @Override
    public GatewayPrincipal authenticate(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            throw GatewayException.invalidApiKey("缺少 Authorization: Bearer <token>");
        }
        if (isStaticToken(bearerToken)) {
            return new GatewayPrincipal("0", properties.getAuth().getStaticUsername(), "SERVICE");
        }
        if (!jwtUtil.validateToken(bearerToken)) {
            throw GatewayException.invalidApiKey("Token 无效或已过期");
        }
        if (tokenBlacklistService.isRevoked(bearerToken)) {
            throw GatewayException.tokenRevoked("Token 已吊销，请重新登录");
        }
        String username = jwtUtil.getUsernameFromToken(bearerToken);
        if (!StringUtils.hasText(username)) {
            throw GatewayException.invalidApiKey("Token 中缺少用户标识");
        }
        GatewayPrincipal cached = principalCache.getIfPresent(username);
        if (cached != null) {
            return cached;
        }
        Optional<UserEntity> found = userService.findByUsername(username);
        if (found.isEmpty()) {
            throw GatewayException.accountDisabled("账号不存在或已禁用: " + username);
        }
        UserEntity user = found.get();
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw GatewayException.accountDisabled("账号不存在或已禁用: " + username);
        }
        GatewayPrincipal principal = new GatewayPrincipal(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRole());
        principalCache.put(username, principal);
        log.debug("[网关鉴权] 通过 username={}", username);
        return principal;
    }

    /**
     * 清除主体缓存（用户信息变更后由管理端调用）。
     *
     * @param username 用户名
     */
    public void evict(String username) {
        if (StringUtils.hasText(username)) {
            principalCache.invalidate(username);
        }
    }

    private boolean isStaticToken(String token) {
        String configured = properties.getAuth().getStaticToken();
        if (!"static".equalsIgnoreCase(properties.getAuth().getMode()) || !StringUtils.hasText(configured)) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断请求头是否以 Bearer 开头。
     *
     * @param header Authorization 头
     * @return 是则返回 token
     */
    public static String stripBearer(String header) {
        if (StringUtils.hasText(header) && header.startsWith(HEADER_PREFIX)) {
            return header.substring(HEADER_PREFIX.length()).trim();
        }
        return null;
    }
}
