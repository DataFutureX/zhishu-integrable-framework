package cn.datafuturex.zhishu.security;

import cn.datafuturex.zhishu.config.LoginCryptoProperties;
import cn.datafuturex.zhishu.modules.dto.PublicKeyResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录 RSA 加解密服务单元测试
 */
class LoginCryptoServiceTest {

    private LoginKeyStore loginKeyStore;
    private LoginCryptoService loginCryptoService;

    @BeforeEach
    void setUp() {
        LoginCryptoProperties properties = new LoginCryptoProperties();
        properties.setKeySize(2048);
        properties.setKeyExpireSeconds(300);
        loginKeyStore = new LoginKeyStore();
        loginCryptoService = new LoginCryptoService(loginKeyStore, properties);
    }

    @Test
    @DisplayName("生成公钥应返回完整信息")
    void testCreatePublicKey() {
        PublicKeyResponseDTO response = loginCryptoService.createPublicKey();

        assertNotNull(response.keyId());
        assertNotNull(response.publicKey());
        assertEquals("RSA/ECB/PKCS1Padding", response.algorithm());
        assertEquals(300, response.expireSeconds());
    }

    @Test
    @DisplayName("加密凭证应能正确解密")
    void testDecryptAndConsume() {
        KeyPair keyPair = RsaCryptoUtils.generateKeyPair(2048);
        String keyId = "test-key-id";
        loginKeyStore.save(keyId, keyPair.getPrivate(), System.currentTimeMillis() + 300_000);

        String encryptedUsername = RsaCryptoUtils.encryptToBase64(keyPair.getPublic(), "admin");
        String encryptedPassword = RsaCryptoUtils.encryptToBase64(keyPair.getPublic(), "admin123");

        DecryptedCredentials credentials = loginCryptoService.decryptAndConsume(
                keyId, encryptedUsername, encryptedPassword);

        assertEquals("admin", credentials.username());
        assertEquals("admin123", credentials.password());
    }

    @Test
    @DisplayName("密钥重复使用应失败")
    void testKeyOneTimeUse() {
        KeyPair keyPair = RsaCryptoUtils.generateKeyPair(2048);
        String keyId = "test-key-id";
        loginKeyStore.save(keyId, keyPair.getPrivate(), System.currentTimeMillis() + 300_000);

        String encryptedUsername = RsaCryptoUtils.encryptToBase64(keyPair.getPublic(), "admin");
        String encryptedPassword = RsaCryptoUtils.encryptToBase64(keyPair.getPublic(), "admin123");

        loginCryptoService.decryptAndConsume(keyId, encryptedUsername, encryptedPassword);

        assertThrows(IllegalArgumentException.class, () ->
                loginCryptoService.decryptAndConsume(keyId, encryptedUsername, encryptedPassword));
    }
}
