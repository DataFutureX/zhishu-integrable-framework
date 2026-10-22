package cn.datafuturex.zhishu.gateway.compat;

import cn.datafuturex.zhishu.gateway.config.GatewayReadiness;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 网关就绪闸门：建表/迁移未完成时以 OpenAI 错误体返回 503，而不是抛 SQL 异常。
 */
@RequiredArgsConstructor
public class GatewayReadyFilter extends OncePerRequestFilter {

    private static final String V1_PREFIX = "/v1/";

    private final GatewayReadiness readiness;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return path == null || !path.startsWith(V1_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (readiness.isReady()) {
            chain.doFilter(request, response);
            return;
        }
        String reason = readiness.reason() == null ? "模型网关尚未就绪" : readiness.reason();
        GatewayException error = GatewayException.notReady(reason);
        response.setStatus(error.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OpenAiErrors.body(error));
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri != null && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
