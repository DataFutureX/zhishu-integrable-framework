package cn.datafuturex.zhishu.ai.openapi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时确保 open_app / open_app_credential 就绪（幂等）。
 */
@Component
@Order(28)
@RequiredArgsConstructor
@Slf4j
public class OpenAppSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/archive/migration_open_app.sql"));
            ClassPathResource seed = new ClassPathResource("db/patch_open_app_wanxiang.sql");
            if (seed.exists()) {
                populator.addScript(seed);
            }
            ClassPathResource dropBriefing = new ClassPathResource("db/patch_open_app_remove_briefing_scope.sql");
            if (dropBriefing.exists()) {
                populator.addScript(dropBriefing);
            }
            ClassPathResource aksk = new ClassPathResource("db/patch_open_app_aksk.sql");
            if (aksk.exists()) {
                populator.addScript(aksk);
            }
            populator.execute(dataSource);
            log.info("已检查/初始化 open_app / open_app_credential（含 AK/SK 字段）");
        } catch (Exception e) {
            log.warn("open_app 表初始化跳过或失败（可手动执行 init_ai.sql）: {}", e.getMessage());
        }
    }
}
