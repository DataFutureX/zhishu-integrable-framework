package cn.datafuturex.zhishu.ai.agent.config;

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
 * 启动时确保 ai_agent / qa_history.agent_id 已就绪（幂等）。
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class AgentSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            // 历史增量已归档；启动仍幂等执行，新库请直接用 db/init_postgresql.sql
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_phase2.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_report_period.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_inspection.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_i2_tools.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_nl2sql.sql"));
            populator.addScript(new ClassPathResource("db/archive/migration_ai_agent_kg.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 ai_agent / ai_agent_run 表结构与种子数据（含巡检/NL2SQL/知识图谱智能体）");
        } catch (Exception e) {
            log.warn("ai_agent 表初始化跳过或失败（可手动执行 db/init_postgresql.sql 或 archive/migration_ai_agent*.sql）: {}", e.getMessage());
        }
    }
}
