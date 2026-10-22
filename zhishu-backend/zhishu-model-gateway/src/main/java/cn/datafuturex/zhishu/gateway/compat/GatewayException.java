package cn.datafuturex.zhishu.gateway.compat;

/**
 * 网关业务异常：携带对外 HTTP 状态与 OpenAI 错误码。
 * <p>
 * 协议面不走 {@code Result} 包装，异常统一由 {@code GatewayExceptionAdvice}
 * 或 {@code GatewayAuthFilter} 序列化为 OpenAI 错误体。
 */
public class GatewayException extends RuntimeException {

    private final int status;
    private final String type;
    private final String code;
    private final Long resetAt;
    private final Long limit;
    private final Long used;

    private GatewayException(int status, String type, String code, String message,
                             Long resetAt, Long limit, Long used) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
        this.resetAt = resetAt;
        this.limit = limit;
        this.used = used;
    }

    /**
     * 401：缺少/无效 token。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException invalidApiKey(String message) {
        return new GatewayException(401, "invalid_request_error", "invalid_api_key", message, null, null, null);
    }

    /**
     * 401：token 已吊销。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException tokenRevoked(String message) {
        return new GatewayException(401, "invalid_request_error", "token_revoked", message, null, null, null);
    }

    /**
     * 403：账号被禁用。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException accountDisabled(String message) {
        return new GatewayException(403, "permission_error", "account_disabled", message, null, null, null);
    }

    /**
     * 404：模型不存在或不可见。
     *
     * @param modelId 模型 ID
     * @return 异常
     */
    public static GatewayException modelNotFound(String modelId) {
        return new GatewayException(404, "invalid_request_error", "model_not_found",
                "模型不存在或当前账号不可用: " + modelId, null, null, null);
    }

    /**
     * 400：请求非法。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException invalidRequest(String message) {
        return new GatewayException(400, "invalid_request_error", "invalid_request_error", message, null, null, null);
    }

    /**
     * 429：额度不足。
     *
     * @param message  错误说明
     * @param resetAt  周期重置时间（epoch 秒）
     * @param limit    周期上限
     * @param used     已用量
     * @return 异常
     */
    public static GatewayException insufficientQuota(String message, Long resetAt, Long limit, Long used) {
        return new GatewayException(429, "quota_exceeded", "insufficient_quota", message, resetAt, limit, used);
    }

    /**
     * 429：限流。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException rateLimited(String message) {
        return new GatewayException(429, "rate_limit_error", "rate_limit_exceeded", message, null, null, null);
    }

    /**
     * 500：网关内部错误。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException internal(String message) {
        return new GatewayException(500, "server_error", "internal_error", message, null, null, null);
    }

    /**
     * 503：网关未就绪（建表/迁移未完成）。
     *
     * @param message 可操作的原因
     * @return 异常
     */
    public static GatewayException notReady(String message) {
        return new GatewayException(503, "gateway_unavailable", "gateway_not_ready", message, null, null, null);
    }

    /**
     * 503：上游不可达或未配置。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException upstreamUnavailable(String message) {
        return new GatewayException(503, "upstream_error", "upstream_unavailable", message, null, null, null);
    }

    /**
     * 504：上游超时。
     *
     * @param message 错误说明
     * @return 异常
     */
    public static GatewayException upstreamTimeout(String message) {
        return new GatewayException(504, "upstream_error", "upstream_timeout", message, null, null, null);
    }

    /**
     * 上游返回错误，需要归一化状态码。
     *
     * @param upstreamStatus 上游状态码
     * @param message        归一化后的说明
     * @return 异常
     */
    public static GatewayException upstreamError(int upstreamStatus, String message) {
        int status = upstreamStatus >= 400 && upstreamStatus < 500 ? upstreamStatus : 502;
        String type = "upstream_error";
        String code = status == 429 ? "rate_limit_exceeded" : "upstream_error";
        return new GatewayException(status, type, code, message, null, null, null);
    }

    public int getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public Long getResetAt() {
        return resetAt;
    }

    public Long getLimit() {
        return limit;
    }

    public Long getUsed() {
        return used;
    }
}
