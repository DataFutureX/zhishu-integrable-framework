package cn.datafuturex.zhishu.ai.platform.config;

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
 * AI 模块建表引导：启动期幂等执行 {@code db/init_ai.sql}（唯一权威脚本）。
 * <p>
 * 取代原先分散在 Chat / Knowledge / OpenApi 三个模块、各自执行 archive 历史迁移的初始化器 ——
 * 那种写法的后果是「表结构的真实来源在 archive 目录里的增量脚本」，新环境与老环境极易漂移。
 * <p>
 * 约定：脚本内的 DDL 全部为 {@code CREATE ... IF NOT EXISTS} 且不含 {@code $$} 块，
 * 因此可被 {@link ResourceDatabasePopulator}（分隔符 {@code ;}）安全、重复执行。
 */
@Slf4j
@Component
@Order(15)
@RequiredArgsConstructor
public class AiSchemaInitializer implements ApplicationRunner {

    private static final String SCRIPT = "db/init_ai.sql";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource(SCRIPT));
            populator.execute(dataSource);
            log.info("已检查/初始化 AI 模块表结构（{}）", SCRIPT);
        } catch (Exception e) {
            log.warn("AI 模块表结构初始化跳过或失败（可手动执行 {}）: {}", SCRIPT, e.getMessage());
        }
    }
}
