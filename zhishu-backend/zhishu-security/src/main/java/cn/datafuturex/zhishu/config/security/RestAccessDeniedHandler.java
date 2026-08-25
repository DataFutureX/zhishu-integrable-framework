package cn.datafuturex.zhishu.config.security;

import tools.jackson.databind.json.JsonMapper;
import cn.datafuturex.zhishu.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 已认证但无权限请求返回 403 JSON
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("无权访问: {} {}", request.getMethod(), request.getRequestURI());
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.error(403, "无权访问该资源"));
    }
}
