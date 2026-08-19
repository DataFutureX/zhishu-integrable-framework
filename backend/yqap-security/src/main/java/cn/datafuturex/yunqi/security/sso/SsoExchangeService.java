package cn.datafuturex.yunqi.security.sso;

import cn.datafuturex.yunqi.config.SsoProperties;
import cn.datafuturex.yunqi.config.security.JwtUtil;
import cn.datafuturex.yunqi.modules.dto.SsoExchangeResponseDTO;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * 伙伴 Ticket 验签换票：校验后签发云起业务 JWT
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoExchangeService {

    private final SsoProperties ssoProperties;
    private final SsoPublicKeyResolver publicKeyResolver;
    private final SsoJtiStore jtiStore;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** 仅用于未验签前解析 Header/Payload；不依赖 Spring Jackson Bean */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SsoExchangeResult exchange(String ticket, String redirect) {
        if (!ssoProperties.isEnabled()) {
            throw new SsoException(403, "未开启单点登录");
        }
        if (!StringUtils.hasText(ticket)) {
            throw new SsoException(400, "票据不能为空");
        }

        String trimmed = ticket.trim();
        String[] parts = trimmed.split("\\.");
        if (parts.length != 3) {
            throw new SsoException(400, "票据不能为空");
        }

        JsonNode header;
        JsonNode payload;
        try {
            header = objectMapper.readTree(base64UrlDecode(parts[0]));
            payload = objectMapper.readTree(base64UrlDecode(parts[1]));
        } catch (Exception e) {
            log.warn("SSO 票据解析失败: {}", e.getMessage());
            throw new SsoException(401, "SSO 票据无效或已过期");
        }

        String alg = text(header, "alg");
        if (!SsoTicketAlgorithm.isSupported(alg)) {
            log.warn("SSO 拒绝不支持的算法: alg={}", alg);
            throw new SsoException(401, "票据签名无效");
        }

        String iss = text(payload, "iss");
        if (!StringUtils.hasText(iss)) {
            throw new SsoException(401, "SSO 票据无效或已过期");
        }

        SsoProperties.Partner partner = findPartner(iss);
        if (partner == null || !partner.isEnabled()) {
            log.warn("SSO 未登记或未启用的来源: iss={}", iss);
            throw new SsoException(403, "未开通该来源的单点登录", LoginChannel.fromIssuer(iss), null);
        }
        if (!SsoTicketAlgorithm.isAllowed(partner.getAlgorithm(), alg)) {
            log.warn("SSO 伙伴未允许该算法: iss={}, alg={}, allow={}",
                    iss, alg, partner.getAlgorithm());
            throw new SsoException(401, "票据签名无效", LoginChannel.fromIssuer(iss), null);
        }

        String channel = LoginChannel.fromIssuer(partner.getIssuer());
        String kid = text(header, "kid");
        PublicKey publicKey = publicKeyResolver.resolve(partner, kid);

        Claims claims = SsoTicketAlgorithm.RS256.equals(alg)
                ? verifyRs256(trimmed, publicKey, iss, channel)
                : verifySm2(parts, payload, publicKey, channel);

        validateTtl(claims, partner, channel);

        String jti = claims.getId();
        if (!StringUtils.hasText(jti)) {
            throw new SsoException(401, "SSO 票据无效或已过期", channel, null);
        }
        if (!jtiStore.tryConsume(partner.getIssuer(), jti, ssoProperties.getJtiTtlSeconds())) {
            log.warn("SSO 票据重放: iss={}, jti={}", partner.getIssuer(), jti);
            throw new SsoException(401, "票据已使用", channel, null);
        }

        String username = resolveUsername(claims, partner);
        if (!StringUtils.hasText(username)) {
            throw new SsoException(401, "账号未开通，请联系管理员", channel, null);
        }

        UserEntity user = userService.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("SSO 用户不存在: iss={}, username={}", partner.getIssuer(), username);
            throw new SsoException(401, "账号未开通，请联系管理员", channel, username);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("SSO 用户已禁用: iss={}, username={}", partner.getIssuer(), username);
            throw new SsoException(403, "账号已被禁用，请联系管理员", channel, username);
        }

        String token = jwtUtil.generateToken(user.getUsername());
        long expiration = jwtUtil.getExpirationFromToken(token);
        if (expiration <= 0) {
            expiration = System.currentTimeMillis() + jwtUtil.getConfiguredExpirationMillis();
        }

        String safeRedirect = sanitizeRedirect(redirect);
        log.info("SSO 换票成功: channel={}, alg={}, username={}", channel, alg, user.getUsername());
        return new SsoExchangeResult(
                new SsoExchangeResponseDTO(token, expiration, safeRedirect),
                user.getUsername(),
                channel);
    }

    private Claims verifyRs256(String jwt, PublicKey publicKey, String iss, String channel) {
        if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            log.warn("SSO RS256 公钥类型不匹配: iss={}, keyAlg={}", iss, publicKey.getAlgorithm());
            throw new SsoException(401, "票据签名无效", channel, null);
        }
        try {
            return Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .requireAudience(ssoProperties.getAudience())
                    .clockSkewSeconds(ssoProperties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
        } catch (ExpiredJwtException | PrematureJwtException e) {
            log.warn("SSO 票据时间无效: iss={}, reason={}", iss, e.getMessage());
            throw new SsoException(401, "票据无效或已过期", channel, null);
        } catch (JwtException e) {
            log.warn("SSO 票据验签失败: iss={}, reason={}", iss, e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("audience")) {
                throw new SsoException(401, "票据接收方不正确", channel, null);
            }
            throw new SsoException(401, "票据签名无效", channel, null);
        }
    }

    private Claims verifySm2(String[] parts, JsonNode payload, PublicKey publicKey, String channel) {
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signature;
        try {
            signature = base64UrlDecode(parts[2]);
        } catch (Exception e) {
            throw new SsoException(401, "票据签名无效", channel, null);
        }
        if (!SsoSm2Verifier.verify(publicKey, signingInput, signature)) {
            log.warn("SSO SM2 验签失败");
            throw new SsoException(401, "票据签名无效", channel, null);
        }
        validateAudience(payload, channel);
        validateNotBeforeAndExpiration(payload, channel);
        return toClaims(payload, channel);
    }

    private void validateAudience(JsonNode payload, String channel) {
        String expected = ssoProperties.getAudience();
        JsonNode aud = payload.get("aud");
        boolean matched = false;
        if (aud != null && aud.isTextual()) {
            matched = expected.equals(aud.asText());
        } else if (aud != null && aud.isArray()) {
            for (JsonNode item : aud) {
                if (item != null && expected.equals(item.asText())) {
                    matched = true;
                    break;
                }
            }
        }
        if (!matched) {
            throw new SsoException(401, "票据接收方不正确", channel, null);
        }
    }

    private void validateNotBeforeAndExpiration(JsonNode payload, String channel) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long skew = Math.max(0L, ssoProperties.getClockSkewSeconds());
        Long nbf = epochSeconds(payload, "nbf");
        Long exp = epochSeconds(payload, "exp");
        if (nbf != null && nowSeconds + skew < nbf) {
            throw new SsoException(401, "票据无效或已过期", channel, null);
        }
        if (exp == null || nowSeconds - skew >= exp) {
            throw new SsoException(401, "票据无效或已过期", channel, null);
        }
    }

    private Claims toClaims(JsonNode payload, String channel) {
        Long iat = epochSeconds(payload, "iat");
        Long nbf = epochSeconds(payload, "nbf");
        Long exp = epochSeconds(payload, "exp");
        if (iat == null || exp == null) {
            throw new SsoException(401, "票据无效或已过期", channel, null);
        }
        var builder = Jwts.claims()
                .issuer(text(payload, "iss"))
                .subject(text(payload, "sub"))
                .id(text(payload, "jti"))
                .issuedAt(new Date(iat * 1000L))
                .expiration(new Date(exp * 1000L));
        if (nbf != null) {
            builder.notBefore(new Date(nbf * 1000L));
        }
        Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getKey();
            if ("iss".equals(name) || "sub".equals(name) || "jti".equals(name)
                    || "iat".equals(name) || "nbf".equals(name) || "exp".equals(name)
                    || "aud".equals(name)) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                builder.add(name, value.numberValue());
            } else if (value.isBoolean()) {
                builder.add(name, value.booleanValue());
            } else if (value.isTextual()) {
                builder.add(name, value.asText());
            } else {
                builder.add(name, value.toString());
            }
        }
        return builder.build();
    }

    private SsoProperties.Partner findPartner(String iss) {
        Map<String, SsoProperties.Partner> partners = ssoProperties.getPartners();
        if (partners == null || partners.isEmpty()) {
            return null;
        }
        for (SsoProperties.Partner partner : partners.values()) {
            if (partner != null && iss.equals(partner.getIssuer())) {
                return partner;
            }
        }
        SsoProperties.Partner byKey = partners.get(iss);
        if (byKey != null && (!StringUtils.hasText(byKey.getIssuer()) || iss.equals(byKey.getIssuer()))) {
            if (!StringUtils.hasText(byKey.getIssuer())) {
                byKey.setIssuer(iss);
            }
            return byKey;
        }
        return null;
    }

    private void validateTtl(Claims claims, SsoProperties.Partner partner, String channel) {
        Date iat = claims.getIssuedAt();
        Date exp = claims.getExpiration();
        if (iat == null || exp == null) {
            throw new SsoException(401, "票据无效或已过期", channel, null);
        }
        long ttlSeconds = (exp.getTime() - iat.getTime() + 999) / 1000;
        if (ttlSeconds > partner.getTicketTtlMaxSeconds()) {
            log.warn("SSO 票据有效期超限: iss={}, ttlSeconds={}, max={}",
                    partner.getIssuer(), ttlSeconds, partner.getTicketTtlMaxSeconds());
            throw new SsoException(401, "票据有效期超出限制", channel, null);
        }
    }

    private String resolveUsername(Claims claims, SsoProperties.Partner partner) {
        String claimName = StringUtils.hasText(partner.getUsernameClaim())
                ? partner.getUsernameClaim()
                : "username";
        Object claimValue = claims.get(claimName);
        if (claimValue != null && StringUtils.hasText(String.valueOf(claimValue))) {
            return String.valueOf(claimValue).trim();
        }
        return claims.getSubject();
    }

    /**
     * 仅允许站内相对路径，非法则回落默认首页
     */
    public String sanitizeRedirect(String redirect) {
        String fallback = StringUtils.hasText(ssoProperties.getDefaultRedirect())
                ? ssoProperties.getDefaultRedirect()
                : "/home/dashboard";
        if (!StringUtils.hasText(redirect)) {
            return fallback;
        }
        String value = redirect.trim();
        if (!value.startsWith("/") || value.startsWith("//")
                || value.contains("://") || value.contains("\\")
                || value.contains("\r") || value.contains("\n")) {
            return fallback;
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long epochSeconds(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static byte[] base64UrlDecode(String part) {
        return Base64.getUrlDecoder().decode(part);
    }
}
