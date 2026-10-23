package cn.datafuturex.zhishu.gateway.compat;

import cn.datafuturex.zhishu.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * token 估算器单元测试。
 * <p>
 * 估算是额度预留与「上游未回 usage」时的兜底口径，直接影响台账与 429 判定，
 * 因此把取整方向、空输入、消息开销、多模态与 tools 计入等边界固定下来。
 */
class TokenEstimatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TokenEstimator estimator;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.getQuota().setEstimateCharsPerToken(2.0);
        estimator = new TokenEstimator(properties);
    }

    @Test
    @DisplayName("空输入估为 0，非空最少 1")
    void textBounds() {
        assertEquals(0L, estimator.estimateText(null));
        assertEquals(0L, estimator.estimateText("  "));
        assertEquals(1L, estimator.estimateText("a"));
    }

    @Test
    @DisplayName("按字符数除以 chars-per-token 向上取整")
    void textCeilsUp() {
        assertEquals(2L, estimator.estimateText("abcd"));
        // 3 字符 / 2.0 = 1.5 → 必须进位到 2，避免长期低估预留
        assertEquals(2L, estimator.estimateText("abc"));
    }

    @Test
    @DisplayName("更长文本不会估得更小")
    void monotonic() {
        long shortOne = estimator.estimateText("短提示");
        long longOne = estimator.estimateText("短提示".repeat(20));
        assertTrue(longOne > shortOne);
    }

    @Test
    @DisplayName("请求体：含消息开销、tools 与 system 都计入")
    void requestIncludesOverhead() throws Exception {
        assertEquals(0L, estimator.estimateRequest(null));

        // 无 messages 时按最小 1 计，避免除零与零预留
        assertEquals(1L, estimator.estimateRequest(MAPPER.readTree("{}")));

        // 单条消息：11 字符 + 4 开销 = 15 → /2 向上取整 = 8
        JsonNode basic = MAPPER.readTree("""
                {"messages":[{"role":"user","content":"hello world"}]}
                """);
        assertEquals(8L, estimator.estimateRequest(basic));

        // 多模态 content 数组：累加各 part 的 text（2+2）+ 4 开销 = 8 → 4
        JsonNode multimodal = MAPPER.readTree("""
                {"messages":[{"role":"user","content":[{"type":"text","text":"ab"},{"type":"text","text":"cd"}]}]}
                """);
        assertEquals(4L, estimator.estimateRequest(multimodal));

        // system 文本参与估算：3 字符 → 2
        JsonNode systemOnly = MAPPER.readTree("""
                {"system":"sys"}
                """);
        assertEquals(2L, estimator.estimateRequest(systemOnly));

        // tools 定义计入，估算值应高于同消息不带 tools 的情况
        JsonNode withTools = MAPPER.readTree("""
                {"messages":[{"role":"user","content":"hello world"}],
                 "tools":[{"type":"function","function":{"name":"query_device","description":"查询设备状态"}}]}
                """);
        assertTrue(estimator.estimateRequest(withTools) > estimator.estimateRequest(basic));
    }
}

