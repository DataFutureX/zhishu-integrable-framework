package com.datafuturex.assistant.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.datafuturex.assistant.mcp.support.McpCrypto;
import com.datafuturex.assistant.openapi.domain.entity.OpenAppCredentialEntity;
import com.datafuturex.assistant.openapi.domain.entity.OpenAppEntity;
import com.datafuturex.assistant.openapi.mapper.OpenAppCredentialMapper;
import com.datafuturex.assistant.openapi.mapper.OpenAppMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OpenApiAuthService {

    private static final TypeReference<List<String>> SCOPES_TYPE = new TypeReference<>() {
    };

    private final OpenAppMapper openAppMapper;
    private final OpenAppCredentialMapper credentialMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuthenticatedOpenApp authenticate(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new OpenApiAuthException(401, "缺少开放应用凭证");
        }
        String token = rawToken.trim();
        String prefix = McpCrypto.keyPrefix(token);
        OpenAppCredentialEntity cred = credentialMapper.selectOne(new LambdaQueryWrapper<OpenAppCredentialEntity>()
                .eq(OpenAppCredentialEntity::getKeyPrefix, prefix)
                .eq(OpenAppCredentialEntity::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (cred == null) {
            throw new OpenApiAuthException(401, "开放应用凭证无效");
        }
        if (cred.getExpiresAt() != null && cred.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OpenApiAuthException(401, "开放应用凭证已过期");
        }
        if (!McpCrypto.sha256Hex(token).equalsIgnoreCase(cred.getSecretHash())) {
            throw new OpenApiAuthException(401, "开放应用凭证无效");
        }
        OpenAppEntity app = openAppMapper.selectById(cred.getAppId());
        if (app == null || !"ENABLED".equalsIgnoreCase(app.getStatus())) {
            throw new OpenApiAuthException(403, "开放应用已停用");
        }
        Set<String> scopes = parseScopes(app.getAllowedScopes());
        credentialMapper.update(null, new LambdaUpdateWrapper<OpenAppCredentialEntity>()
                .eq(OpenAppCredentialEntity::getId, cred.getId())
                .set(OpenAppCredentialEntity::getLastUsedAt, LocalDateTime.now()));
        return new AuthenticatedOpenApp(app.getId(), app.getCode(), scopes);
    }

    public UserRef resolveOnBehalfOf(String username) {
        if (!StringUtils.hasText(username)) {
            throw new OpenApiAuthException(400, "缺少 X-On-Behalf-Of");
        }
        String name = username.trim();
        try {
            List<UserRef> rows = jdbcTemplate.query(
                    "SELECT id::text AS id, username FROM sys_user WHERE username = ? LIMIT 1",
                    (rs, i) -> new UserRef(rs.getString("id"), rs.getString("username")),
                    name);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {
            // 知枢无对应用户时仍以用户名作为上下文，便于万象代调
        }
        return new UserRef(name, name);
    }

    public boolean allows(Set<String> scopes, String required) {
        if (scopes == null || scopes.isEmpty()) {
            return true;
        }
        return scopes.contains(required) || scopes.contains("*");
    }

    private Set<String> parseScopes(String raw) {
        if (!StringUtils.hasText(raw) || "[]".equals(raw.trim())) {
            return Collections.emptySet();
        }
        try {
            List<String> list = objectMapper.readValue(raw, SCOPES_TYPE);
            if (list == null || list.isEmpty()) {
                return Collections.emptySet();
            }
            return Set.copyOf(list);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    public record AuthenticatedOpenApp(Long appId, String code, Set<String> scopes) {
    }

    public record UserRef(String userId, String username) {
    }

    public static class OpenApiAuthException extends RuntimeException {
        private final int status;

        public OpenApiAuthException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
