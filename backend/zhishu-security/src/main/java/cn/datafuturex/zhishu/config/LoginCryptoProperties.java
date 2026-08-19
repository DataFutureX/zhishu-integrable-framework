package cn.datafuturex.zhishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录凭证非对称加密配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunqi.login-crypto")
public class LoginCryptoProperties {

    /**
     * 是否启用登录凭证 RSA 加密传输
     */
    private boolean enabled = true;

    /**
     * RSA 密钥长度
     */
    private int keySize = 2048;

    /**
     * 公钥有效期（秒），超时后不可用于登录
     */
    private int keyExpireSeconds = 300;
}
