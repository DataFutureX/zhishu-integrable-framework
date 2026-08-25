package cn.datafuturex.zhishu.modules.dto;

/**
 * SSO 换票响应（业务 JWT + 站内跳转）
 */
public record SsoExchangeResponseDTO(
        String token,
        Long expiration,
        String redirect
) {
}
