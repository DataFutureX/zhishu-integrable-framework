package cn.datafuturex.zhishu.ai.mcp.config;

import cn.datafuturex.zhishu.ai.biztools.api.BizToolProviderPort;
import cn.datafuturex.zhishu.ai.mcp.service.McpCallLogService;
import cn.datafuturex.zhishu.ai.mcp.support.McpGuardedToolCallback;
import cn.datafuturex.zhishu.ai.mcp.support.McpOutboundCatalog;
import cn.datafuturex.zhishu.ai.mcp.support.McpRateLimiter;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class McpOutboundToolsConfiguration {

    @Bean
    @ConditionalOnProperty(name = "wanxiang.mcp.server-enabled", havingValue = "true", matchIfMissing = false)
    public ToolCallbackProvider wanxiangOutboundMcpTools(
            BizToolProviderPort bizToolProviderPort,
            McpCallLogService callLogService,
            McpRateLimiter rateLimiter) {
        ToolCallback[] all = ToolCallbacks.from(bizToolProviderPort.toolBeans());
        ToolCallback[] outbound = Arrays.stream(all)
                .filter(cb -> McpOutboundCatalog.DEFAULT_TOOL_NAMES.contains(cb.getToolDefinition().name()))
                .map(cb -> (ToolCallback) new McpGuardedToolCallback(cb, callLogService, rateLimiter))
                .toArray(ToolCallback[]::new);
        return () -> outbound;
    }
}
