package cn.datafuturex.zhishu.ai.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.modelconfig.config.ModelConfigProperties;
import cn.datafuturex.zhishu.ai.openapi.domain.entity.OpenAppEntity;
import cn.datafuturex.zhishu.ai.openapi.mapper.OpenAppMapper;
import cn.datafuturex.zhishu.ai.openapi.support.OpenApiCrypto;
import cn.datafuturex.zhishu.ai.openapi.support.OpenApiCrypto.TokenPayload;
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
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelConfigProperties modelConfigProperties;

    /**
     * AK/SK Token 鉴权。
     * <p>
     * Token 格式：{@code {ak}:{timestampMs}:{signature}}
     */
    public AuthenticatedOpenApp authenticate(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new OpenApiAuthException(401, "缺少开放应用凭证");
        }
        // 1. 解析 Token 获取 AK
        TokenPayload payload;
        try {
            payload = OpenApiCrypto.parseToken(rawToken.trim());
        } catch (OpenApiCrypto.TokenParseException e) {
            throw new OpenApiAuthException(401, "Token 格式无效: " + e.getMessage());
        }
        // 2. 通过 AK 查找应用
        OpenAppEntity app = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppEntity>()
                .eq(OpenAppEntity::getAccessKey, payload.ak())
                .last("LIMIT 1"));
        if (app == null) {
            throw new OpenApiAuthException(401, "Access Key 无效");
        }
        if (!"ENABLED".equalsIgnoreCase(app.getStatus())) {
            throw new OpenApiAuthException(403, "开放应用已停用");
        }
        // 3. 解密 SK 并验签
        if (!StringUtils.hasText(app.getSecretKeyEnc())) {
            throw new OpenApiAuthException(401, "该应用未配置 AK/SK，请先生成凭证");
        }
        String sk;
        try {
            sk = McpCrypto.decrypt(app.getSecretKeyEnc(), modelConfigProperties.getCryptoKey());
        } catch (Exception e) {
            throw new OpenApiAuthException(500, "服务端解密失败");
        }
        try {
            OpenApiCrypto.verifyToken(rawToken.trim(), sk);
        } catch (OpenApiCrypto.TokenParseException e) {
            throw new OpenApiAuthException(401, e.getMessage());
        }
        // 4. 解析 scopes 并更新最近使用时间
        Set<String> scopes = parseScopes(app.getAllowedScopes());
        openAppMapper.update(null, new LambdaUpdateWrapper<OpenAppEntity>()
                .eq(OpenAppEntity::getId, app.getId())
                .set(OpenAppEntity::getLastUsedAt, LocalDateTime.now()));
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
