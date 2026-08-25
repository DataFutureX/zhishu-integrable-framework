package cn.datafuturex.yunqi.sso.sdk;

import cn.datafuturex.yunqi.sso.sdk.internal.PemSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 伙伴侧密钥生成与 PEM 导出。私钥仅留在伙伴机房，公钥交给云起登记。
 */
public final class YunqiSsoKeys {

    private YunqiSsoKeys() {
    }

    /** 生成 RSA 密钥对（默认 2048 bit，云起要求 ≥2048） */
    public static KeyMaterial generateRsa() {
        return generateRsa(2048);
    }

    public static KeyMaterial generateRsa(int keySize) {
        if (keySize < 2048) {
            throw new IllegalArgumentException("RSA 密钥长度至少 2048");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            KeyPair pair = generator.generateKeyPair();
            return KeyMaterial.of(SsoAlgorithm.RS256, pair.getPrivate(), pair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥失败: " + e.getMessage(), e);
        }
    }

    /** 生成国密 SM2 密钥对（曲线 sm2p256v1） */
    public static KeyMaterial generateSm2() {
        try {
            PemSupport.ensureBouncyCastle();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new ECNamedCurveGenParameterSpec("sm2p256v1"));
            KeyPair pair = generator.generateKeyPair();
            return KeyMaterial.of(SsoAlgorithm.SM2, pair.getPrivate(), pair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException("生成 SM2 密钥失败: " + e.getMessage(), e);
        }
    }

    public static PrivateKey loadPrivateKey(String pem, SsoAlgorithm algorithm) {
        try {
            return PemSupport.readPrivateKey(pem, algorithm == SsoAlgorithm.SM2);
        } catch (Exception e) {
            throw new IllegalArgumentException("加载私钥失败: " + e.getMessage(), e);
        }
    }

    public static PrivateKey loadPrivateKey(Path pemFile, SsoAlgorithm algorithm) {
        try {
            return loadPrivateKey(Files.readString(pemFile, StandardCharsets.UTF_8), algorithm);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("读取私钥文件失败: " + pemFile + " — " + e.getMessage(), e);
        }
    }

    public static PublicKey loadPublicKey(String pem, SsoAlgorithm algorithm) {
        try {
            return PemSupport.readPublicKey(pem, algorithm == SsoAlgorithm.SM2);
        } catch (Exception e) {
            throw new IllegalArgumentException("加载公钥失败: " + e.getMessage(), e);
        }
    }

    /**
     * 密钥材料：可导出 PEM 文本或写入文件。
     */
    public static final class KeyMaterial {
        private final SsoAlgorithm algorithm;
        private final PrivateKey privateKey;
        private final PublicKey publicKey;

        private KeyMaterial(SsoAlgorithm algorithm, PrivateKey privateKey, PublicKey publicKey) {
            this.algorithm = algorithm;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        static KeyMaterial of(SsoAlgorithm algorithm, PrivateKey privateKey, PublicKey publicKey) {
            return new KeyMaterial(algorithm, privateKey, publicKey);
        }

        public SsoAlgorithm algorithm() {
            return algorithm;
        }

        public PrivateKey privateKey() {
            return privateKey;
        }

        public PublicKey publicKey() {
            return publicKey;
        }

        public String privateKeyPem() {
            try {
                return PemSupport.toPrivateKeyPem(privateKey);
            } catch (Exception e) {
                throw new IllegalStateException("导出私钥 PEM 失败", e);
            }
        }

        public String publicKeyPem() {
            try {
                return PemSupport.toPublicKeyPem(publicKey);
            } catch (Exception e) {
                throw new IllegalStateException("导出公钥 PEM 失败", e);
            }
        }

        public void writePrivateKeyPem(Path path) {
            write(path, privateKeyPem());
        }

        public void writePublicKeyPem(Path path) {
            write(path, publicKeyPem());
        }

        private static void write(Path path, String content) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, content, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("写入文件失败: " + path, e);
            }
        }
    }
}
