package cn.datafuturex.zhishu.config.security;

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
 * SSE / 异步完成后响应已提交时，ExceptionTranslationFilter 无法再写 401/403，
 * 会抛 ServletException 并被 Tomcat 转到 /error，形成完整 ERROR 堆栈。
 * 本过滤器在异步与 ERROR 分发上同样生效，吞掉这类已提交异常。
 */
@Slf4j
@Component
public class CommittedResponseSecurityExceptionFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException | AuthenticationException ex) {
            swallowIfCommitted(request, response, ex);
        } catch (ServletException | RuntimeException ex) {
            if (isCommittedSecurityFailure(response, ex)) {
                logBrief(request, rootMessage(ex));
                return;
            }
            throw ex;
        }
    }

    private void swallowIfCommitted(HttpServletRequest request,
                                    HttpServletResponse response,
                                    RuntimeException ex) throws RuntimeException {
        if (response.isCommitted()) {
            logBrief(request, ex.getMessage());
            return;
        }
        throw ex;
    }

    private void logBrief(HttpServletRequest request, String message) {
        log.debug("安全异常（响应已提交，忽略）: {} {} - {}",
                request.getMethod(), request.getRequestURI(),
                message != null ? message : "Access Denied");
    }

    private static boolean isCommittedSecurityFailure(HttpServletResponse response, Throwable throwable) {
        if (isCommittedMessage(throwable)) {
            return true;
        }
        return response.isCommitted() && isSecurityDenied(throwable);
    }

    private static boolean isCommittedMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("response is already committed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
