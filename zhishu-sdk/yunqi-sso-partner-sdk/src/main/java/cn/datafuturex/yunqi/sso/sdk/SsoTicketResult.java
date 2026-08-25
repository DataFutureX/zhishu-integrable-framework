package cn.datafuturex.yunqi.sso.sdk;

import java.util.Objects;

/**
 * 签发结果：JWT Ticket 与可选的云起前端回调 URL。
 */
public final class SsoTicketResult {

    private final String ticket;
    private final String callbackUrl;
    private final String jti;
    private final long iat;
    private final long exp;
    private final SsoAlgorithm algorithm;

    public SsoTicketResult(String ticket, String callbackUrl, String jti, long iat, long exp, SsoAlgorithm algorithm) {
        this.ticket = Objects.requireNonNull(ticket, "ticket");
        this.callbackUrl = callbackUrl;
        this.jti = jti;
        this.iat = iat;
        this.exp = exp;
        this.algorithm = algorithm;
    }

    /** 完整 JWT，提交给云起换票或拼入回调 URL */
    public String ticket() {
        return ticket;
    }

    /**
     * 浏览器跳转地址：{YUNQI_WEB}/sso/callback?ticket=...&amp;redirect=...
     * 若客户端未配置 yunqiWebBase 则为 null。
     */
    public String callbackUrl() {
        return callbackUrl;
    }

    public String jti() {
        return jti;
    }

    public long iat() {
        return iat;
    }

    public long exp() {
        return exp;
    }

    public SsoAlgorithm algorithm() {
        return algorithm;
    }
}
