package cn.datafuturex.zhishu.biz.operationlog;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志辅助工具
 */
public final class OperationLogUtils {

    private static final int MAX_PARAMS_LENGTH = 2000;

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "token", "captchatoken", "keyid", "authorization"
    );

    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("users", "用户管理"),
            Map.entry("roles", "角色管理"),
            Map.entry("menus", "菜单管理"),
            Map.entry("units", "单位管理"),
            Map.entry("system-config", "系统设置"),
            Map.entry("announcements", "公告管理"),
            Map.entry("operation-logs", "操作日志"),
            Map.entry("system", "运维监控"),
            Map.entry("auth", "认证")
    );

    private OperationLogUtils() {
    }

    public static String resolveModule(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "未知模块";
        }
        String path = uri.startsWith("/api/v1/") ? uri.substring("/api/v1/".length()) : uri;
        int slashIndex = path.indexOf('/');
        String segment = slashIndex > 0 ? path.substring(0, slashIndex) : path;
        return MODULE_NAMES.getOrDefault(segment, segment);
    }

    public static String resolveOperation(String httpMethod) {
        if (httpMethod == null) {
            return "UNKNOWN";
        }
        return switch (httpMethod.toUpperCase()) {
            case "GET" -> "QUERY";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> httpMethod.toUpperCase();
        };
    }

    public static boolean shouldRecord(String method, String uri) {
        if (!StringUtils.hasText(uri) || !uri.startsWith("/api/v1/")) {
            return false;
        }
        if (uri.startsWith("/api/v1/operation-logs")) {
            return false;
        }
        if (uri.startsWith("/api/v1/system/health")) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        return true;
    }

    public static String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    public static String buildRequestParams(jakarta.servlet.http.HttpServletRequest request, String body) {
        Map<String, Object> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values == null) {
                return;
            }
            if (values.length == 1) {
                params.put(key, maskIfSensitive(key, values[0]));
            } else {
                params.put(key, maskIfSensitive(key, values));
            }
        });
        if (StringUtils.hasText(body)) {
            params.put("_body", maskSensitiveBody(body));
        }
        if (params.isEmpty()) {
            return null;
        }
        String json = params.toString();
        return json.length() <= MAX_PARAMS_LENGTH ? json : json.substring(0, MAX_PARAMS_LENGTH);
    }

    private static Object maskIfSensitive(String key, Object value) {
        if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase())) {
            return "******";
        }
        return value;
    }

    private static String maskSensitiveBody(String body) {
        String masked = body;
        for (String key : SENSITIVE_KEYS) {
            masked = masked.replaceAll(
                    "(?i)(\"" + key + "\"\\s*:\\s*\")([^\"]*)(\")",
                    "$1******$3");
        }
        return masked.length() <= MAX_PARAMS_LENGTH ? masked : masked.substring(0, MAX_PARAMS_LENGTH);
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
