package cn.datafuturex.yunqi.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 异步/SSE 等场景下响应已提交时，权限/认证异常无法再写 403/401，
 * 捕获后仅打印简要提示，避免 Tomcat 输出完整 ERROR 堆栈。
 */
@Slf4j
@Component
public class CommittedResponseSecurityExceptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException | AuthenticationException ex) {
            handleIfCommitted(request, response, ex);
        } catch (ServletException ex) {
            if (response.isCommitted() && isSecurityDenied(ex)) {
                logBrief(request, rootMessage(ex));
                return;
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (response.isCommitted() && isSecurityDenied(ex)) {
                logBrief(request, rootMessage(ex));
                return;
            }
            throw ex;
        }
    }

    private void handleIfCommitted(HttpServletRequest request,
                                   HttpServletResponse response,
                                   RuntimeException ex) {
        if (response.isCommitted()) {
            logBrief(request, ex.getMessage());
            return;
        }
        throw ex;
    }

    private void logBrief(HttpServletRequest request, String message) {
        log.warn("权限拒绝（响应已提交）: {} {} - {}",
                request.getMethod(), request.getRequestURI(),
                message != null ? message : "Access Denied");
    }

    private static boolean isSecurityDenied(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AccessDeniedException || current instanceof AuthenticationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
