package cn.datafuturex.zhishu.security.sso;

/**
 * 登录审计渠道编码（写入操作日志 requestParams.channel）
 */
public final class LoginChannel {

    public static final String LOCAL = "LOCAL";
    public static final String WANXIANG = "WANXIANG";
    public static final String SHUZHI_IOT = "SHUZHI_IOT";

    private LoginChannel() {
    }

    /**
     * 由伙伴 issuer 推导审计渠道；未知来源返回大写并替换连字符
     */
    public static String fromIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return "UNKNOWN";
        }
        return switch (issuer.trim().toLowerCase()) {
            case "wanxiang" -> WANXIANG;
            case "shuzhi-iot" -> SHUZHI_IOT;
            default -> issuer.trim().toUpperCase().replace('-', '_');
        };
    }
}
