package cn.datafuturex.zhishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关可配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunqi.security")
public class SecurityProperties {

    /**
     * 是否放行 API 文档相关路径（Swagger / Knife4j）。
     * 仅建议在开发环境开启。
     */
    private boolean apiDocsPermitAll = false;
}
