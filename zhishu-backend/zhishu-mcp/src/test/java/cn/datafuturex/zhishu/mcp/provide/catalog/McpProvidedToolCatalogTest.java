package cn.datafuturex.zhishu.mcp.provide.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对外目录与能力白名单：决定「谁能调用哪些 Tool」。
 */
class McpProvidedToolCatalogTest {

    @Test
    @DisplayName("系统介绍 Tool 常驻公开目录")
    void systemToolIsAlwaysPublic() {
        assertThat(McpProvidedToolCatalog.PUBLIC_TOOLS).contains("getSystemIntroduction");
        assertThat(McpProvidedToolCatalog.isPublic("getSystemIntroduction")).isTrue();
        assertThat(McpProvidedToolCatalog.isPublic("getTerminalOnlineOverview")).isFalse();
    }

    @Test
    @DisplayName("空能力回落到公开目录（不再是空集合）")
    void emptyCapabilitiesFallsBackToPublicTools() {
        assertThat(McpProvidedToolCatalog.allowedTools(List.of()))
                .isEqualTo(McpProvidedToolCatalog.PUBLIC_TOOLS);
        assertThat(McpProvidedToolCatalog.allowedTools(null))
                .isEqualTo(McpProvidedToolCatalog.PUBLIC_TOOLS);
    }

    @Test
    @DisplayName("能力码映射到对应 Tool，并附带公开目录")
    void capabilityMapsToTools() {
        assertThat(McpProvidedToolCatalog.allowedTools(List.of("ONLINE")))
                .contains("getTerminalOnlineOverview", "queryTerminalOnlineStatus", "listTerminals",
                        "getSystemIntroduction");
        assertThat(McpProvidedToolCatalog.allowedTools(List.of("INSPECTION_PLAN", "alert")))
                .contains("listInspectionPlans", "getInspectionPlan", "queryRecentAlerts", "queryAlertTrends");
    }

    @Test
    @DisplayName("未登记的能力码不额外授权（NL2SQL / RAG / MCP_TOOLS 等）")
    void unknownCapabilityGrantsNothingMore() {
        assertThat(McpProvidedToolCatalog.allowedTools(List.of("MCP_TOOLS", "RAG", "NL2SQL")))
                .isEqualTo(McpProvidedToolCatalog.PUBLIC_TOOLS);
    }

    @Test
    @DisplayName("能力字典与准入集合一致（贡献 Tool 只有登记后才对外）")
    void capabilityDictionaryIsExposed() {
        assertThat(McpProvidedToolCatalog.capabilityTools())
                .containsKeys("STATION_LATEST", "ONLINE", "ALERT", "INSPECTION_SUMMARY", "KNOWLEDGE_GRAPH");
    }
}
