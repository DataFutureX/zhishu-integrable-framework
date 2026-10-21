package cn.datafuturex.zhishu.desktop.config;

import cn.datafuturex.zhishu.desktop.security.DesktopAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 桌面客户端 API 模块自动装配。
 * <p>
 * 通过 {@code zhishu.desktop.enabled} 控制开关，注册鉴权过滤器。
 * 控制器由组件扫描自动注册（包路径在 {@code cn.datafuturex.zhishu} 下）。
 */
@Configuration
@EnableConfigurationProperties(DesktopApiProperties.class)
@ConditionalOnProperty(name = "zhishu.desktop.enabled", havingValue = "true", matchIfMissing = true)
public class DesktopApiAutoConfiguration {

    /**
     * 注册桌面端 API 鉴权过滤器。
     * <p>
     * 过滤器顺序：排在 Spring Security 过滤器之前（Security 层已对 /desktop/v1/** permitAll，
     * 真实鉴权由本过滤器完成），与 MCP 过滤器策略保持一致。
     *
     * @param properties 桌面端配置属性
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<DesktopAuthFilter> desktopAuthFilterRegistration(
            DesktopApiProperties properties) {
        var registration = new FilterRegistrationBean<>(new DesktopAuthFilter(properties));
        registration.addUrlPatterns("/desktop/v1/*");
        registration.setOrder(Integer.MIN_VALUE + 110);
        registration.setName("desktopApiAuthFilter");
        return registration;
    }
}
