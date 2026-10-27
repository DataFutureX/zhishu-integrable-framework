package cn.datafuturex.zhishu.mcp.provide.security;

import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import cn.datafuturex.zhishu.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@code /mcp} 唯一入站鉴权过滤器，接受两种凭据：
 * <ol>
 *   <li>外部 Client API Key：{@code Authorization: Bearer wxmcp_*}（或 {@code ?token=} ，仅该形态允许）</li>
 *   <li>平台登录态 JWT：{@code Authorization: Bearer <jwt>}，由 zhishu-security 的 {@link JwtUtil} 校验</li>
 * </ol>
 * 原 {@code zhishu-mcp} 的静态 {@code X-API-Key} 过滤器已移除，生产不再需要双头调用。
 * Spring Security 对 {@code /mcp} 是 permitAll 且 {@code JwtAuthenticationFilter} 跳过该路径，
 * 因此本过滤器是 {@code /mcp} 的单点鉴权决策处。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
@Slf4j
public class McpEndpointAuthFilter extends OncePerRequestFilter {

    private final McpProperties properties;
    private final McpClientAuthenticator clientAuthenticator;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = stripContextPath(request);
        return !path.equals("/mcp") && !path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        applyCors(response);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!properties.provideEnabled()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "mcp_disabled");
            return;
        }
        McpProperties.Provide provide = properties.getProvide();
        String bearer = extractBearer(request);
        try {
            McpCallerContext.Caller caller = resolveCaller(bearer, provide);
            if (caller == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, unauthorizedReason(bearer, provide));
                return;
            }
            McpCallerContext.set(caller);
            UserContext.setUserId(caller.boundUserId() == null
                    ? caller.username()
                    : String.valueOf(caller.boundUserId()));
            UserContext.setUsername(caller.username());
            filterChain.doFilter(request, response);
        } finally {
            McpCallerContext.clear();
            UserContext.clear();
        }
    }

    /**
     * 凭据分流：先按 {@code wxmcp_} 前缀识别 Client Key，其余按平台 JWT 校验。
     *
     * @return 调用方快照；凭据无效时返回 null
     */
    private McpCallerContext.Caller resolveCaller(String bearer, McpProperties.Provide provide) {
        if (!StringUtils.hasText(bearer)) {
            return null;
        }
        if (McpClientAuthenticator.looksLikeApiKey(bearer)) {
            if (!provide.isClientKeyEnabled()) {
                log.warn("[MCP鉴权] Client Key 模式已关闭但收到 wxmcp_ 凭据");
                return null;
            }
            return clientAuthenticator.authenticate(bearer);
        }
        if (!provide.isPlatformJwtEnabled()) {
            log.warn("[MCP鉴权] 平台 JWT 模式已关闭，拒绝该凭据");
            return null;
        }
        String username = resolveJwtUsername(bearer);
        if (username == null) {
            return null;
        }
        return new McpCallerContext.Caller(
                McpCallerContext.CallerType.PLATFORM_JWT,
                null,
                username,
                null,
                username,
                McpProvidedToolCatalog.PUBLIC_TOOLS,
                provide.getJwtDefaultRpm());
    }

    /** JWT 校验（含注销黑名单），失败返回 null */
    private String resolveJwtUsername(String token) {
        try {
            if (!jwtUtil.validateToken(token) || tokenBlacklistService.isRevoked(token)) {
                return null;
            }
            String username = jwtUtil.getUsernameFromToken(token);
            return StringUtils.hasText(username) ? username : null;
        } catch (Exception e) {
            log.warn("[MCP鉴权] JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String unauthorizedReason(String bearer, McpProperties.Provide provide) {
        if (!StringUtils.hasText(bearer)) {
            return "missing_credential";
        }
        if (McpClientAuthenticator.looksLikeApiKey(bearer)) {
            return provide.isClientKeyEnabled() ? "invalid_mcp_key" : "client_key_disabled";
        }
        return provide.isPlatformJwtEnabled() ? "invalid_jwt" : "platform_jwt_disabled";
    }

    /**
     * 取凭据：仅 {@code Authorization: Bearer}。
     * <p>
     * {@code ?token=} 查询串只对 Client Key 开放（URL 会进访问日志，JWT 不允许走 query）。
     */
    private static String extractBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String query = request.getParameter("token");
        if (StringUtils.hasText(query) && McpClientAuthenticator.looksLikeApiKey(query.trim())) {
            return query.trim();
        }
        return null;
    }

    private static String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path != null && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path == null ? "" : path;
    }

    private static void applyCors(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers",
                "Authorization, Content-Type, Accept, Mcp-Session-Id, Last-Event-ID, MCP-Protocol-Version");
        response.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
        response.setHeader("Access-Control-Max-Age", "86400");
    }

    private static void writeError(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
