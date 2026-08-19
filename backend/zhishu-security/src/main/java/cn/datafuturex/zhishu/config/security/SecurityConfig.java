package cn.datafuturex.zhishu.config.security;

import cn.datafuturex.zhishu.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Spring Security 配置类 - 基于 SecurityFilterChain
 *
 * @author YunQi Application Platform Team
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CommittedResponseSecurityExceptionFilter committedResponseSecurityExceptionFilter;
    private final SwaggerFrameOptionsFilter swaggerFrameOptionsFilter;
    private final SecurityProperties securityProperties;

    /**
     * 配置 SecurityFilterChain
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     * @throws Exception 异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/system-config").permitAll()
                    .requestMatchers("/api/v1/system/health").permitAll()
                    .requestMatchers("/uploads/**").permitAll();
                permitApiDocsIfEnabled(auth);
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(committedResponseSecurityExceptionFilter, ExceptionTranslationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(swaggerFrameOptionsFilter, HeaderWriterFilter.class);

        return http.build();
    }

    /**
     * 避免 Spring Boot 将 SwaggerFrameOptionsFilter 再注册为 Servlet Filter（已挂在 Security 链）。
     */
    @Bean
    public FilterRegistrationBean<SwaggerFrameOptionsFilter> swaggerFrameOptionsFilterRegistration(
            SwaggerFrameOptionsFilter filter) {
        FilterRegistrationBean<SwaggerFrameOptionsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private void permitApiDocsIfEnabled(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        if (securityProperties.isApiDocsPermitAll()) {
            auth.requestMatchers(
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**"
            ).permitAll();
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
