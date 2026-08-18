package cn.datafuturex.yunqi.biz.operationlog;

import cn.datafuturex.yunqi.common.SecurityUtils;
import cn.datafuturex.yunqi.biz.operationlog.entity.OperationLogEntity;
import cn.datafuturex.yunqi.biz.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 操作日志拦截器，记录已登录用户的写操作
 */
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "operationLogStartTime";

    private final OperationLogService operationLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (!OperationLogUtils.shouldRecord(method, uri)) {
            return;
        }

        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return;
        }

        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        int durationMs = startTime == null ? 0 : (int) (System.currentTimeMillis() - startTime);
        int httpStatus = response.getStatus();
        boolean success = ex == null && httpStatus >= 200 && httpStatus < 400;

        OperationLogEntity entity = new OperationLogEntity();
        entity.setUsername(username);
        entity.setModule(OperationLogUtils.resolveModule(uri));
        entity.setOperation(OperationLogUtils.resolveOperation(method));
        entity.setMethod(method.toUpperCase() + " " + uri);
        entity.setRequestParams(OperationLogUtils.buildRequestParams(request, readRequestBody(request)));
        entity.setResponseCode(httpStatus);
        entity.setIpAddress(OperationLogUtils.getClientIp(request));
        entity.setUserAgent(OperationLogUtils.truncate(request.getHeader("User-Agent"), 500));
        entity.setDurationMs(durationMs);
        entity.setStatus(success ? 1 : 0);
        entity.setErrorMessage(ex != null ? OperationLogUtils.truncate(ex.getMessage(), 500) : null);
        entity.setCreateTime(LocalDateTime.now());

        operationLogService.recordAsync(entity);
    }

    private String readRequestBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length == 0) {
                return null;
            }
            return new String(content, StandardCharsets.UTF_8);
        }
        return null;
    }
}
