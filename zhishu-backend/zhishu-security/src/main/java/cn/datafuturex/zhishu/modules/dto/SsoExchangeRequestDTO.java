package cn.datafuturex.zhishu.modules.dto;

/**
 * SSO 换票请求
 */
public record SsoExchangeRequestDTO(
        String ticket,
        String redirect
) {
}
