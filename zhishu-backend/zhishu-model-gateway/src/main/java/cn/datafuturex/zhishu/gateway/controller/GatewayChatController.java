package cn.datafuturex.zhishu.gateway.controller;

import cn.datafuturex.zhishu.gateway.auth.GatewayAuthFilter;
import cn.datafuturex.zhishu.gateway.auth.GatewayPrincipal;
import cn.datafuturex.zhishu.gateway.chat.GatewayChatService;
import cn.datafuturex.zhishu.gateway.usage.GatewayUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 推理端点（OpenAI 兼容）。
 * <p>
 * 协议面自行写响应：流式与非流式共用同一入口，因此返回 {@code void} 而非 {@code Result}。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "模型网关 · 推理", description = "OpenAI 兼容的 Chat Completions 转发端点")
public class GatewayChatController {

    private static final String CHANNEL_HEADER = "X-Zhishu-Channel";
    private static final String CLIENT_NAME_HEADER = "X-Client-Name";
    private static final String CLIENT_VERSION_HEADER = "X-Client-Version";
    private static final String DEFAULT_CHANNEL = "DESKTOP";
    private static final String CONSOLE_CHANNEL = "CONSOLE";

    private final GatewayChatService gatewayChatService;

    /**
     * 转发一次 Chat Completions 请求。
     *
     * @param body     原始 OpenAI 请求体
     * @param request  请求
     * @param response 响应
     * @throws IOException 响应写出失败
     */
    @PostMapping("${zhishu.gateway.chat-path:/v1/chat/completions}")
    @Operation(summary = "推理转发", description = "按登录用户计量并强制额度，支持 SSE 流式")
    public void chatCompletions(@RequestBody String body,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(GatewayAuthFilter.PRINCIPAL_ATTRIBUTE);
        gatewayChatService.handle(principal, channel(request), body, clientMeta(request), response);
    }

    private static String channel(HttpServletRequest request) {
        String header = request.getHeader(CHANNEL_HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim().toUpperCase();
        }
        // 经反代/网关转发的请求一定带转发头，视为外部客户端
        if (StringUtils.hasText(request.getHeader("X-Forwarded-For"))
                || StringUtils.hasText(request.getHeader("X-Real-IP"))) {
            return DEFAULT_CHANNEL;
        }
        // 无转发头且来源为本机回环 → 平台内部调用
        String remote = request.getRemoteAddr();
        if ("127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            return CONSOLE_CHANNEL;
        }
        return DEFAULT_CHANNEL;
    }

    private static GatewayUsageService.ClientMeta clientMeta(HttpServletRequest request) {
        return new GatewayUsageService.ClientMeta(
                clientIp(request),
                request.getHeader("User-Agent"),
                request.getHeader(CLIENT_NAME_HEADER),
                request.getHeader(CLIENT_VERSION_HEADER));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }
}
