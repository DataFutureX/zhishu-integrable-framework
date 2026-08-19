package com.datafuturex.assistant.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wanxiang.mcp")
public class McpProperties {

    private boolean enabled = true;

    private boolean serverEnabled = true;

    private boolean clientEnabled = true;

    /** AES 密钥；空则明文存储上游 Authorization */
    private String cryptoKey = "";

    private int defaultRpm = 60;

    private int maxUpstreamsPerAgent = 5;

    private int maxToolsPerUpstream = 40;
}
