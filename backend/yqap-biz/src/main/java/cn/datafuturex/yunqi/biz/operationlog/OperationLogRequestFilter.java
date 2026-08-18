package cn.datafuturex.yunqi.biz.operationlog;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求体缓存过滤器，供操作日志记录 POST/PUT 请求体
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperationLogRequestFilter extends OncePerRequestFilter {

    /** 操作日志请求体缓存上限（64KB） */
    private static final int CONTENT_CACHE_LIMIT = 64 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldWrap(request)) {
            ContentCachingRequestWrapper wrappedRequest =
                    new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT);
            filterChain.doFilter(wrappedRequest, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldWrap(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method))) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/v1/");
    }
}
