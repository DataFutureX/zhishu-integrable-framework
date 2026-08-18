package cn.datafuturex.yunqi.api.spi;

/**
 * 认证审计（由业务模块实现，供登录流程记录操作日志）
 */
public interface AuthAuditApi {

    void recordLogin(String username, String ipAddress, String userAgent, boolean success, String errorMessage);
}
