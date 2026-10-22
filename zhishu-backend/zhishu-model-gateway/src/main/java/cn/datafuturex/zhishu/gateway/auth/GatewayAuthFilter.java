package cn.datafuturex.zhishu.gateway.auth;

import cn.datafuturex.zhishu.ai.shared.UserContext;
import cn.datafuturex.zhishu.gateway.auth.impl.GatewayAuthServiceImpl;
import cn.datafuturex.zhishu.gateway.compat.GatewayException;
import cn.datafuturex.zhishu.gateway.compat.OpenAiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 网关鉴权过滤器：作用于 {@code /v1/**}，先于 Spring Security 链执行。
 * <p>
 * 校验通过后把主体写入 request attribute 与 {@link UserContext}，失败直接写 OpenAI 错误体。
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

    /** 主体在 request 中的属性名 */
    public static final String PRINCIPAL_ATTRIBUTE = "zhishu.gateway.principal";

    private static final String V1_PREFIX = "/v1/";

    private final GatewayAuthService gatewayAuthService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return path == null || !path.startsWith(V1_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = GatewayAuthServiceImpl.stripBearer(request.getHeader("Authorization"));
            GatewayPrincipal principal = gatewayAuthService.authenticate(token);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            UserContext.setUserId(principal.username());
            UserContext.setUsername(principal.username());
            chain.doFilter(request, response);
        } catch (GatewayException e) {
            log.info("[网关鉴权失败] uri={}, code={}, 原因: {}",
                    request.getRequestURI(), e.getCode(), e.getMessage());
            writeError(response, e);
        } finally {
            UserContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, GatewayException e) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(e.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(OpenAiErrors.body(e));
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
