package cn.datafuturex.yunqi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 伙伴单点登录（Ticket 换票）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunqi.sso")
public class SsoProperties {

    /**
     * 是否启用 SSO 换票
     */
    private boolean enabled = false;

    /**
     * Ticket 期望的 aud
     */
    private String audience = "yunqi-application-platform";

    /**
     * 时钟偏差容许（秒）
     */
    private long clockSkewSeconds = 30;

    /**
     * jti 已用记录 TTL（秒）
     */
    private long jtiTtlSeconds = 180;

    /**
     * 换票成功后的默认站内跳转路径
     */
    private String defaultRedirect = "/home/dashboard";

    /**
     * 换票接口按 IP 限流：窗口秒数
     */
    private int rateLimitWindowSeconds = 60;

    /**
     * 换票接口按 IP 限流：窗口内最大次数
     */
    private int rateLimitMaxRequests = 30;

    /**
     * 伙伴登记表，key 建议与 issuer 一致
     */
    private Map<String, Partner> partners = new LinkedHashMap<>();

    @Data
    public static class Partner {

        /**
         * 是否启用该伙伴
         */
        private boolean enabled = true;

        /**
         * Ticket iss，须与伙伴书面确认一致
         */
        private String issuer;

        /**
         * 展示名（日志用）
         */
        private String displayName;

        /**
         * 允许的 Ticket 算法（逗号分隔）。支持 RS256、SM2；空则两者均可。
         * 实际验签按票据 Header.alg 选择，不强制伙伴只使用一种。
         */
        private String algorithm = "RS256,SM2";

        /**
         * 默认公钥资源（classpath: 或 file:）
         */
        private String publicKey;

        /**
         * 按 kid 映射的多把公钥（轮换）；未命中 kid 时回退 {@link #publicKey}
         */
        private Map<String, String> publicKeys = new LinkedHashMap<>();

        /**
         * Ticket 最大存活秒数（exp - iat）
         */
        private long ticketTtlMaxSeconds = 120;

        /**
         * 映射云起用户名的 claim；空则回退 sub
         */
        private String usernameClaim = "username";
    }
}
