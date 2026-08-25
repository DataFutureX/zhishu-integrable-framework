package cn.datafuturex.yunqi.sso.sdk.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JWT 片段 Base64URL（无 padding）。
 */
public final class Base64Url {

    private Base64Url() {
    }

    public static String encode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static String encodeUtf8(String text) {
        return encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decode(String text) {
        return Base64.getUrlDecoder().decode(text);
    }
}
