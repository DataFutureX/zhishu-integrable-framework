package cn.datafuturex.yunqi.sso.sdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 云起前端回调 URL 拼装。
 */
public final class SsoCallbackUrlBuilder {

    public static final String CALLBACK_PATH = "/sso/callback";
    public static final String DEFAULT_REDIRECT = "/home/dashboard";

    private SsoCallbackUrlBuilder() {
    }

    /**
     * @param yunqiWebBase 云起前端源站，如 https://yunqi.example.com（不要末尾斜杠）
     * @param ticket       伙伴签发的 JWT
     * @param redirect     站内相对路径；空则使用 {@link #DEFAULT_REDIRECT}
     */
    public static String build(String yunqiWebBase, String ticket, String redirect) {
        if (yunqiWebBase == null || yunqiWebBase.isBlank()) {
            throw new IllegalArgumentException("yunqiWebBase 不能为空");
        }
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("ticket 不能为空");
        }
        String base = trimSlash(yunqiWebBase.trim());
        String safeRedirect = sanitizeRedirect(redirect);
        return base + CALLBACK_PATH
                + "?ticket=" + urlEncode(ticket)
                + "&redirect=" + urlEncode(safeRedirect);
    }

    /**
     * 仅允许站内相对路径，非法则回落默认首页（与云起后端规则对齐）。
     */
    public static String sanitizeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return DEFAULT_REDIRECT;
        }
        String value = redirect.trim();
        if (!value.startsWith("/") || value.startsWith("//")
                || value.contains("://") || value.contains("\\")
                || value.contains("\r") || value.contains("\n")) {
            return DEFAULT_REDIRECT;
        }
        return value;
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
