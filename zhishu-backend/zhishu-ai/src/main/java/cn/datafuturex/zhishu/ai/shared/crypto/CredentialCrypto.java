package cn.datafuturex.zhishu.ai.shared.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 凭证加解密与摘要工具（AES-256-GCM，密钥取配置值的 SHA-256）。
 * <p>
 * 原位于 {@code ai.mcp.support}，但实际使用方已跨三域（MCP 上游凭证、开放应用 AK/SK、
 * 模型网关供应商 API Key），故下沉到 {@code ai.shared.crypto}：shared 为 OPEN 模块，
 * 兄弟模块与跳 Maven 模块（zhishu-model-gateway）均可合法引用。
 * <p>
 * 存储约定：{@code enc:Base64(iv[12] + cipherText+tag)}；无 {@code enc:} 前缀的值视为明文原样返回。
 * 更换 {@code zhishu.ai.crypto-key} 会使存量密文无法解出（抛「解密失败」）。
 */
public final class CredentialCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCrypto() {
    }

    public static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String newApiKey() {
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        return "wxmcp_" + HexFormat.of().formatHex(raw);
    }

    public static String keyPrefix(String apiKey) {
        if (apiKey == null || apiKey.length() < 14) {
            return apiKey;
        }
        return apiKey.substring(0, 14);
    }

    public static String encrypt(String plain, String cryptoKey) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        if (cryptoKey == null || cryptoKey.isBlank()) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(cryptoKey), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return "enc:" + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public static String decrypt(String stored, String cryptoKey) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith("enc:")) {
            return stored;
        }
        if (cryptoKey == null || cryptoKey.isBlank()) {
            throw new IllegalStateException("已加密上游凭证，但未配置解密密钥（zhishu.ai.crypto-key 或 wanxiang.mcp.crypto-key）");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(4));
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[packed.length - 12];
            System.arraycopy(packed, 0, iv, 0, 12);
            System.arraycopy(packed, 12, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(cryptoKey), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    private static SecretKeySpec aesKey(String cryptoKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cryptoKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
