package cn.datafuturex.zhishu.biz.operationlog.spi;

import cn.datafuturex.zhishu.api.spi.AuthAuditApi;
import cn.datafuturex.zhishu.biz.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证审计适配（登录成功/失败写入操作日志）
 */
@Service
@RequiredArgsConstructor
public class AuthAuditApiImpl implements AuthAuditApi {

    private final OperationLogService operationLogService;

    @Override
    public void recordLogin(String username, String ipAddress, String userAgent,
                            boolean success, String errorMessage, String channel) {
        operationLogService.recordLogin(username, ipAddress, userAgent, success, errorMessage, channel);
    }
}
