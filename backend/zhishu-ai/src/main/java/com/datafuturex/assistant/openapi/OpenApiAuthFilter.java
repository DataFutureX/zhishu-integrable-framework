package com.datafuturex.assistant.openapi;

import com.datafuturex.assistant.openapi.service.OpenApiAuthService;
import com.datafuturex.assistant.openapi.service.OpenApiAuthService.AuthenticatedOpenApp;
import com.datafuturex.assistant.openapi.service.OpenApiAuthService.OpenApiAuthException;
import com.datafuturex.assistant.openapi.service.OpenApiAuthService.UserRef;
import com.datafuturex.assistant.shared.Result;
import com.datafuturex.assistant.shared.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private final OpenApiAuthService openApiAuthService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = servletPath(request);
        return path == null || !path.startsWith("/open/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractBearer(request);
            AuthenticatedOpenApp app = openApiAuthService.authenticate(token);
            String required = requiredScope(servletPath(request));
            if (required != null && !openApiAuthService.allows(app.scopes(), required)) {
                writeError(response, 403, "开放应用缺少权限: " + required);
                return;
            }
            String onBehalf = request.getHeader(OpenApiScopes.HEADER_ON_BEHALF_OF);
            UserRef user = openApiAuthService.resolveOnBehalfOf(onBehalf);
            request.setAttribute(OpenApiScopes.ATTR_SCOPES, app.scopes() != null ? app.scopes() : Set.of());
            UserContext.setUserId(user.userId());
            UserContext.setUsername(user.username());
            filterChain.doFilter(request, response);
        } catch (OpenApiAuthException e) {
            writeError(response, e.getStatus(), e.getMessage());
        }
    }

    private static String requiredScope(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/open/v1/chat")) {
            return OpenApiScopes.CHAT;
        }
        if (path.startsWith("/open/v1/knowledges")) {
            return OpenApiScopes.KNOWLEDGES;
        }
        if (path.startsWith("/open/v1/briefings") || path.startsWith("/open/v1/agents")) {
            return OpenApiScopes.BRIEFINGS;
        }
        if (path.startsWith("/open/v1/kg")) {
            return OpenApiScopes.KG;
        }
        return null;
    }

    private static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private static String servletPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path != null && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.fail(status, message));
    }
}
