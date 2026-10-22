package cn.datafuturex.zhishu.gateway.compat;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容错误体构造器。
 * <p>
 * 协议面必须与 OpenAI 客户端（含桌面端 dsh-plugin-model-gateway 的
 * {@code isQuotaExceededError} 判定）对齐，因此不复用平台的 {@code Result}。
 */
public final class OpenAiErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiErrors() {
    }

    /**
     * 由网关异常构造错误体 JSON。
     *
     * @param e 网关异常
     * @return 错误体 JSON
     */
    public static String body(GatewayException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", e.getType());
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        if (e.getResetAt() != null) {
            error.put("reset_at", Instant.ofEpochSecond(e.getResetAt()).toString());
        }
        if (e.getLimit() != null) {
            error.put("limit", e.getLimit());
        }
        if (e.getUsed() != null) {
            error.put("used", e.getUsed());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        return write(payload);
    }

    /**
     * 序列化任意负载。
     *
     * @param payload 负载
     * @return JSON 字符串
     */
    public static String write(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("序列化网关响应失败", e);
        }
    }
}
