package cn.datafuturex.zhishu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 配置类
 * 用于配置 Swagger UI 的文档信息和安全认证
 *
 * @author YunQi Application Platform Team
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 文档信息和安全方案
     *
     * @return OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("云起应用平台 API 文档")
                        .description("基于 Spring Boot 4 + Spring Security 7 的云起应用平台 API 接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("YunQi Application Platform Team")
                                .email("datafuturex@163.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                // 添加全局安全要求
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                // 配置安全方案：JWT Bearer Token
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 JWT Token，格式为: Bearer <token>")));
    }
}
