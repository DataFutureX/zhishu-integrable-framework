package cn.datafuturex.zhishu.ai.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(16)
@RequiredArgsConstructor
@Slf4j
public class ChatSessionSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/archive/migration_chat_session.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 chat_session 表结构与历史回填");
        } catch (Exception e) {
            log.warn("chat_session 表初始化跳过或失败（可手动执行 db/init_postgresql.sql 或 archive/migration_chat_session.sql）: {}", e.getMessage());
        }
    }
}
