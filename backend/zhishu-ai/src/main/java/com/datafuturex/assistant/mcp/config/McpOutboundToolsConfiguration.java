package com.datafuturex.assistant.mcp.config;

import com.datafuturex.assistant.biztools.api.BizToolProviderPort;
import com.datafuturex.assistant.mcp.service.McpCallLogService;
import com.datafuturex.assistant.mcp.support.McpGuardedToolCallback;
import com.datafuturex.assistant.mcp.support.McpOutboundCatalog;
import com.datafuturex.assistant.mcp.support.McpRateLimiter;
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
    @ConditionalOnProperty(name = "wanxiang.mcp.server-enabled", havingValue = "true", matchIfMissing = true)
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
