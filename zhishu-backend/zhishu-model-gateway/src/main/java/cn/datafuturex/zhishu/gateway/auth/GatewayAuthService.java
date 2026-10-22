package cn.datafuturex.zhishu.gateway.auth;

/**
 * 网关鉴权服务：把 Bearer token 解析为调用主体。
 */
public interface GatewayAuthService {

    /**
     * 校验 token 并解析调用主体。
     *
     * @param bearerToken Authorization 头中的 token（不含 Bearer 前缀）
     * @return 调用主体
     * @throws cn.datafuturex.zhishu.gateway.compat.GatewayException token 缺失/失效/账号禁用
     */
    GatewayPrincipal authenticate(String bearerToken);
}
