package cn.datafuturex.zhishu.ai.mcp.support;

import java.util.List;
import java.util.Set;

/**
 * 知枢对外 MCP 默认目录：不含万象监测 Tool（改由 wanxiang-mcp upstream）。
 */
public final class McpOutboundCatalog {

    public static final List<String> DEFAULT_CAPABILITIES = List.of();

    public static final Set<String> DEFAULT_TOOL_NAMES = Set.of();

    private McpOutboundCatalog() {
    }
}
