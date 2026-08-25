package cn.datafuturex.zhishu.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;

import javax.sql.DataSource;

/**
 * 主数据源（HikariCP）。单元测试使用 spring.datasource（H2）时不启用本配置。
 */
@Configuration
@EnableConfigurationProperties(YunqiDataSourceProperties.class)
@ConditionalOnProperty(prefix = "yunqi.datasource", name = "host")
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(YunqiDataSourceProperties props) {
        Assert.hasText(props.getUsername(), "yunqi.datasource.username 不能为空");

        YunqiDataSourceProperties.Pool pool = props.getPool();
        HikariConfig config = new HikariConfig();
        config.setPoolName("zhishu-pool");
        config.setJdbcUrl(props.resolveJdbcUrl());
        config.setUsername(props.getUsername());
        config.setPassword(props.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeout());
        config.setIdleTimeout(pool.getIdleTimeout());
        config.setMaxLifetime(pool.getMaxLifetime());
        return new HikariDataSource(config);
    }
}
