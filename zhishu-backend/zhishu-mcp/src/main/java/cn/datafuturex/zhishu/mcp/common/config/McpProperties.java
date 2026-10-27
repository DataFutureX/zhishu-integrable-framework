package cn.datafuturex.zhishu.mcp.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 模块配置（{@code zhishu.mcp.*}）。
 * <p>
 * 两个平面各自一组开关与限额，互不读取对方字段；旧的
 * {@code wanxiang.mcp.*} 与 {@code zhishu.mcp.server.*} 已一次性删除，不留双读。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.mcp")
public class McpProperties {

    /** 模块总开关；关闭后 provide/consume 均不装配 */
    private boolean enabled = true;

    /** AES 密钥；空则上游 Authorization 明文存储（生产建议配置） */
    private String cryptoKey = "";

    /** 我方对外提供 MCP 服务 */
    private final Provide provide = new Provide();

    /** 接入他方提供的 MCP 服务 */
    private final Consume consume = new Consume();

    /** 整体可用（总开关 + 平面开关） */
    public boolean provideEnabled() {
        return enabled && provide.isEnabled();
    }

    /** 整体可用（总开关 + 平面开关） */
    public boolean consumeEnabled() {
        return enabled && consume.isEnabled();
    }

    @Data
    public static class Provide {

        /** 是否装配对外 Tool 目录（{@code /mcp} 的 tools/list 来源） */
        private boolean enabled = true;

        /** 协议端点，仅用于概览展示；真实路径由 {@code spring.ai.mcp.server.streamable-http.mcp-endpoint} 决定 */
        private String endpoint = "/mcp";

        /** 凭据 A：外部 Client API Key（{@code Bearer wxmcp_*}） */
        private boolean clientKeyEnabled = true;

        /** 凭据 B：平台登录态 JWT */
        private boolean platformJwtEnabled = true;

        /** 新建对外 Client 的默认 RPM */
        private int defaultRpm = 60;

        /** JWT 调用方的 RPM（按用户名计数） */
        private int jwtDefaultRpm = 120;
    }

    @Data
    public static class Consume {

        /** 是否允许连接他方 MCP（探活、预热、Agent 工具解析） */
        private boolean enabled = true;

        /** 单个智能体可绑定的上游数量上限 */
        private int maxUpstreamsPerAgent = 5;

        /** 单个上游入库的工具条数上限（超出截断） */
        private int maxToolsPerUpstream = 40;

        /** 启动时是否预热连接 ENABLED 上游 */
        private boolean warmupOnStartup = true;
    }
}
