package cn.datafuturex.zhishu.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 模型网关本地参数。
 * <p>
 * 全部显式配置、加载期校验；不提供隐式默认业务行为（默认值仅保证最小可运行）。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.gateway")
public class GatewayProperties {

    /** 网关总开关 */
    private boolean enabled = true;

    /** 推理端点路径 */
    private String chatPath = "/v1/chat/completions";

    /** 模型目录端点路径 */
    private String modelsPath = "/v1/models";

    /** 额度端点路径 */
    private String quotaPath = "/v1/quota";

    private Auth auth = new Auth();

    private Upstream upstream = new Upstream();

    private Quota quota = new Quota();

    private Catalog catalog = new Catalog();

    private Logging log = new Logging();

    private Internal internal = new Internal();

    /**
     * 加载期校验：路径必须落在 /v1 前缀下，模式必须合法。
     *
     * @throws IllegalStateException 配置非法
     */
    public void validate() {
        if (!isV1Path(chatPath)) {
            throw new IllegalStateException("zhishu.gateway.chat-path 必须以 /v1 开头: " + chatPath);
        }
        if (!isV1Path(modelsPath)) {
            throw new IllegalStateException("zhishu.gateway.models-path 必须以 /v1 开头: " + modelsPath);
        }
        if (!isV1Path(quotaPath)) {
            throw new IllegalStateException("zhishu.gateway.quota-path 必须以 /v1 开头: " + quotaPath);
        }
        String mode = auth.getMode();
        if (!"jwt".equalsIgnoreCase(mode) && !"static".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("zhishu.gateway.auth.mode 只能是 jwt 或 static: " + mode);
        }
        if ("static".equalsIgnoreCase(mode) && !StringUtils.hasText(auth.getStaticToken())) {
            throw new IllegalStateException("auth.mode=static 时必须配置 zhishu.gateway.auth.static-token");
        }
        if (upstream.getConnectTimeoutMs() <= 0 || upstream.getRequestTimeoutMs() <= 0
                || upstream.getStreamIdleTimeoutMs() <= 0) {
            throw new IllegalStateException("zhishu.gateway.upstream 超时参数必须大于 0");
        }
        if (quota.getEstimateCharsPerToken() <= 0) {
            throw new IllegalStateException("zhishu.gateway.quota.estimate-chars-per-token 必须大于 0");
        }
        if (!isHttpUrl(internal.getBaseUrl())) {
            throw new IllegalStateException("zhishu.gateway.internal.base-url 必须是 http(s) 地址: "
                    + internal.getBaseUrl());
        }
    }

    private static boolean isV1Path(String path) {
        return StringUtils.hasText(path) && path.startsWith("/v1");
    }

    private static boolean isHttpUrl(String url) {
        return StringUtils.hasText(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 鉴权参数。
     */
    @Data
    public static class Auth {

        /** jwt：校验知枢 JWT；static：允许静态网关 token（无门禁部署） */
        private String mode = "jwt";

        /** 静态 token（仅 static 模式使用） */
        private String staticToken = "";

        /** 静态 token 对应的主体用户名 */
        private String staticUsername = "gateway-service";
    }

    /**
     * 上游转发参数。
     */
    @Data
    public static class Upstream {

        /** 建立连接超时（毫秒） */
        private int connectTimeoutMs = 5000;

        /** 非流式整体超时（毫秒） */
        private int requestTimeoutMs = 120000;

        /** 流式两帧之间最大空闲（毫秒） */
        private int streamIdleTimeoutMs = 60000;

        /** 请求体上限（字节） */
        private int maxRequestBytes = 8 * 1024 * 1024;
    }

    /**
     * 额度参数。
     */
    @Data
    public static class Quota {

        /** 是否强制额度 */
        private boolean enabled = true;

        /** 无任何额度配置时的月度上限 */
        private long defaultMonthlyTokens = 5_000_000L;

        /** 估算：多少字符折算 1 token（中文保守取 2.0） */
        private double estimateCharsPerToken = 2.0d;

        /** 预留时单次输出上限的封顶值 */
        private int reserveMaxOutputTokens = 4096;
    }

    /**
     * 目录缓存参数。
     */
    @Data
    public static class Catalog {

        /** 目录缓存 TTL（秒） */
        private int cacheTtlSeconds = 60;
    }

    /**
     * 日志参数。
     */
    @Data
    public static class Logging {

        /** 是否记录 prompt 正文（默认关闭，仅排障期打开） */
        private boolean promptBodies = false;
    }

    /**
     * 平台内部调用参数。
     */
    @Data
    public static class Internal {

        /** false：内部回退直连上游（灰度/回滚） */
        private boolean enabled = true;

        /** 内部调用网关的回环地址 */
        private String baseUrl = "http://127.0.0.1:8080";

        /** 无用户上下文时的调用主体 */
        private String serviceUsername = "gateway-service";

        /** caller：计入当前登录用户；platform：统一计入服务账号 */
        private String quotaSubject = "caller";

        /** 服务账号 token 续签周期（秒） */
        private int tokenRenewSeconds = 3600;

        /** 通道标识（写入台账 channel） */
        private String channel = "CONSOLE";
    }
}
