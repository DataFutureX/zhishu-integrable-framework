package cn.datafuturex.zhishu.openapi.sdk;

/**
 * 知枢 Open API 调用异常。
 *
 * @param statusCode HTTP 状态码（0 表示网络/序列化层异常）
 * @param body       响应体原文（可能为 null）
 */
public class OpenApiException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public OpenApiException(int statusCode, String body, String message) {
        super(message);
        this.statusCode = statusCode;
        this.body = body;
    }

    public OpenApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.body = null;
    }

    /** HTTP 状态码，0 表示网络层异常 */
    public int getStatusCode() {
        return statusCode;
    }

    /** 响应体原文，可能为 null */
    public String getBody() {
        return body;
    }
}
