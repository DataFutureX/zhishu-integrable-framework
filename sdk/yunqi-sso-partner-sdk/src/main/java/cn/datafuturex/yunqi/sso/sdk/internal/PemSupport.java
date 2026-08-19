package cn.datafuturex.yunqi.sso.sdk.internal;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 读写（RSA / SM2）。
 */
public final class PemSupport {

    private PemSupport() {
    }

    public static void ensureBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static PrivateKey readPrivateKey(String pem, boolean sm2) throws Exception {
        ensureBouncyCastle();
        String trimmed = pem == null ? "" : pem.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("私钥 PEM 不能为空");
        }

        try (PEMParser parser = new PEMParser(new StringReader(trimmed))) {
            Object obj = parser.readObject();
            if (obj == null) {
                return readPrivateKeyPkcs8Fallback(trimmed, sm2);
            }
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (obj instanceof PEMKeyPair keyPair) {
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (obj instanceof PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            // 加密私钥：暂不支持，走明确错误
            if (obj != null && obj.getClass().getSimpleName().contains("EncryptedPrivateKeyInfo")) {
                throw new IllegalArgumentException("暂不支持加密私钥 PEM，请先解密为 PKCS#8");
            }
        }

        return readPrivateKeyPkcs8Fallback(trimmed, sm2);
    }

    private static PrivateKey readPrivateKeyPkcs8Fallback(String pem, boolean sm2) throws Exception {
        String normalized = stripPemHeaders(pem);
        byte[] der = Base64.getMimeDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        if (sm2) {
            ensureBouncyCastle();
            return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePrivate(spec);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static PublicKey readPublicKey(String pem, boolean sm2) throws Exception {
        ensureBouncyCastle();
        String trimmed = pem == null ? "" : pem.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("公钥 PEM 不能为空");
        }

        try (PEMParser parser = new PEMParser(new StringReader(trimmed))) {
            Object obj = parser.readObject();
            if (obj instanceof SubjectPublicKeyInfo info) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
                return converter.getPublicKey(info);
            }
        } catch (Exception ignored) {
            // fallback
        }

        try (PemReader reader = new PemReader(new StringReader(trimmed))) {
            PemObject pemObject = reader.readPemObject();
            if (pemObject != null) {
                X509EncodedKeySpec spec = new X509EncodedKeySpec(pemObject.getContent());
                if (sm2) {
                    return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePublic(spec);
                }
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            }
        }

        String normalized = stripPemHeaders(trimmed);
        byte[] der = Base64.getMimeDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        if (sm2) {
            return KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePublic(spec);
        }
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public static String toPrivateKeyPem(PrivateKey privateKey) throws IOException {
        return writePem("PRIVATE KEY", privateKey.getEncoded());
    }

    public static String toPublicKeyPem(PublicKey publicKey) throws IOException {
        return writePem("PUBLIC KEY", publicKey.getEncoded());
    }

    private static String writePem(String type, byte[] content) throws IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(new PemObject(type, content));
        }
        return sw.toString();
    }

    private static String stripPemHeaders(String pem) {
        return pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }
}
