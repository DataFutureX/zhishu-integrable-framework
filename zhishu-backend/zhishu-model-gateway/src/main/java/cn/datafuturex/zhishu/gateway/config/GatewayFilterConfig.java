package cn.datafuturex.zhishu.gateway.config;

import cn.datafuturex.zhishu.gateway.auth.GatewayAuthFilter;
import cn.datafuturex.zhishu.gateway.auth.GatewayAuthService;
import cn.datafuturex.zhishu.gateway.compat.GatewayReadyFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关过滤器注册：{@code /v1/*} 由 {@link GatewayAuthFilter} 鉴权。
 */
@Configuration
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayFilterConfig {

    /**
     * 注册鉴权过滤器（先于 Spring Security 链）。
     *
     * @param gatewayAuthService 鉴权服务
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(
            GatewayAuthService gatewayAuthService) {
        FilterRegistrationBean<GatewayAuthFilter> registration =
                new FilterRegistrationBean<>(new GatewayAuthFilter(gatewayAuthService));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Integer.MIN_VALUE + 120);
        registration.setName("gatewayAuthFilter");
        return registration;
    }

    /**
     * 注册就绪闸门：结构未就绪时返回 503 + OpenAI 错误体（排在鉴权之后）。
     *
     * @param readiness 就绪状态
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<GatewayReadyFilter> gatewayReadyFilterRegistration(
            GatewayReadiness readiness) {
        FilterRegistrationBean<GatewayReadyFilter> registration =
                new FilterRegistrationBean<>(new GatewayReadyFilter(readiness));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Integer.MIN_VALUE + 130);
        registration.setName("gatewayReadyFilter");
        return registration;
    }
}
