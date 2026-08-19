package cn.datafuturex.yunqi.sso.sdk;

import java.util.Objects;

/**
 * 签发 Ticket 的请求参数。
 */
public final class SsoTicketRequest {

    private final String username;
    private final String subject;
    private final String displayName;
    private final String redirect;
    private final long ttlSeconds;

    private SsoTicketRequest(Builder builder) {
        this.username = builder.username;
        this.subject = builder.subject != null ? builder.subject : builder.username;
        this.displayName = builder.displayName;
        this.redirect = builder.redirect;
        this.ttlSeconds = builder.ttlSeconds;
    }

    public static Builder builder(String username) {
        return new Builder(username);
    }

    public String username() {
        return username;
    }

    public String subject() {
        return subject;
    }

    public String displayName() {
        return displayName;
    }

    public String redirect() {
        return redirect;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public static final class Builder {
        private final String username;
        private String subject;
        private String displayName;
        private String redirect;
        private long ttlSeconds = 60L;

        private Builder(String username) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("username 不能为空");
            }
            this.username = username.trim();
        }

        /** 缺省与 username 相同；映射云起 sys_user.username 时建议保持一致 */
        public Builder subject(String subject) {
            this.subject = subject == null ? null : subject.trim();
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
            return this;
        }

        /** 登录成功后的云起站内相对路径，如 /home/dashboard；可空 */
        public Builder redirect(String redirect) {
            this.redirect = redirect;
            return this;
        }

        /**
         * Ticket 存活秒数（exp - iat）。建议 60，云起默认上限 120。
         */
        public Builder ttlSeconds(long ttlSeconds) {
            if (ttlSeconds <= 0 || ttlSeconds > 120) {
                throw new IllegalArgumentException("ttlSeconds 须在 1～120 之间，建议 60");
            }
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public SsoTicketRequest build() {
            return new SsoTicketRequest(this);
        }
    }

    @Override
    public String toString() {
        return "SsoTicketRequest{username='" + username + "', ttlSeconds=" + ttlSeconds + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SsoTicketRequest that)) {
            return false;
        }
        return ttlSeconds == that.ttlSeconds
                && Objects.equals(username, that.username)
                && Objects.equals(subject, that.subject)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(redirect, that.redirect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, subject, displayName, redirect, ttlSeconds);
    }
}
