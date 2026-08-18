package cn.datafuturex.yunqi.security.sso;

import cn.datafuturex.yunqi.config.SsoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按伙伴 / kid 解析并缓存 RSA 公钥
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoPublicKeyResolver {

    private final ResourceLoader resourceLoader;
    private final Map<String, RSAPublicKey> cache = new ConcurrentHashMap<>();

    public RSAPublicKey resolve(SsoProperties.Partner partner, String kid) {
        String location = null;
        if (StringUtils.hasText(kid) && partner.getPublicKeys() != null) {
            location = partner.getPublicKeys().get(kid);
        }
        if (!StringUtils.hasText(location)) {
            location = partner.getPublicKey();
        }
        if (!StringUtils.hasText(location)) {
            throw new SsoException(401, "票据签名无效", LoginChannel.fromIssuer(partner.getIssuer()), null);
        }

        final String resolvedLocation = location;
        String cacheKey = partner.getIssuer() + "|" + (StringUtils.hasText(kid) ? kid : "default") + "|" + resolvedLocation;
        return cache.computeIfAbsent(cacheKey, k -> loadPublicKey(resolvedLocation, partner.getIssuer()));
    }

    private RSAPublicKey loadPublicKey(String location, String issuer) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.error("SSO 公钥不存在: issuer={}, location={}", issuer, location);
                throw new SsoException(401, "票据签名无效", LoginChannel.fromIssuer(issuer), null);
            }
            String pem;
            try (InputStream in = resource.getInputStream()) {
                pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return parsePem(pem);
        } catch (SsoException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载 SSO 公钥失败: issuer={}, location={}", issuer, location, e);
            throw new SsoException(401, "票据签名无效", LoginChannel.fromIssuer(issuer), null);
        }
    }

    static RSAPublicKey parsePem(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) factory.generatePublic(spec);
    }
}
