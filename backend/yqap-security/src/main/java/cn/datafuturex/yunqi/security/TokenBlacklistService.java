package cn.datafuturex.yunqi.security;

import cn.datafuturex.yunqi.config.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Token 吊销服务：登出按 Token 拉黑；改密/禁用按用户签发时间截止
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistStore store;
    private final JwtUtil jwtUtil;

    /**
     * 吊销当前 Token（登出）
     */
    public void revokeToken(String token) {
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            return;
        }
        long expireAt = jwtUtil.getExpirationFromToken(token);
        if (expireAt <= 0) {
            return;
        }
        store.revokeToken(hash(token), expireAt);
        log.debug("Token 已加入黑名单");
    }

    /**
     * 使某用户在此之前签发的全部 Token 失效（改密、禁用、重置密码）
     */
    public void invalidateUserTokens(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        long now = System.currentTimeMillis();
        store.invalidateUserTokens(username, now);
        log.info("用户 Token 已全部失效: username={}", username);
    }

    /**
     * 判断 Token 是否已被吊销或因用户级失效而无效
     */
    public boolean isRevoked(String token) {
        if (!StringUtils.hasText(token)) {
            return true;
        }
        if (store.isTokenRevoked(hash(token))) {
            return true;
        }
        String username = jwtUtil.getUsernameFromToken(token);
        if (!StringUtils.hasText(username)) {
            return true;
        }
        Long invalidBefore = store.getUserInvalidBefore(username);
        if (invalidBefore == null) {
            return false;
        }
        long issuedAt = jwtUtil.getIssuedAtFromToken(token);
        // 严格小于：同毫秒/同秒重登签发的新 Token 仍可用
        return issuedAt > 0 && issuedAt < invalidBefore;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(token.hashCode());
        }
    }
}
