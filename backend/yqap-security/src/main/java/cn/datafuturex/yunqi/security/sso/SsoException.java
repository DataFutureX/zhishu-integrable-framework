package cn.datafuturex.yunqi.security.sso;

/**
 * SSO 换票业务异常（映射为 Result.code / message）
 */
public class SsoException extends RuntimeException {

    private final int code;
    private final String channel;
    private final String username;

    public SsoException(int code, String message) {
        this(code, message, null, null);
    }

    public SsoException(int code, String message, String channel, String username) {
        super(message);
        this.code = code;
        this.channel = channel;
        this.username = username;
    }

    public int getCode() {
        return code;
    }

    public String getChannel() {
        return channel;
    }

    public String getUsername() {
        return username;
    }
}
