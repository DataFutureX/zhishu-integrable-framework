package cn.datafuturex.zhishu.mcp.security;

import cn.datafuturex.zhishu.mcp.config.McpServerProperties;
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
 * MCP 端点 API Key 鉴权过滤器。
 * <p>
 * 仅拦截 MCP 端点路径（{@code /mcp}），当 {@code authKey} 非空时校验请求头中的 API Key；
 * 为空时放行（开发模式）。
 */
@RequiredArgsConstructor
@Slf4j
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String MCP_ENDPOINT = "/mcp";

    private final McpServerProperties properties;

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
            log.warn("[MCP鉴权失败] uri={}, header={}, 原因: {}",
                    request.getRequestURI(), headerName,
                    !StringUtils.hasText(providedKey) ? "缺少鉴权头" : "密钥不匹配");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {"error":"Unauthorized","message":"Invalid or missing API Key"}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅拦截 /mcp 路径
        String path = request.getRequestURI();
        return !path.startsWith(MCP_ENDPOINT);
    }
}
