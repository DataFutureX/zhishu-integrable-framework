package com.datafuturex.assistant.kg.config;

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
 * 启动时确保 ai_kg_sync_watermark 表就绪（幂等）。
 */
@Component
@Order(28)
@RequiredArgsConstructor
@Slf4j
public class KgSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/archive/migration_ai_kg_sync.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 ai_kg_sync_watermark 表结构");
        } catch (Exception e) {
            log.warn("ai_kg_sync_watermark 初始化跳过或失败: {}", e.getMessage());
        }
    }
}
