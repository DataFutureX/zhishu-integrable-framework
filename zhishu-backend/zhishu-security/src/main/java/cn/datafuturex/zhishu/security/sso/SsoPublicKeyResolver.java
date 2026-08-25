package cn.datafuturex.zhishu.security.sso;

import cn.datafuturex.zhishu.config.SsoProperties;
import cn.hutool.crypto.PemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按伙伴 / kid 解析并缓存公钥（RSA 或 SM2）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoPublicKeyResolver {

    private final ResourceLoader resourceLoader;
    private final Map<String, PublicKey> cache = new ConcurrentHashMap<>();

    public PublicKey resolve(SsoProperties.Partner partner, String kid) {
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

    private PublicKey loadPublicKey(String location, String issuer) {
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

    static PublicKey parsePem(String pem) throws Exception {
        ensureBouncyCastle();
        try (InputStream in = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            PublicKey key = PemUtil.readPemPublicKey(in);
            if (key != null) {
                return key;
            }
        } catch (Exception e) {
            log.debug("PemUtil 解析 SSO 公钥失败，回退 KeyFactory: {}", e.getMessage());
        }

        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception rsaFailed) {
            // 国密 SM2 曲线 OID，须用 BC 的 EC KeyFactory
            return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePublic(spec);
        }
    }

    private static void ensureBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
