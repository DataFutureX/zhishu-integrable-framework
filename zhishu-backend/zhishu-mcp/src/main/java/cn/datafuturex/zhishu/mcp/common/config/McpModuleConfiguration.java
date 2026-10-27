package cn.datafuturex.zhishu.mcp.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 模块装配入口：启用 {@code zhishu.mcp.*} 配置绑定。
 * <p>
 * 两个平面的自动装配类各自负责自己的 Bean，本类只做配置绑定，不含业务规则。
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpModuleConfiguration {
}
