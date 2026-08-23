package cn.datafuturex.zhishu.ai.knowledge.config;

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
 * 启动时确保 knowledges_category / knowledges 表结构就绪（幂等）。
 */
@Component
@Order(15)
@RequiredArgsConstructor
@Slf4j
public class DocumentSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            // documents→knowledges，再 document_category→knowledges_category，再分类种子
            populator.addScript(new ClassPathResource("db/archive/migration_rename_documents_to_knowledges.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_rename_document_category_to_knowledges_category.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_document_category.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 knowledges_category / knowledges 表结构与种子数据");
        } catch (Exception e) {
            log.warn("knowledges 表初始化跳过或失败（可手动执行 db/init_postgresql.sql 或 archive/migration_*.sql）: {}", e.getMessage());
        }
    }
}
