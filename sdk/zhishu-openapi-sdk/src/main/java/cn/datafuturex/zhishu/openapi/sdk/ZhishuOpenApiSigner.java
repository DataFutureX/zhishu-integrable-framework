package cn.datafuturex.zhishu.openapi.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * AK/SK Token 签名器。
 * <p>
 * Token 格式：{@code {ak}:{timestampMs}:{signature}}<br>
 * 签名算法：{@code Base64URL(HMAC-SHA256(sk, ak + timestampMs))}
 * </p>
 */
public final class ZhishuOpenApiSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private ZhishuOpenApiSigner() {
    }

    /**
     * 生成 Bearer Token。
     *
     * @param ak Access Key
     * @param sk Secret Key（明文）
     * @return 完整 Token 字符串，可直接放入 {@code Authorization: Bearer ...} 头
     */
    public static String sign(String ak, String sk) {
        return sign(ak, sk, System.currentTimeMillis());
    }

    /**
     * 生成 Bearer Token（指定时间戳，用于测试或时钟同步场景）。
     *
     * @param ak          Access Key
     * @param sk          Secret Key（明文）
     * @param timestampMs 毫秒时间戳
     * @return 完整 Token 字符串
     */
    public static String sign(String ak, String sk, long timestampMs) {
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("Access Key 不能为空");
        }
        if (sk == null || sk.isBlank()) {
            throw new IllegalArgumentException("Secret Key 不能为空");
        }
        String ts = String.valueOf(timestampMs);
        String signingInput = ak + ts;
        String signature = hmacSha256Base64Url(sk, signingInput);
        return ak + ":" + ts + ":" + signature;
    }

    /**
     * 生成 Authorization 头值。
     *
     * @param ak Access Key
     * @param sk Secret Key
     * @return {@code "Bearer {ak}:{ts}:{signature}"}
     */
    public static String authorizationHeader(String ak, String sk) {
        return "Bearer " + sign(ak, sk);
    }

    /* ── 内部方法 ──────────────────────────────────────────── */

    private static String hmacSha256Base64Url(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }
}
