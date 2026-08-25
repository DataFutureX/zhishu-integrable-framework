package cn.datafuturex.zhishu.security;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * RSA 加解密工具
 */
public final class RsaCryptoUtils {

    private static final String ALGORITHM = "RSA/ECB/PKCS1Padding";

    private RsaCryptoUtils() {
    }

    /**
     * 生成 RSA 密钥对
     */
    public static KeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥对生成失败", e);
        }
    }

    /**
     * 公钥转 PEM 格式（兼容 JSEncrypt 等前端库）
     */
    public static String toPemPublicKey(PublicKey publicKey) {
        String base64 = Base64.encode(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    /**
     * 使用公钥加密为 Base64 密文
     */
    public static String encryptToBase64(PublicKey publicKey, String plainText) {
        RSA rsa = new RSA(null, publicKey);
        return rsa.encryptBase64(plainText, KeyType.PublicKey);
    }

    /**
     * 使用私钥解密 Base64 密文
     */
    public static String decryptFromBase64(PrivateKey privateKey, String cipherText) {
        RSA rsa = new RSA(privateKey, null);
        return rsa.decryptStr(cipherText, KeyType.PrivateKey);
    }

    public static String algorithm() {
        return ALGORITHM;
    }
}
