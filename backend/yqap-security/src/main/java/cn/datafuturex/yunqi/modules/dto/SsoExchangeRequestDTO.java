package cn.datafuturex.yunqi.modules.dto;

/**
 * SSO 换票请求
 */
public record SsoExchangeRequestDTO(
        String ticket,
        String redirect
) {
}
