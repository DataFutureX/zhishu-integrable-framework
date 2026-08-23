package cn.datafuturex.zhishu.ai.briefing.config;

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
 * 启动时确保 ai_briefing_* 表与种子数据就绪（幂等）。
 */
@Component
@Order(25)
@RequiredArgsConstructor
@Slf4j
public class BriefingSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/archive/migration_ai_briefing.sql"));
            ClassPathResource webhookPatch = new ClassPathResource("db/patch_webhook_delivery.sql");
            if (webhookPatch.exists()) {
                populator.addScript(webhookPatch);
            }
            populator.execute(dataSource);
            log.info("已检查/初始化 ai_briefing_schedule / ai_briefing_delivery 表结构与种子数据");
        } catch (Exception e) {
            log.warn("ai_briefing 表初始化跳过或失败（可手动执行 archive/migration_ai_briefing.sql）: {}",
                    e.getMessage());
        }
    }
}
