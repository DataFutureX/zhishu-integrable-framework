package cn.datafuturex.zhishu.biz.operationlog;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 操作日志拦截器注册（业务模块）
 */
@Configuration
@RequiredArgsConstructor
public class OperationLogWebConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
