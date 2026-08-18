package cn.datafuturex.yunqi.security;

import cn.datafuturex.yunqi.config.LoginCryptoProperties;
import cn.datafuturex.yunqi.modules.dto.PublicKeyResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.util.UUID;

/**
 * 登录凭证 RSA 加解密服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginCryptoService {

    private final LoginKeyStore loginKeyStore;
    private final LoginCryptoProperties loginCryptoProperties;

    /**
     * 生成临时 RSA 密钥对并返回公钥
     */
    public PublicKeyResponseDTO createPublicKey() {
        KeyPair keyPair = RsaCryptoUtils.generateKeyPair(loginCryptoProperties.getKeySize());
        String keyId = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + loginCryptoProperties.getKeyExpireSeconds() * 1000L;
        loginKeyStore.save(keyId, keyPair.getPrivate(), expireAt);

        log.debug("生成登录 RSA 公钥: keyId={}", keyId);
        return new PublicKeyResponseDTO(
                keyId,
                RsaCryptoUtils.toPemPublicKey(keyPair.getPublic()),
                RsaCryptoUtils.algorithm(),
                loginCryptoProperties.getKeyExpireSeconds()
        );
    }

    /**
     * 解密登录凭证（密钥一次性使用）
     *
     * @param keyId              密钥标识
     * @param encryptedUsername  加密用户名（Base64）
     * @param encryptedPassword  加密密码（Base64）
     * @return 明文用户名和密码
     */
    public DecryptedCredentials decryptAndConsume(String keyId, String encryptedUsername, String encryptedPassword) {
        if (!StringUtils.hasText(keyId)
                || !StringUtils.hasText(encryptedUsername)
                || !StringUtils.hasText(encryptedPassword)) {
            throw new IllegalArgumentException("登录凭证不完整");
        }

        var privateKey = loginKeyStore.consumePrivateKey(keyId)
                .orElseThrow(() -> new IllegalArgumentException("密钥无效或已过期"));

        try {
            String username = RsaCryptoUtils.decryptFromBase64(privateKey, encryptedUsername);
            String password = RsaCryptoUtils.decryptFromBase64(privateKey, encryptedPassword);
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalArgumentException("解密结果为空");
            }
            return new DecryptedCredentials(username, password);
        } catch (Exception e) {
            log.warn("登录凭证解密失败: keyId={}", keyId);
            throw new IllegalArgumentException("登录凭证解密失败");
        }
    }
}
