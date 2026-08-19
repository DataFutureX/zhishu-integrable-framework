package cn.datafuturex.yunqi.sso.sdk.internal;

import cn.datafuturex.yunqi.sso.sdk.SsoAlgorithm;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.UUID;

/**
 * 按协议组装并签名伙伴 Ticket。
 */
public final class TicketSigner {

    private static final String AUDIENCE = "zhishu-integrable-framework";

    private TicketSigner() {
    }

    public static SignedTicket sign(
            SsoAlgorithm algorithm,
            PrivateKey privateKey,
            String issuer,
            String kid,
            String username,
            String subject,
            String displayName,
            long ttlSeconds,
            long nowEpochSeconds) throws Exception {

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer 不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
        String sub = (subject == null || subject.isBlank()) ? username.trim() : subject.trim();
        String jti = UUID.randomUUID().toString();
        long iat = nowEpochSeconds;
        long exp = iat + ttlSeconds;

        StringBuilder header = new StringBuilder(96);
        header.append("{\"alg\":\"").append(algorithm.jwtAlg()).append("\",\"typ\":\"JWT\"");
        if (kid != null && !kid.isBlank()) {
            header.append(",\"kid\":\"").append(escapeJson(kid.trim())).append('"');
        }
        header.append('}');

        StringBuilder payload = new StringBuilder(256);
        payload.append('{')
                .append("\"iss\":\"").append(escapeJson(issuer.trim())).append("\",")
                .append("\"aud\":\"").append(AUDIENCE).append("\",")
                .append("\"sub\":\"").append(escapeJson(sub)).append("\",")
                .append("\"username\":\"").append(escapeJson(username.trim())).append("\",");
        if (displayName != null && !displayName.isBlank()) {
            payload.append("\"name\":\"").append(escapeJson(displayName.trim())).append("\",");
        }
        payload.append("\"iat\":").append(iat).append(',')
                .append("\"nbf\":").append(iat).append(',')
                .append("\"exp\":").append(exp).append(',')
                .append("\"jti\":\"").append(jti).append('"')
                .append('}');

        String headerPart = Base64Url.encodeUtf8(header.toString());
        String payloadPart = Base64Url.encodeUtf8(payload.toString());
        String signingInput = headerPart + '.' + payloadPart;
        byte[] signature = signBytes(algorithm, privateKey, signingInput.getBytes(StandardCharsets.US_ASCII));
        String ticket = signingInput + '.' + Base64Url.encode(signature);
        return new SignedTicket(ticket, jti, iat, exp, algorithm);
    }

    private static byte[] signBytes(SsoAlgorithm algorithm, PrivateKey privateKey, byte[] data) throws Exception {
        if (algorithm == SsoAlgorithm.RS256) {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        }
        PemSupport.ensureBouncyCastle();
        Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public record SignedTicket(String ticket, String jti, long iat, long exp, SsoAlgorithm algorithm) {
    }
}
