package cn.datafuturex.zhishu.mcp.provide.security;

import cn.datafuturex.zhishu.common.crypto.CredentialCrypto;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.mcp.provide.client.McpClientAdminService;
import cn.datafuturex.zhishu.mcp.provide.client.entity.AiMcpClientEntity;
import cn.datafuturex.zhishu.mcp.provide.client.mapper.AiMcpClientMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Client Key 校验：前缀查库 + SHA-256 摘要比对 + 能力换算。
 */
class McpClientAuthenticatorTest {

    private final AiMcpClientMapper clientMapper = mock(AiMcpClientMapper.class);
    private final McpClientAdminService clientAdminService = mock(McpClientAdminService.class);
    private final McpClientAuthenticator authenticator =
            new McpClientAuthenticator(clientMapper, clientAdminService);

    @Test
    @DisplayName("有效 Key 返回调用方，并写 last_used_at")
    void validKeyReturnsCaller() {
        String apiKey = CredentialCrypto.newApiKey();
        when(clientMapper.selectOne(any())).thenReturn(client(apiKey, "ENABLED", "[\"ONLINE\"]", 30));

        McpCallerContext.Caller caller = authenticator.authenticate(apiKey);

        assertThat(caller).isNotNull();
        assertThat(caller.type()).isEqualTo(McpCallerContext.CallerType.CLIENT_KEY);
        assertThat(caller.rateLimitKey()).isEqualTo("client:1");
        assertThat(caller.rpmLimit()).isEqualTo(30);
        assertThat(caller.allowedToolNames())
                .contains("getTerminalOnlineOverview")
                .containsAll(McpProvidedToolCatalog.PUBLIC_TOOLS);
        verify(clientAdminService).touchLastUsed(1L);
    }

    @Test
    @DisplayName("非 wxmcp_ 前缀直接拒绝，不查库")
    void nonApiKeyPrefixRejected() {
        assertThat(authenticator.authenticate("jwt.token.value")).isNull();
        assertThat(authenticator.authenticate(null)).isNull();
        verify(clientMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("停用 Client 拒绝")
    void disabledClientRejected() {
        String apiKey = CredentialCrypto.newApiKey();
        when(clientMapper.selectOne(any())).thenReturn(client(apiKey, "DISABLED", "[]", 30));

        assertThat(authenticator.authenticate(apiKey)).isNull();
        verify(clientAdminService, never()).touchLastUsed(any());
    }

    @Test
    @DisplayName("摘要不匹配（Key 被篡改）拒绝")
    void secretHashMismatchRejected() {
        String other = CredentialCrypto.newApiKey();
        AiMcpClientEntity entity = client(other, "ENABLED", "[]", 30);
        entity.setSecretHash(CredentialCrypto.sha256Hex("wxmcp_forged"));

        when(clientMapper.selectOne(any())).thenReturn(entity);

        assertThat(authenticator.authenticate(other)).isNull();
    }

    @Test
    @DisplayName("RPM 缺失时回落到默认值")
    void missingRpmFallsBackToDefault() {
        String apiKey = CredentialCrypto.newApiKey();
        when(clientMapper.selectOne(any())).thenReturn(client(apiKey, "ENABLED", null, null));

        McpCallerContext.Caller caller = authenticator.authenticate(apiKey);

        assertThat(caller).isNotNull();
        assertThat(caller.rpmLimit()).isEqualTo(McpClientAdminService.DEFAULT_RPM_FALLBACK);
        assertThat(caller.allowedToolNames()).isEqualTo(McpProvidedToolCatalog.PUBLIC_TOOLS);
    }

    private static AiMcpClientEntity client(String apiKey, String status, String capabilities, Integer rpm) {
        AiMcpClientEntity entity = new AiMcpClientEntity();
        entity.setId(1L);
        entity.setName("伙伴A");
        entity.setKeyPrefix(CredentialCrypto.keyPrefix(apiKey));
        entity.setSecretHash(CredentialCrypto.sha256Hex(apiKey));
        entity.setBoundUserId(42L);
        entity.setBoundUsername("svc_a");
        entity.setCapabilities(capabilities == null ? "[]" : capabilities);
        entity.setRpmLimit(rpm);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("能力清单解析为空时回落到公开目录")
    void blankCapabilitiesUsePublicCatalog() {
        String apiKey = CredentialCrypto.newApiKey();
        when(clientMapper.selectOne(any())).thenReturn(client(apiKey, "ENABLED", "[]", 60));

        McpCallerContext.Caller caller = authenticator.authenticate(apiKey);

        assertThat(caller.allowedToolNames())
                .isEqualTo(McpProvidedToolCatalog.allowedTools(List.of()));
    }
}
