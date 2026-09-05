package cn.datafuturex.zhishu.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Server 对外服务配置属性。
 * <p>
 * 通过 {@code zhishu.mcp.server.*} 配置，支持环境变量覆盖。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.mcp.server")
public class McpServerProperties {

    /** 是否启用 MCP Server 对外模块 */
    private boolean enabled = true;

    /** API Key 鉴权密钥；空字符串 = 不鉴权（开发模式） */
    private String authKey = "";

    /** 鉴权请求头名称 */
    private String authHeader = "X-API-Key";
}
