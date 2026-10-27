package cn.datafuturex.zhishu.mcp.provide.security;

import cn.datafuturex.zhishu.common.context.UserContext;
import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /mcp} 双凭据鉴权链的行为约定。
 */
class McpEndpointAuthFilterTest {

    private McpProperties properties;
    private McpClientAuthenticator clientAuthenticator;
    private JwtUtil jwtUtil;
    private TokenBlacklistService blacklist;
    private McpEndpointAuthFilter filter;

    @BeforeEach
    void setUp() {
        properties = new McpProperties();
        clientAuthenticator = mock(McpClientAuthenticator.class);
        jwtUtil = mock(JwtUtil.class);
        blacklist = mock(TokenBlacklistService.class);
        filter = new McpEndpointAuthFilter(properties, clientAuthenticator, jwtUtil, blacklist);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        McpCallerContext.clear();
    }

    @Test
    @DisplayName("Client Key 凭据：注入调用方与绑定用户身份")
    void clientKeyCredentialEstablishesCaller() throws Exception {
        when(clientAuthenticator.authenticate("wxmcp_abcdef1234567890")).thenReturn(new McpCallerContext.Caller(
                McpCallerContext.CallerType.CLIENT_KEY, 7L, "伙伴A", 42L, "svc_a",
                Set.of("getSystemIntroduction"), 60));

        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicInteger chainCalls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/mcp", "Bearer wxmcp_abcdef1234567890"), response, chain(() -> {
            chainCalls.incrementAndGet();
            seenUser.set(UserContext.getUserId());
        }));

        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(seenUser.get()).isEqualTo("42");
        // 请求结束必须清理 ThreadLocal，否则线程池复用会串号
        assertThat(McpCallerContext.get()).isNull();
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("Client Key 校验失败：401 invalid_mcp_key 且不进入后续链路")
    void invalidClientKeyRejected() throws Exception {
        when(clientAuthenticator.authenticate(anyString())).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(post("/mcp", "Bearer wxmcp_abcdef1234567890"), response, chain(chainCalls::incrementAndGet));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_mcp_key");
        assertThat(chainCalls.get()).isZero();
    }

    @Test
    @DisplayName("平台登录 JWT：按用户名建立身份，只开放公开目录")
    void platformJwtCredentialAccepted() throws Exception {
        when(jwtUtil.validateToken("jwt.token.value")).thenReturn(true);
        when(blacklist.isRevoked("jwt.token.value")).thenReturn(false);
        when(jwtUtil.getUsernameFromToken("jwt.token.value")).thenReturn("admin");

        AtomicReference<McpCallerContext.Caller> seenCaller = new AtomicReference<>();
        AtomicInteger chainCalls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/mcp", "Bearer jwt.token.value"), response, chain(() -> {
            chainCalls.incrementAndGet();
            seenCaller.set(McpCallerContext.get());
        }));

        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(seenCaller.get()).isNotNull();
        assertThat(seenCaller.get().type()).isEqualTo(McpCallerContext.CallerType.PLATFORM_JWT);
        assertThat(seenCaller.get().clientId()).isNull();
        assertThat(seenCaller.get().username()).isEqualTo("admin");
        assertThat(seenCaller.get().allowedToolNames()).isEqualTo(McpProvidedToolCatalog.PUBLIC_TOOLS);
        assertThat(seenCaller.get().rateLimitKey()).isEqualTo("user:admin");
        verify(clientAuthenticator, never()).authenticate(anyString());
    }

    @Test
    @DisplayName("已注销的 JWT：401 invalid_jwt")
    void revokedJwtRejected() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(blacklist.isRevoked(anyString())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/mcp", "Bearer jwt.token.value"), response, chain(() -> {
        }));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_jwt");
    }

    @Test
    @DisplayName("JWT 不允许走 ?token= （URL 会进访问日志）")
    void jwtViaQueryParameterNotAccepted() throws Exception {
        MockHttpServletRequest request = post("/mcp", null);
        request.setParameter("token", "jwt.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain(() -> {
        }));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("missing_credential");
        verify(jwtUtil, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("Client Key 可以走 ?token= （兼容无法自定义头的客户端）")
    void clientKeyViaQueryParameterAccepted() throws Exception {
        when(clientAuthenticator.authenticate("wxmcp_querytoken12")).thenReturn(new McpCallerContext.Caller(
                McpCallerContext.CallerType.CLIENT_KEY, 9L, "伙伴B", null, "svc_b",
                McpProvidedToolCatalog.PUBLIC_TOOLS, 60));
        MockHttpServletRequest request = post("/mcp", null);
        request.setParameter("token", "wxmcp_querytoken12");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), chain(chainCalls::incrementAndGet));

        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("OPTIONS 预检：204 并带 CORS 头，不校验凭据")
    void optionsPrefillShortCircuits() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, chain(chainCalls::incrementAndGet));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Access-Control-Expose-Headers")).contains("Mcp-Session-Id");
        assertThat(chainCalls.get()).isZero();
    }

    @Test
    @DisplayName("非 /mcp 路径不拦截")
    void otherPathsNotFiltered() throws Exception {
        AtomicInteger chainCalls = new AtomicInteger();
        filter.doFilter(post("/api/v1/mcp/clients", null), new MockHttpServletResponse(),
                chain(chainCalls::incrementAndGet));

        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("provide 关闭时：503 mcp_disabled")
    void disabledProvidePlaneReturnsServiceUnavailable() throws Exception {
        properties.setEnabled(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/mcp", "Bearer wxmcp_abcdef1234567890"), response, chain(() -> {
        }));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("mcp_disabled");
    }

    private static MockHttpServletRequest post(String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static FilterChain chain(Runnable inside) {
        return new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException {
                inside.run();
            }
        };
    }
}
