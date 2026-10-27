package cn.datafuturex.zhishu.mcp.web.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "对外 MCP 默认能力与 Tool 目录")
public record McpProvideCatalogVO(
        List<String> capabilities,
        List<String> toolNames
) {
}
