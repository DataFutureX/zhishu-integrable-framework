package cn.datafuturex.zhishu.config.security;

import cn.datafuturex.zhishu.modules.service.PermissionService;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 *
 * @author YunQi Application Platform Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);

            if (StringUtils.hasText(token)
                    && jwtUtil.validateToken(token)
                    && !tokenBlacklistService.isRevoked(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                List<String> permissionCodes = permissionService.listPermissionCodesByUsername(username);

                // null 表示用户不存在或已禁用，不建立认证上下文
                if (permissionCodes != null) {
                    List<SimpleGrantedAuthority> authorities = permissionCodes.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("用户 {} 认证成功，权限数={}", username, authorities.size());
                }
            }
        } catch (Exception e) {
            log.error("JWT 认证失败", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 Token（仅 Authorization Bearer，避免 query token 泄露）
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 兼容 SSE/WebSocket 等无法自定义 Header 的场景
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
