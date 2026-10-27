package cn.datafuturex.zhishu.gateway.config;

import cn.datafuturex.zhishu.common.crypto.CredentialProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 模型网关基础配置：注册配置属性并在加载期校验。
 * <p>
 * 与 {@code zhishu.gateway.enabled} 无关，保证关闭网关时其余模块仍可注入配置。
 * {@link CredentialProperties} 住在 zhishu-security 的 common 层，网关自行注册以便独立装配。
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, CredentialProperties.class})
public class GatewayConfiguration {

    /**
     * 加载期校验网关参数。
     *
     * @param properties 网关配置
     */
    public GatewayConfiguration(GatewayProperties properties) {
        properties.validate();
    }
}
