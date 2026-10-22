package cn.datafuturex.zhishu.gateway.auth;

/**
 * 网关调用主体（来自知枢 JWT 或静态 token）。
 *
 * @param userId   用户 ID（字符串形式）
 * @param username 用户名（额度主体键）
 * @param role     角色编码（额度回落用）
 */
public record GatewayPrincipal(
        String userId,
        String username,
        String role
) {
}
