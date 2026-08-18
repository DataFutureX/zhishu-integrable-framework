package cn.datafuturex.yunqi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 各环境公共数据源配置（开发 / 测试 / 生产）。
 */
@Data
@ConfigurationProperties(prefix = "yunqi.datasource")
public class YunqiDataSourceProperties {

    private static final String DEFAULT_PARAMS =
            "useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true";

    /**
     * 完整 JDBC URL；若为空则根据 host/port/database/params 拼接。
     */
    private String jdbcUrl;

    private String host = "localhost";
    private int port = 3306;
    private String database = "yunqi_application_platform";
    private String username;
    private String password;
    /**
     * URL 查询参数（不含前导 ?）。
     */
    private String params = DEFAULT_PARAMS;

    private Pool pool = new Pool();

    public String resolveJdbcUrl() {
        if (StringUtils.hasText(jdbcUrl)) {
            return jdbcUrl;
        }
        String query = StringUtils.hasText(params) ? "?" + params : "";
        return "jdbc:mysql://" + host + ":" + port + "/" + database + query;
    }

    @Data
    public static class Pool {
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeout = 30_000;
        private long idleTimeout = 600_000;
        private long maxLifetime = 1_800_000;
    }
}
