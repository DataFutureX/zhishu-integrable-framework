package cn.datafuturex.zhishu.desktop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 桌面客户端 API 模块配置属性。
 * <p>
 * 通过 {@code zhishu.desktop.*} 配置，支持环境变量覆盖。
 */
@Data
@ConfigurationProperties(prefix = "zhishu.desktop")
public class DesktopApiProperties {

    /** 是否启用桌面 API 模块 */
    private boolean enabled = true;

    /**
     * 桌面端鉴权密钥；空字符串 = 不鉴权（开发模式）。
     * 生产环境必须配置，建议通过环境变量 ZHISHU_DESKTOP_AUTH_KEY 注入。
     */
    private String authKey = "";

    /** 鉴权请求头名称，默认 X-Desktop-Key */
    private String authHeader = "X-Desktop-Key";

    /** 桌面端 API 版本标识，用于 /desktop/v1/info 返回 */
    private String apiVersion = "1.0.0";
}
