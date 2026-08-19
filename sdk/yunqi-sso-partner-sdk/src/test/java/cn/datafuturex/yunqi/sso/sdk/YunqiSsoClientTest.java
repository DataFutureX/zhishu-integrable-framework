package cn.datafuturex.yunqi.sso.sdk;

import cn.datafuturex.yunqi.sso.sdk.internal.Base64Url;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YunqiSsoClientTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("RSA 签发 Ticket 可自验签，并生成回调 URL")
    void issueRs256Ticket() throws Exception {
        YunqiSsoKeys.KeyMaterial keys = YunqiSsoKeys.generateRsa(2048);
        Path privatePem = tempDir.resolve("private.pem");
        keys.writePrivateKeyPem(privatePem);

        YunqiSsoClient client = YunqiSsoClient.builder()
                .issuer("wanxiang")
                .kid("wanxiang-2026")
                .algorithm(SsoAlgorithm.RS256)
                .privateKeyFile(privatePem)
                .yunqiWebBase("http://localhost:3000")
                .defaultRedirect("/home/dashboard")
                .build();

        SsoTicketResult result = client.issueTicket(
                SsoTicketRequest.builder("admin")
                        .displayName("管理员")
                        .ttlSeconds(60)
                        .build());

        assertNotNull(result.ticket());
        assertNotNull(result.jti());
        assertEquals(SsoAlgorithm.RS256, result.algorithm());
        assertTrue(result.callbackUrl().startsWith("http://localhost:3000/sso/callback?ticket="));
        assertTrue(result.callbackUrl().contains("redirect=%2Fhome%2Fdashboard"));
        assertTrue(verifyRs256(keys, result.ticket()));

        String[] parts = result.ticket().split("\\.");
        String headerJson = new String(Base64Url.decode(parts[0]), StandardCharsets.UTF_8);
        String payloadJson = new String(Base64Url.decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"RS256\""));
        assertTrue(headerJson.contains("\"kid\":\"wanxiang-2026\""));
        assertTrue(payloadJson.contains("\"iss\":\"wanxiang\""));
        assertTrue(payloadJson.contains("\"aud\":\"yunqi-application-platform\""));
        assertTrue(payloadJson.contains("\"username\":\"admin\""));
        assertTrue(payloadJson.contains("\"name\":\"管理员\""));
    }

    @Test
    @DisplayName("SM2 签发 Ticket 可自验签")
    void issueSm2Ticket() throws Exception {
        YunqiSsoKeys.KeyMaterial keys = YunqiSsoKeys.generateSm2();
        YunqiSsoClient client = YunqiSsoClient.builder()
                .issuer("shuzhi-iot")
                .kid("shuzhi-iot-sm2-2026")
                .algorithm(SsoAlgorithm.SM2)
                .privateKey(keys.privateKey())
                .build();

        SsoTicketResult result = client.issueTicket("iot-user");
        assertEquals(SsoAlgorithm.SM2, result.algorithm());
        assertTrue(result.ticket().startsWith("eyJ"));
        assertTrue(verifySm2(keys, result.ticket()));
        assertEquals(null, result.callbackUrl());
    }

    @Test
    @DisplayName("非法 redirect 回落默认首页")
    void sanitizeRedirect() {
        assertEquals("/home/dashboard", SsoCallbackUrlBuilder.sanitizeRedirect("https://evil.example"));
        assertEquals("/ok", SsoCallbackUrlBuilder.sanitizeRedirect("/ok"));
    }

    @Test
    @DisplayName("TTL 超限拒绝")
    void rejectBadTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> SsoTicketRequest.builder("u").ttlSeconds(121).build());
    }

    @Test
    @DisplayName("密钥 PEM 往返")
    void keyPemRoundTrip() {
        YunqiSsoKeys.KeyMaterial rsa = YunqiSsoKeys.generateRsa();
        assertFalse(rsa.publicKeyPem().isBlank());
        assertTrue(rsa.publicKeyPem().contains("BEGIN PUBLIC KEY"));
        YunqiSsoKeys.KeyMaterial sm2 = YunqiSsoKeys.generateSm2();
        assertTrue(sm2.privateKeyPem().contains("BEGIN PRIVATE KEY"));
    }

    private static boolean verifyRs256(YunqiSsoKeys.KeyMaterial keys, String ticket) throws Exception {
        String[] parts = ticket.split("\\.");
        byte[] data = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keys.publicKey());
        signature.update(data);
        return signature.verify(sig);
    }

    private static boolean verifySm2(YunqiSsoKeys.KeyMaterial keys, String ticket) throws Exception {
        String[] parts = ticket.split("\\.");
        byte[] data = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        Signature signature = Signature.getInstance("SM3withSM2", "BC");
        signature.initVerify(keys.publicKey());
        signature.update(data);
        return signature.verify(sig);
    }
}
