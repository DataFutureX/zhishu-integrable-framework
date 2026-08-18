package cn.datafuturex.yunqi.security.sso;

import cn.datafuturex.yunqi.modules.dto.SsoExchangeResponseDTO;

/**
 * 换票内部结果（含审计字段）
 */
public record SsoExchangeResult(
        SsoExchangeResponseDTO response,
        String username,
        String channel
) {
}
