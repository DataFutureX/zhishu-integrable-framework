package cn.datafuturex.zhishu.desktop.security;

import cn.datafuturex.zhishu.desktop.config.DesktopApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 桌面端 API 鉴权过滤器。
 * <p>
 * 仅拦截 {@code /desktop/v1/**} 路径。当 {@code authKey} 非空时，
 * 校验请求头中的桌面端密钥；为空时放行（开发模式）。
 * {@code /desktop/v1/health} 无需鉴权，直接放行。
 */
@RequiredArgsConstructor
@Slf4j
public class DesktopAuthFilter extends OncePerRequestFilter {

    private static final String DESKTOP_PATH_PREFIX = "/desktop/v1";
    private static final String HEALTH_ENDPOINT = "/desktop/v1/health";
    private static final String AUTH_PATH_PREFIX = "/desktop/v1/auth";

    private final DesktopApiProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configuredKey = properties.getAuthKey();

        // 未配置鉴权密钥 → 放行（开发模式）
        if (!StringUtils.hasText(configuredKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = properties.getAuthHeader();
        String providedKey = request.getHeader(headerName);

        if (!StringUtils.hasText(providedKey) || !configuredKey.equals(providedKey)) {
            log.warn("[Desktop鉴权失败] uri={}, header={}, 原因: {}",
                    request.getRequestURI(), headerName,
                    !StringUtils.hasText(providedKey) ? "缺少鉴权头" : "密钥不匹配");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {"code":401,"message":"Unauthorized: invalid or missing Desktop Key"}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 非桌面端路径 → 跳过
        if (!path.startsWith(DESKTOP_PATH_PREFIX)) {
            return true;
        }
        // 健康检查 或 认证接口（登录/退出/公钥）→ 跳过 X-Desktop-Key 鉴权
        return path.equals(HEALTH_ENDPOINT) || path.startsWith(AUTH_PATH_PREFIX);
    }
}
