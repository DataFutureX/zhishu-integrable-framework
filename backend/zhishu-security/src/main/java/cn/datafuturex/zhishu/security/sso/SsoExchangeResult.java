package cn.datafuturex.zhishu.security.sso;

import cn.datafuturex.zhishu.modules.dto.SsoExchangeResponseDTO;

/**
 * 换票内部结果（含审计字段）
 */
public record SsoExchangeResult(
        SsoExchangeResponseDTO response,
        String username,
        String channel
) {
}
