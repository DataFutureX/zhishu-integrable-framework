package cn.datafuturex.zhishu.mcp.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计方向取值与历史值兼容（读侧必须认得迁移前的 IN/OUT）。
 */
class McpCallDirectionTest {

    @Test
    @DisplayName("新值直接命中")
    void newValuesResolve() {
        assertThat(McpCallDirection.fromFilter("SERVE")).isEqualTo(McpCallDirection.SERVE);
        assertThat(McpCallDirection.fromFilter("consume")).isEqualTo(McpCallDirection.CONSUME);
    }

    @Test
    @DisplayName("历史值映射到新语义：OUT=SERVE、IN=CONSUME")
    void legacyValuesMap() {
        assertThat(McpCallDirection.fromFilter("OUT")).isEqualTo(McpCallDirection.SERVE);
        assertThat(McpCallDirection.fromFilter("in")).isEqualTo(McpCallDirection.CONSUME);
        assertThat(McpCallDirection.fromFilter("INBOUND")).isEqualTo(McpCallDirection.CONSUME);
        assertThat(McpCallDirection.fromFilter("OUTBOUND")).isEqualTo(McpCallDirection.SERVE);
    }

    @Test
    @DisplayName("空值与未知值不过滤")
    void blankOrUnknownReturnsNull() {
        assertThat(McpCallDirection.fromFilter(null)).isNull();
        assertThat(McpCallDirection.fromFilter("  ")).isNull();
        assertThat(McpCallDirection.fromFilter("WHATEVER")).isNull();
    }

    @Test
    @DisplayName("枚举名即落库值")
    void enumNameIsStoredValue() {
        assertThat(McpCallDirection.SERVE.name()).isEqualTo("SERVE");
        assertThat(McpCallDirection.CONSUME.name()).isEqualTo("CONSUME");
    }
}
