package cn.datafuturex.zhishu.openapi.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ZhishuOpenApiSignerTest {

    private static final String AK = "zsak_0123456789abcdef0123456789abcdef";
    private static final String SK = "zssk_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("签名格式为 ak:ts:sig，三段以冒号分隔")
    void tokenFormat() {
        String token = ZhishuOpenApiSigner.sign(AK, SK, 1700000000000L);
        String[] parts = token.split(":");
        assertEquals(3, parts.length, "Token 应为三段");
        assertEquals(AK, parts[0]);
        assertEquals("1700000000000", parts[1]);
        assertFalse(parts[2].isBlank(), "签名不能为空");
    }

    @Test
    @DisplayName("签名结果可被本地 HMAC-SHA256 验证")
    void signatureVerifiable() throws Exception {
        long ts = 1700000000000L;
        String token = ZhishuOpenApiSigner.sign(AK, SK, ts);
        String[] parts = token.split(":");
        String sig = parts[2];

        // 手动计算期望签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((AK + ts).getBytes(StandardCharsets.UTF_8));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

        assertEquals(expected, sig);
    }

    @Test
    @DisplayName("authorizationHeader 以 Bearer 前缀返回")
    void authorizationHeaderFormat() {
        String header = ZhishuOpenApiSigner.authorizationHeader(AK, SK);
        assertTrue(header.startsWith("Bearer "));
        String token = header.substring("Bearer ".length());
        assertTrue(token.startsWith(AK + ":"));
    }

    @Test
    @DisplayName("相同 AK/SK/时间戳产生相同签名（确定性）")
    void deterministicSignature() {
        String a = ZhishuOpenApiSigner.sign(AK, SK, 1700000000000L);
        String b = ZhishuOpenApiSigner.sign(AK, SK, 1700000000000L);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("不同时间戳产生不同签名")
    void differentTimestampDifferentSignature() {
        String a = ZhishuOpenApiSigner.sign(AK, SK, 1700000000000L);
        String b = ZhishuOpenApiSigner.sign(AK, SK, 1700000001000L);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("空 AK 抛异常")
    void rejectEmptyAk() {
        assertThrows(IllegalArgumentException.class, () -> ZhishuOpenApiSigner.sign("", SK));
        assertThrows(IllegalArgumentException.class, () -> ZhishuOpenApiSigner.sign(null, SK));
    }

    @Test
    @DisplayName("空 SK 抛异常")
    void rejectEmptySk() {
        assertThrows(IllegalArgumentException.class, () -> ZhishuOpenApiSigner.sign(AK, ""));
        assertThrows(IllegalArgumentException.class, () -> ZhishuOpenApiSigner.sign(AK, null));
    }

    @Test
    @DisplayName("无参 sign() 使用当前时间戳，格式正确")
    void signWithCurrentTime() {
        String token = ZhishuOpenApiSigner.sign(AK, SK);
        String[] parts = token.split(":");
        assertEquals(3, parts.length);
        long ts = Long.parseLong(parts[1]);
        // 时间戳应在当前时间 5 秒内
        assertTrue(Math.abs(System.currentTimeMillis() - ts) < 5000);
    }
}
