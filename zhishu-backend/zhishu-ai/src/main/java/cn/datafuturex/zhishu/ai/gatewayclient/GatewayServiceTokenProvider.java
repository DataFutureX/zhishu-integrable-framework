package cn.datafuturex.zhishu.ai.gatewayclient;

import cn.datafuturex.zhishu.ai.gatewayclient.config.GatewayInternalProperties;
import cn.datafuturex.zhishu.config.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 服务账号 token 提供者：无请求上下文（定时任务、启动自检）时代表平台调用网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayServiceTokenProvider {

    private final JwtUtil jwtUtil;
    private final GatewayInternalProperties properties;

    private volatile String cachedToken;

    private volatile long expiresAtMs;

    /**
     * 获取服务账号 token（到期自动续签）。
     *
     * @return JWT
     */
    public String token() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < expiresAtMs) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < expiresAtMs) {
                return cachedToken;
            }
            String username = properties.getServiceUsername();
            cachedToken = jwtUtil.generateToken(username);
            long renewSeconds = Math.max(60, properties.getTokenRenewSeconds());
            expiresAtMs = System.currentTimeMillis() + renewSeconds * 1000L;
            log.info("[网关客户端] 已签发服务账号 token username={}, 续签周期={}s", username, renewSeconds);
            return cachedToken;
        }
    }
}
