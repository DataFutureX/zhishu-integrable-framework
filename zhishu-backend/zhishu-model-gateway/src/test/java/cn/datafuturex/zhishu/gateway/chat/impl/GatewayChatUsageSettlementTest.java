package cn.datafuturex.zhishu.gateway.chat.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关 token 结算回归测试。
 * <p>
 * 锁死两类历史缺陷：流式帧归约先剥一层导致 {@code extractUsage} 落空（台账静默变估算），
 * 以及 {@code total_tokens} 缺失时未由 prompt/completion 补齐。
 */
class GatewayChatUsageSettlementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 末帧归约应保留整帧，而不是内层 usage 对象。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("流式末帧：captureUsage 保留整帧并可被 extractUsage 读取")
    void captureUsageKeepsWholeFrame() throws Exception {
        JsonNode frame = MAPPER.readTree("""
                {"id":"c1","object":"chat.completion.chunk","choices":[],
                 "usage":{"prompt_tokens":35,"completion_tokens":16,"total_tokens":51,
                          "prompt_tokens_details":{"cached_tokens":7},
                          "completion_tokens_details":{"reasoning_tokens":14}}}
                """);

        JsonNode captured = GatewayChatServiceImpl.captureUsage(null, frame);

        assertSame(frame, captured);
        GatewayChatServiceImpl.UsageNumbers numbers = GatewayChatServiceImpl.extractUsage(captured, 30, 20);
        assertFalse(numbers.estimated());
        assertEquals(35, numbers.prompt());
        assertEquals(16, numbers.completion());
        assertEquals(51, numbers.total());
        assertEquals(7, numbers.cached());
        assertEquals(14, numbers.reasoning());
    }

    /**
     * 无 usage 的增量帧不应覆盖已累计的帧。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("流式增量帧：无 usage 时保持既有累计值")
    void captureUsageKeepsPreviousWhenNoUsage() throws Exception {
        JsonNode usageFrame = MAPPER.readTree("""
                {"usage":{"total_tokens":51}}
                """);
        JsonNode deltaFrame = MAPPER.readTree("""
                {"choices":[{"delta":{"content":"hi"}}]}
                """);

        assertNull(GatewayChatServiceImpl.captureUsage(null, deltaFrame));
        assertSame(usageFrame, GatewayChatServiceImpl.captureUsage(usageFrame, deltaFrame));
        assertNull(GatewayChatServiceImpl.captureUsage(null, null));
    }

    /**
     * 反向锁死缺陷形态：把内层 usage 对象直接交给结算方法时应走估算，而不是静默失真。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("误传内层 usage 对象：应落到估算分支以便被发现")
    void passingInnerUsageObjectFallsBackToEstimate() throws Exception {
        JsonNode frame = MAPPER.readTree("""
                {"usage":{"prompt_tokens":35,"completion_tokens":16,"total_tokens":51}}
                """);

        GatewayChatServiceImpl.UsageNumbers numbers =
                GatewayChatServiceImpl.extractUsage(frame.path("usage"), 30, 20);

        assertTrue(numbers.estimated());
        assertEquals(50, numbers.total());
    }

    /**
     * 非流式响应按整个响应体结算。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("非流式响应：按整个响应体入账")
    void extractUsageFromWholeResponse() throws Exception {
        JsonNode response = MAPPER.readTree("""
                {"id":"c2","object":"chat.completion",
                 "choices":[{"message":{"content":"收到"}}],
                 "usage":{"prompt_tokens":120,"completion_tokens":40,"total_tokens":160}}
                """);

        GatewayChatServiceImpl.UsageNumbers numbers = GatewayChatServiceImpl.extractUsage(response, 100, 30);

        assertFalse(numbers.estimated());
        assertEquals(120, numbers.prompt());
        assertEquals(40, numbers.completion());
        assertEquals(160, numbers.total());
        assertNull(numbers.cached());
        assertNull(numbers.reasoning());
    }

    /**
     * 缺 total_tokens 时由 prompt + completion 补齐。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("缺 total_tokens：由 prompt + completion 补齐且不算估算")
    void missingTotalIsDerived() throws Exception {
        JsonNode frame = MAPPER.readTree("""
                {"usage":{"prompt_tokens":35,"completion_tokens":16}}
                """);

        GatewayChatServiceImpl.UsageNumbers numbers = GatewayChatServiceImpl.extractUsage(frame, 0, 0);

        assertFalse(numbers.estimated());
        assertEquals(51, numbers.total());
    }

    /**
     * 上游完全没给 usage 时走估算并显式标记。
     *
     * @throws Exception JSON 解析失败
     */
    @Test
    @DisplayName("无 usage：落到估算分支并标记 estimated")
    void noUsageUsesEstimate() throws Exception {
        JsonNode frame = MAPPER.readTree("""
                {"choices":[{"delta":{"content":"hi"}}]}
                """);

        GatewayChatServiceImpl.UsageNumbers numbers = GatewayChatServiceImpl.extractUsage(frame, 30, 20);

        assertTrue(numbers.estimated());
        assertEquals(30, numbers.prompt());
        assertEquals(20, numbers.completion());
        assertEquals(50, numbers.total());
    }
}
