package cn.datafuturex.zhishu.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 凭证加解密单元测试。
 * <p>
 * 这层直接决定模型供应商 API Key 能否被解出：密钥不一致会以「解密失败」在运行时爆发
 * （测试环境曾因两把 key 共用一个库踩过），因此把格式约定与失败行为固定下来。
 */
class CredentialCryptoTest {

    private static final String KEY_A = "unit-test-key-A";
    private static final String KEY_B = "unit-test-key-B";

    @Test
    @DisplayName("加解密可往返，且密文带 enc: 前缀")
    void roundTrip() {
        String cipher = CredentialCrypto.encrypt("sk-real-value-123", KEY_A);

        assertTrue(cipher.startsWith("enc:"));
        assertNotEquals("sk-real-value-123", cipher);
        assertEquals("sk-real-value-123", CredentialCrypto.decrypt(cipher, KEY_A));
    }

    @Test
    @DisplayName("IV 随机：同一明文两次密文不同，但都能解回")
    void randomIvPerCall() {
        String first = CredentialCrypto.encrypt("same-input", KEY_A);
        String second = CredentialCrypto.encrypt("same-input", KEY_A);

        assertNotEquals(first, second);
        assertEquals("same-input", CredentialCrypto.decrypt(first, KEY_A));
        assertEquals("same-input", CredentialCrypto.decrypt(second, KEY_A));
    }

    @Test
    @DisplayName("密钥不一致必须抛错，而不是静默返回乱码")
    void wrongKeyThrows() {
        String cipher = CredentialCrypto.encrypt("value", KEY_A);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CredentialCrypto.decrypt(cipher, KEY_B));
        assertTrue(error.getMessage().contains("解密失败"));
    }

    @Test
    @DisplayName("未配置密钥：已加密数据抛明确错误，加密动作退化为明文存储")
    void missingKeyBehaviour() {
        String cipher = CredentialCrypto.encrypt("value", KEY_A);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CredentialCrypto.decrypt(cipher, ""));
        assertTrue(error.getMessage().contains("未配置"));
        assertEquals("value", CredentialCrypto.encrypt("value", null));
    }

    @Test
    @DisplayName("历史明文与空值原样放行，保证平滑兼容")
    void plainAndBlankPassThrough() {
        assertEquals("sk-legacy-plain", CredentialCrypto.decrypt("sk-legacy-plain", KEY_A));
        assertNull(CredentialCrypto.decrypt(null, KEY_A));
        assertNull(CredentialCrypto.decrypt("  ", KEY_A));
        assertNull(CredentialCrypto.encrypt(null, KEY_A));
        assertNull(CredentialCrypto.encrypt("   ", KEY_A));
    }

    @Test
    @DisplayName("摘要与 Key 生成：稳定、十六进制小写、带前缀")
    void digestAndKeyGeneration() {
        assertEquals(64, CredentialCrypto.sha256Hex("admin123").length());
        assertEquals(CredentialCrypto.sha256Hex("admin123"), CredentialCrypto.sha256Hex("admin123"));
        assertNotEquals(CredentialCrypto.sha256Hex("admin123"), CredentialCrypto.sha256Hex("admin124"));
        assertTrue(CredentialCrypto.newApiKey().startsWith("wxmcp_"));
        assertNotEquals(CredentialCrypto.newApiKey(), CredentialCrypto.newApiKey());
    }
}
