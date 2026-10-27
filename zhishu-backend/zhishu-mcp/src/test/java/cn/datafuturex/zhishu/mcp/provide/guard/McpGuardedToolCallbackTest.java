package cn.datafuturex.zhishu.mcp.provide.guard;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.provide.security.McpCallerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 对外守卫的白名单、限流与<b>身份归属</b>行为。
 */
class McpGuardedToolCallbackTest {

    private McpCallLogService callLogService;
    private McpRateLimiter rateLimiter;
    private ToolCallback delegate;
    private McpGuardedToolCallback guarded;

    @BeforeEach
    void setUp() {
        callLogService = mock(McpCallLogService.class);
        rateLimiter = mock(McpRateLimiter.class);
        delegate = mock(ToolCallback.class);
        org.mockito.Mockito.when(delegate.getToolDefinition())
                .thenReturn(ToolDefinition.builder().name("getSystemIntroduction")
                        .description("d").inputSchema("{}").build());
        org.mockito.Mockito.when(delegate.call(anyString())).thenReturn("{\"ok\":true}");
        guarded = new McpGuardedToolCallback(delegate, callLogService, rateLimiter);
    }

    @AfterEach
    void tearDown() {
        McpCallerContext.clear();
    }

    @Test
    @DisplayName("无调用方上下文时直接拒绝，不写审计")
    void unauthenticatedIsRejected() {
        McpCallerContext.clear();

        assertThat(guarded.call("{}")).contains("unauthenticated");
        verify(callLogService, never()).recordServe(any(), any(), any(), anyBoolean(), any(), anyInt());
    }

    @Test
    @DisplayName("成功调用以调用方身份写 SERVE 审计（身份在请求线程取好后传入）")
    void successRecordsActorIdentity() {
        setCaller(7L, "svc_a", 42L, Set.of("getSystemIntroduction"));
        org.mockito.Mockito.when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);

        assertThat(guarded.call("{}")).isEqualTo("{\"ok\":true}");
        verify(callLogService).recordServe(eq(7L), eq("svc_a"), eq("getSystemIntroduction"),
                eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("绑定用户名为空时回落到绑定用户 id")
    void actorFallsBackToBoundUserId() {
        setCaller(8L, null, 99L, Set.of("getSystemIntroduction"));
        org.mockito.Mockito.when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);

        guarded.call("{}");

        verify(callLogService).recordServe(eq(8L), eq("99"), eq("getSystemIntroduction"),
                eq(true), isNull(), anyInt());
    }

    @Test
    @DisplayName("未授权工具：拒绝并记失败审计")
    void unauthorizedToolRecordsFailure() {
        setCaller(7L, "svc_a", 42L, Set.of("someOtherTool"));

        assertThat(guarded.call("{}")).contains("未授权工具");
        verify(callLogService).recordServe(eq(7L), eq("svc_a"), eq("getSystemIntroduction"),
                eq(false), eq("工具未授权"), eq(0));
        verify(delegate, never()).call(anyString());
    }

    @Test
    @DisplayName("超限：拒绝并记失败审计")
    void rateLimitedRecordsFailure() {
        setCaller(7L, "svc_a", 42L, Set.of("getSystemIntroduction"));
        org.mockito.Mockito.when(rateLimiter.tryAcquire("client:7", 60)).thenReturn(false);

        assertThat(guarded.call("{}")).contains("频率限制");
        verify(callLogService).recordServe(eq(7L), eq("svc_a"), eq("getSystemIntroduction"),
                eq(false), eq("超过 RPM 限制"), eq(0));
    }

    private static void setCaller(Long clientId, String username, Long boundUserId, Set<String> allowed) {
        McpCallerContext.set(new McpCallerContext.Caller(McpCallerContext.CallerType.CLIENT_KEY,
                clientId, "伙伴", boundUserId, username, allowed, 60));
    }
}
