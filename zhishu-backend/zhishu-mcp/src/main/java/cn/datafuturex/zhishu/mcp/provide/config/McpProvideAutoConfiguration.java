package cn.datafuturex.zhishu.mcp.provide.config;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.mcp.provide.guard.McpGuardedToolCallback;
import cn.datafuturex.zhishu.mcp.provide.guard.McpRateLimiter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * provide 平面装配：把目录里的候选 Tool 全部加守卫后注册给 MCP Server。
 * <p>
 * Spring AI 的 MCP Server 自动装配会收集容器内所有 {@link ToolCallbackProvider}，
 * 本模块是唯一提供方（{@code zhishu-ai} 不再声明 MCP starter）。
 */
@Configuration
public class McpProvideAutoConfiguration {

    /**
     * 对外 Tool 提供者；总开关或 provide 开关关闭时不注册（{@code /mcp} 仍可握手但目录为空）。
     */
    @Bean
    @ConditionalOnProperty(
            name = {"zhishu.mcp.enabled", "zhishu.mcp.provide.enabled"},
            havingValue = "true",
            matchIfMissing = true)
    public ToolCallbackProvider zhishuMcpProvidedTools(
            McpProvidedToolCatalog catalog,
            McpCallLogService callLogService,
            McpRateLimiter rateLimiter) {
        List<ToolCallback> candidates = catalog.candidates();
        ToolCallback[] guarded = candidates.stream()
                .map(callback -> (ToolCallback) new McpGuardedToolCallback(callback, callLogService, rateLimiter))
                .toArray(ToolCallback[]::new);
        return () -> guarded;
    }
}
