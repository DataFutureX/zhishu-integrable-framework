package cn.datafuturex.zhishu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大整数序列化约定：超出 JS 安全整数的 Long 走字符串，其余仍是数字。
 * 前端据此删除了「JSON 原文正则加引号」的兜底逻辑，此用例是双方契约的守门员。
 * 这里必须用 Jackson 3（HTTP 响应实际使用的 Mapper），Jackson 2 的 Module 对出网 JSON 无效。
 */
class JacksonConfigTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .addModule(new JacksonConfig().safeLongModule())
            .build();

    private JsonNode write(Object value) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(value));
    }

    @Test
    @DisplayName("19 位雪花 ID 序列化为字符串")
    void snowflakeIdSerializedAsString() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 1999999999999999999L);
        assertThat(write(payload).get("id").isTextual()).isTrue();
        assertThat(write(payload).get("id").asText()).isEqualTo("1999999999999999999");
    }

    @Test
    @DisplayName("安全范围内的数值仍为数字，不破坏既有契约")
    void safeNumbersStayNumeric() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 100L);
        payload.put("uptimeMillis", 86400000L);
        payload.put("max", 9007199254740991L);

        JsonNode node = write(payload);
        assertThat(node.get("total").isNumber()).isTrue();
        assertThat(node.get("uptimeMillis").isNumber()).isTrue();
        assertThat(node.get("max").isNumber()).isTrue();
    }

    @Test
    @DisplayName("负数超出安全范围同样转字符串")
    void negativeUnsafeNumberSerializedAsString() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offset", -9007199254740992L);
        assertThat(write(payload).get("offset").isTextual()).isTrue();
    }

    @Test
    @DisplayName("null 保持 null")
    void nullStaysNull() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentId", null);
        assertThat(write(payload).get("parentId").isNull()).isTrue();
    }
}
