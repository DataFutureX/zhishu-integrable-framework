package cn.datafuturex.zhishu.ai.gatewayclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台内部调用模型网关的参数（与 {@code zhishu.gateway.internal} 对齐）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhishu.gateway.internal")
public class GatewayInternalProperties {

    /** false：内部回退直连上游（灰度/回滚） */
    private boolean enabled = true;

    /** 网关回环地址（不带 /v1） */
    private String baseUrl = "http://127.0.0.1:8080";

    /** 无用户上下文时的调用主体 */
    private String serviceUsername = "gateway-service";

    /** caller：计入当前登录用户；platform：统一计入服务账号 */
    private String quotaSubject = "caller";

    /** 服务账号 token 续签周期（秒） */
    private int tokenRenewSeconds = 3600;

    /** 内部调用写入台账的通道标识 */
    private String channel = "CONSOLE";

    /** 目录拉取超时（毫秒） */
    private int catalogTimeoutMs = 3000;
}
