package cn.datafuturex.zhishu.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 网关建表：幂等执行 {@code db/init_gateway.sql}。
 */
@Slf4j
@Component
@Order(17)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * 执行建表脚本。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/init_gateway.sql"));
            populator.execute(dataSource);
            log.info("已执行模型网关建表脚本");
        } catch (Exception e) {
            log.error("模型网关建表脚本执行失败，网关相关功能不可用: {}", e.getMessage(), e);
        }
    }
}
