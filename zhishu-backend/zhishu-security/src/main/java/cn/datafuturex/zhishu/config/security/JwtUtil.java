package cn.datafuturex.zhishu.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类
 *
 * @author YunQi Application Platform Team
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:zhishu-secret-key-for-jwt-token-generation-2024}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration; // 默认24小时

    /**
     * 生成 JWT Token
     *
     * @param username 用户名
     * @return JWT Token
     */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                // jti 保证同秒多次签发也不碰撞，避免登出拉黑后再次登录拿到同一串 Token
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            log.error("JWT Token 验证失败", e);
            return false;
        }
    }

    /**
     * 配置的 Token 有效期（毫秒）
     */
    public long getConfiguredExpirationMillis() {
        return expiration;
    }

    /**
     * 获取 Token 过期时间
     *
     * @param token JWT Token
     * @return 过期时间戳
     */
    public long getExpirationFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null && claims.getExpiration() != null ? claims.getExpiration().getTime() : 0;
    }

    /**
     * 获取 Token 签发时间
     *
     * @param token JWT Token
     * @return 签发时间戳，解析失败返回 0
     */
    public long getIssuedAtFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null && claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0;
    }

    /**
     * 从 Token 中获取 Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("解析 JWT Token 失败", e);
            return null;
        }
    }

    /**
     * 判断 Token 是否过期
     *
     * @param claims Claims
     * @return 是否过期
     */
    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * 获取签名密钥
     *
     * @return SecretKey
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
