package cn.datafuturex.zhishu.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 允许前端管理台通过 iframe 嵌入 Swagger UI（屏蔽默认 X-Frame-Options）。
 */
@Component
public class SwaggerFrameOptionsFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !(
                path.startsWith("/swagger-ui")
                        || "/swagger-ui.html".equals(path)
                        || path.startsWith("/v3/api-docs")
                        || path.startsWith("/webjars/")
                        || "/doc.html".equals(path)
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, new FrameHeaderStrippingResponse(response));
    }

    private static final class FrameHeaderStrippingResponse extends HttpServletResponseWrapper {

        private FrameHeaderStrippingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (isBlockedFrameHeader(name)) {
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isBlockedFrameHeader(name)) {
                return;
            }
            super.addHeader(name, value);
        }

        private static boolean isBlockedFrameHeader(String name) {
            return "X-Frame-Options".equalsIgnoreCase(name)
                    || "Content-Security-Policy".equalsIgnoreCase(name);
        }
    }
}
