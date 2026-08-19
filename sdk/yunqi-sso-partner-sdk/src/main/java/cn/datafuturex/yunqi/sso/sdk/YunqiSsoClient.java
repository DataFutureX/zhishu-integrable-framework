package cn.datafuturex.yunqi.sso.sdk;

import cn.datafuturex.yunqi.sso.sdk.internal.TicketSigner;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Objects;

/**
 * 他方接入云起 SSO 的主入口：用伙伴私钥签发短期 Ticket，并可选生成前端回调 URL。
 *
 * <pre>{@code
 * YunqiSsoClient client = YunqiSsoClient.builder()
 *     .issuer("wanxiang")
 *     .kid("wanxiang-2026")
 *     .algorithm(SsoAlgorithm.RS256)
 *     .privateKeyPem(Files.readString(Path.of("wanxiang-private.pem")))
 *     .yunqiWebBase("https://yunqi.example.com")
 *     .build();
 *
 * SsoTicketResult result = client.issueTicket("zhangsan");
 * // 302 / 打开 result.callbackUrl()
 * }</pre>
 */
public final class YunqiSsoClient {

    public static final String AUDIENCE = "yunqi-application-platform";

    private final String issuer;
    private final String kid;
    private final SsoAlgorithm algorithm;
    private final PrivateKey privateKey;
    private final String yunqiWebBase;
    private final String defaultRedirect;
    private final long defaultTtlSeconds;

    private YunqiSsoClient(Builder builder) {
        this.issuer = builder.issuer;
        this.kid = builder.kid;
        this.algorithm = builder.algorithm;
        this.privateKey = builder.privateKey;
        this.yunqiWebBase = builder.yunqiWebBase;
        this.defaultRedirect = builder.defaultRedirect;
        this.defaultTtlSeconds = builder.defaultTtlSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 按用户名签发（TTL / redirect 使用客户端默认值） */
    public SsoTicketResult issueTicket(String username) {
        return issueTicket(SsoTicketRequest.builder(username).build());
    }

    public SsoTicketResult issueTicket(SsoTicketRequest request) {
        Objects.requireNonNull(request, "request");
        long ttl = request.ttlSeconds() > 0 ? request.ttlSeconds() : defaultTtlSeconds;
        String redirect = request.redirect() != null ? request.redirect() : defaultRedirect;
        try {
            long now = System.currentTimeMillis() / 1000L;
            TicketSigner.SignedTicket signed = TicketSigner.sign(
                    algorithm,
                    privateKey,
                    issuer,
                    kid,
                    request.username(),
                    request.subject(),
                    request.displayName(),
                    ttl,
                    now);
            String callbackUrl = null;
            if (yunqiWebBase != null && !yunqiWebBase.isBlank()) {
                callbackUrl = SsoCallbackUrlBuilder.build(yunqiWebBase, signed.ticket(), redirect);
            }
            return new SsoTicketResult(
                    signed.ticket(),
                    callbackUrl,
                    signed.jti(),
                    signed.iat(),
                    signed.exp(),
                    signed.algorithm());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("签发 SSO Ticket 失败: " + e.getMessage(), e);
        }
    }

    public String issuer() {
        return issuer;
    }

    public SsoAlgorithm algorithm() {
        return algorithm;
    }

    public static final class Builder {
        private String issuer;
        private String kid;
        private SsoAlgorithm algorithm = SsoAlgorithm.RS256;
        private PrivateKey privateKey;
        private String privateKeyPem;
        private Path privateKeyFile;
        private String yunqiWebBase;
        private String defaultRedirect = SsoCallbackUrlBuilder.DEFAULT_REDIRECT;
        private long defaultTtlSeconds = 60L;

        private Builder() {
        }

        /** Ticket iss，须与云起登记一致，如 wanxiang / shuzhi-iot */
        public Builder issuer(String issuer) {
            this.issuer = issuer == null ? null : issuer.trim();
            return this;
        }

        /** 密钥 ID，轮换时与云起 public-keys 映射一致 */
        public Builder kid(String kid) {
            this.kid = kid == null || kid.isBlank() ? null : kid.trim();
            return this;
        }

        public Builder algorithm(SsoAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            return this;
        }

        public Builder privateKey(PrivateKey privateKey) {
            this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
            this.privateKeyPem = null;
            this.privateKeyFile = null;
            return this;
        }

        public Builder privateKeyPem(String pem) {
            if (pem == null || pem.isBlank()) {
                throw new IllegalArgumentException("privateKeyPem 不能为空");
            }
            this.privateKeyPem = pem;
            this.privateKey = null;
            this.privateKeyFile = null;
            return this;
        }

        public Builder privateKeyFile(Path pemFile) {
            this.privateKeyFile = Objects.requireNonNull(pemFile, "pemFile");
            this.privateKey = null;
            this.privateKeyPem = null;
            return this;
        }

        /** 云起前端源站，用于生成 callbackUrl；可不配（仅拿 ticket） */
        public Builder yunqiWebBase(String yunqiWebBase) {
            this.yunqiWebBase = yunqiWebBase == null || yunqiWebBase.isBlank() ? null : yunqiWebBase.trim();
            return this;
        }

        public Builder defaultRedirect(String defaultRedirect) {
            this.defaultRedirect = SsoCallbackUrlBuilder.sanitizeRedirect(defaultRedirect);
            return this;
        }

        public Builder defaultTtlSeconds(long defaultTtlSeconds) {
            if (defaultTtlSeconds <= 0 || defaultTtlSeconds > 120) {
                throw new IllegalArgumentException("defaultTtlSeconds 须在 1～120 之间");
            }
            this.defaultTtlSeconds = defaultTtlSeconds;
            return this;
        }

        public YunqiSsoClient build() {
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalStateException("issuer 不能为空");
            }
            if (algorithm == null) {
                throw new IllegalStateException("algorithm 不能为空");
            }
            PrivateKey key = privateKey;
            if (key == null && privateKeyFile != null) {
                key = YunqiSsoKeys.loadPrivateKey(privateKeyFile, algorithm);
            }
            if (key == null && privateKeyPem != null) {
                key = YunqiSsoKeys.loadPrivateKey(privateKeyPem, algorithm);
            }
            if (key == null) {
                throw new IllegalStateException("privateKey 不能为空");
            }
            this.privateKey = key;
            return new YunqiSsoClient(this);
        }
    }
}
