package cn.datafuturex.zhishu.gateway.compat;

import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 轻量 token 估算器（不引入分词器依赖）。
 * <p>
 * 用途：额度预留与上游未返回 {@code usage} 时的兜底计量。中文按偏保守的
 * {@code estimate-chars-per-token}（默认 2.0）折算，宁多算不少算。
 */
@Component
@RequiredArgsConstructor
public class TokenEstimator {

    private static final int MESSAGE_OVERHEAD_CHARS = 4;

    private final GatewayProperties properties;

    /**
     * 估算一段文本的 token 数。
     *
     * @param text 文本
     * @return token 估算值，空文本返回 0
     */
    public long estimateText(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        double charsPerToken = properties.getQuota().getEstimateCharsPerToken();
        return Math.max(1L, (long) Math.ceil(text.length() / charsPerToken));
    }

    /**
     * 估算一次 Chat Completions 请求的输入 token。
     *
     * @param body OpenAI 兼容请求体
     * @return 输入 token 估算值
     */
    public long estimateRequest(JsonNode body) {
        if (body == null) {
            return 0L;
        }
        long chars = 0L;
        JsonNode messages = body.path("messages");
        if (messages.isArray()) {
            for (JsonNode message : messages) {
                chars += MESSAGE_OVERHEAD_CHARS;
                chars += charsOf(message.path("content"));
                JsonNode toolCalls = message.path("tool_calls");
                if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                    chars += toolCalls.toString().length();
                }
            }
        }
        JsonNode tools = body.path("tools");
        if (tools.isArray() && !tools.isEmpty()) {
            chars += tools.toString().length();
        }
        JsonNode system = body.path("system");
        if (system.isTextual()) {
            chars += system.asText().length();
        }
        if (chars <= 0) {
            return 1L;
        }
        double charsPerToken = properties.getQuota().getEstimateCharsPerToken();
        return Math.max(1L, (long) Math.ceil(chars / charsPerToken));
    }

    private static long charsOf(JsonNode content) {
        if (content.isMissingNode() || content.isNull()) {
            return 0L;
        }
        if (content.isTextual()) {
            return content.asText().length();
        }
        if (content.isArray()) {
            long chars = 0L;
            for (JsonNode part : content) {
                JsonNode text = part.path("text");
                chars += text.isTextual() ? text.asText().length() : part.toString().length();
            }
            return chars;
        }
        return content.toString().length();
    }
}
