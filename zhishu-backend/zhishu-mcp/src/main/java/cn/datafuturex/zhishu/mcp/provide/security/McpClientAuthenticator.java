package cn.datafuturex.zhishu.mcp.provide.security;

import cn.datafuturex.zhishu.mcp.provide.client.McpClientAdminService;
import cn.datafuturex.zhishu.mcp.provide.client.entity.AiMcpClientEntity;
import cn.datafuturex.zhishu.mcp.provide.client.mapper.AiMcpClientMapper;
import cn.datafuturex.zhishu.mcp.common.json.McpJson;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.common.crypto.CredentialCrypto;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 凭据 A 校验：外部 MCP Client 的 {@code wxmcp_*} API Key。
 * <p>
 * Key 只在创建/轮换时明文返回一次，库里存前 14 位前缀 + SHA-256；
 * 校验通过后按 Client 的 {@code capabilities} 计算可调用 Tool 集合。
 */
@Component
@RequiredArgsConstructor
public class McpClientAuthenticator {

    /** Client API Key 前缀（与 {@link CredentialCrypto#newApiKey()} 一致） */
    public static final String API_KEY_PREFIX = "wxmcp_";

    private final AiMcpClientMapper clientMapper;
    private final McpClientAdminService clientAdminService;

    /**
     * 校验 Client API Key。
     *
     * @param bearerToken Bearer 后的原始 token
     * @return 调用方快照；token 非法 / Client 停用 / 摘要不匹配时返回 null
     */
    public McpCallerContext.Caller authenticate(String bearerToken) {
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith(API_KEY_PREFIX)) {
            return null;
        }
        String prefix = CredentialCrypto.keyPrefix(bearerToken);
        AiMcpClientEntity entity = clientMapper.selectOne(
                new LambdaQueryWrapper<AiMcpClientEntity>()
                        .eq(AiMcpClientEntity::getKeyPrefix, prefix)
                        .last("LIMIT 1"));
        if (entity == null || !"ENABLED".equalsIgnoreCase(entity.getStatus())) {
            return null;
        }
        if (!CredentialCrypto.sha256Hex(bearerToken).equalsIgnoreCase(entity.getSecretHash())) {
            return null;
        }
        List<String> capabilities = McpJson.parseStringList(entity.getCapabilities());
        Set<String> tools = McpProvidedToolCatalog.allowedTools(capabilities);
        clientAdminService.touchLastUsed(entity.getId());
        return new McpCallerContext.Caller(
                McpCallerContext.CallerType.CLIENT_KEY,
                entity.getId(),
                entity.getName(),
                entity.getBoundUserId(),
                entity.getBoundUsername(),
                tools,
                entity.getRpmLimit() == null || entity.getRpmLimit() <= 0
                        ? McpClientAdminService.DEFAULT_RPM_FALLBACK
                        : entity.getRpmLimit());
    }

    /** 是否为 Client API Key 形态（供过滤器分流） */
    public static boolean looksLikeApiKey(String token) {
        return StringUtils.hasText(token) && token.startsWith(API_KEY_PREFIX);
    }
}
