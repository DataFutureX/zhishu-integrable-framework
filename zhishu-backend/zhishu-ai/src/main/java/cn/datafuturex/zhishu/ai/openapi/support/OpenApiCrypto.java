package cn.datafuturex.zhishu.ai.openapi.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 开放 API AK/SK 签名与验签工具。
 * <p>
 * Token 格式：{@code {ak}:{timestampMs}:{signature}}
 * <p>
 * signature = Base64(HMAC-SHA256(sk, ak + timestampMs))
 */
public final class OpenApiCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Token 有效期：5 分钟 */
    public static final long TOKEN_TOLERANCE_MS = 5 * 60 * 1000L;

    private OpenApiCrypto() {
    }

    /* ── 密钥生成 ──────────────────────────────────────────── */

    /** 生成 Access Key，格式：zsak_ + 16 字节 hex（32 字符） */
    public static String generateAccessKey() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return "zsak_" + HexFormat.of().formatHex(raw);
    }

    /** 生成 Secret Key，格式：zssk_ + 32 字节 hex（64 字符） */
    public static String generateSecretKey() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return "zssk_" + HexFormat.of().formatHex(raw);
    }

    /* ── 签名 ──────────────────────────────────────────────── */

    /**
     * 用 SK 对 (ak + timestampMs) 做 HMAC-SHA256 签名，返回完整 Token。
     *
     * @param ak          Access Key
     * @param sk          Secret Key（明文）
     * @param timestampMs 毫秒时间戳
     * @return 完整 Token 字符串
     */
    public static String signToken(String ak, String sk, long timestampMs) {
        String ts = String.valueOf(timestampMs);
        String signingInput = ak + ts;
        String signature = hmacSha256Base64(sk, signingInput);
        return ak + ":" + ts + ":" + signature;
    }

    /* ── 验签 ──────────────────────────────────────────────── */

    /**
     * 解析并验证 Token。
     *
     * @param token 完整 Token
     * @param sk    服务端存储的 Secret Key（明文）
     * @return 解析结果
     * @throws TokenParseException 格式错误、签名无效或已过期
     */
    public static TokenPayload verifyToken(String token, String sk) {
        TokenPayload payload = parseToken(token);
        String signingInput = payload.ak + payload.timestampMs;
        String expected = hmacSha256Base64(sk, signingInput);
        if (!constantTimeEquals(expected, payload.signature)) {
            throw new TokenParseException("签名无效");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - payload.timestampMs) > TOKEN_TOLERANCE_MS) {
            throw new TokenParseException("Token 已过期或时间偏差过大");
        }
        return payload;
    }

    /**
     * 仅解析 Token（不做签名校验），用于获取 AK 以查找对应 SK。
     */
    public static TokenPayload parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenParseException("Token 为空");
        }
        String[] parts = token.split(":");
        if (parts.length != 3) {
            throw new TokenParseException("Token 格式错误，应为 ak:ts:sig");
        }
        String ak = parts[0];
        long ts;
        try {
            ts = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new TokenParseException("Token 时间戳无效");
        }
        String sig = parts[2];
        if (ak.isBlank() || sig.isBlank()) {
            throw new TokenParseException("Token 格式错误");
        }
        return new TokenPayload(ak, ts, sig);
    }

    /* ── 内部方法 ──────────────────────────────────────────── */

    private static String hmacSha256Base64(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    /** 常数时间比较，防止时序攻击 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }

    /* ── 数据结构 ──────────────────────────────────────────── */

    /** Token 解析结果 */
    public record TokenPayload(String ak, long timestampMs, String signature) {
    }

    /** Token 解析/验签异常 */
    public static class TokenParseException extends RuntimeException {
        public TokenParseException(String message) {
            super(message);
        }
    }
}
