package cn.datafuturex.yunqi.api.spi;

/**
 * 认证审计（由业务模块实现，供登录流程记录操作日志）
 */
public interface AuthAuditApi {

    /**
     * 记录登录审计（默认渠道 LOCAL）
     */
    default void recordLogin(String username, String ipAddress, String userAgent,
                             boolean success, String errorMessage) {
        recordLogin(username, ipAddress, userAgent, success, errorMessage, "LOCAL");
    }

    /**
     * 记录登录审计
     *
     * @param channel 渠道编码：LOCAL / WANXIANG / SHUZHI_IOT 等
     */
    void recordLogin(String username, String ipAddress, String userAgent,
                     boolean success, String errorMessage, String channel);
}
