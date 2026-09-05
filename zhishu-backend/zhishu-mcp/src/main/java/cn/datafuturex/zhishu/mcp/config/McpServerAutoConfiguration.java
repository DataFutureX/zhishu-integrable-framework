package cn.datafuturex.zhishu.mcp.config;

import cn.datafuturex.zhishu.mcp.security.McpAuthFilter;
import cn.datafuturex.zhishu.mcp.tool.SystemIntroductionTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 对外模块自动装配。
 * <p>
 * 注册系统 Tool 与 API Key 鉴权过滤器。通过 {@code zhishu.mcp.server.enabled} 控制开关。
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(name = "zhishu.mcp.server.enabled", havingValue = "true", matchIfMissing = true)
public class McpServerAutoConfiguration {

    /**
     * 注册系统介绍 Tool 到 MCP Server（通过 ToolCallbackProvider 暴露给 MCP 协议）。
     *
     * @param properties MCP Server 配置属性
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider zhishuMcpSystemTools(McpServerProperties properties) {
        ToolCallback systemIntro = new SystemIntroductionTool(properties);
        return () -> new ToolCallback[]{systemIntro};
    }

    /**
     * 注册 MCP 端点鉴权过滤器。
     *
     * @param properties MCP Server 配置属性
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<McpAuthFilter> mcpAuthFilterRegistration(McpServerProperties properties) {
        var registration = new FilterRegistrationBean<>(new McpAuthFilter(properties));
        registration.addUrlPatterns("/mcp/*");
        registration.addUrlPatterns("/mcp");
        registration.setOrder(Integer.MIN_VALUE + 100);
        registration.setName("mcpServerAuthFilter");
        return registration;
    }
}
