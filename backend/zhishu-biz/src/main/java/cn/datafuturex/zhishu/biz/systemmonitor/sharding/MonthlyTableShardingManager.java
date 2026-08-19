package cn.datafuturex.zhishu.biz.systemmonitor.sharding;

import cn.datafuturex.zhishu.biz.systemmonitor.config.TableShardingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 按月分表预建：PostgreSQL 使用 CREATE TABLE ... (LIKE template INCLUDING ALL)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(TableShardingProperties.class)
@ConditionalOnProperty(prefix = "yunqi.table-sharding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonthlyTableShardingManager implements ApplicationRunner {

    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    private final DataSource dataSource;
    private final TableShardingProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        ensureConfiguredRange();
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void ensureTablesDaily() {
        ensureConfiguredRange();
    }

    @Scheduled(cron = "0 5 0 1 * ?")
    public void ensureTablesMonthly() {
        ensureConfiguredRange();
    }

    public void ensureConfiguredRange() {
        if (!properties.isEnabled()) {
            return;
        }
        List<TableShardingProperties.Strategy> strategies = properties.getStrategies();
        if (strategies == null || strategies.isEmpty()) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        YearMonth current = YearMonth.from(LocalDate.now());
        for (TableShardingProperties.Strategy strategy : strategies) {
            if (!strategy.isAutoCreate()) {
                continue;
            }
            if (!StringUtils.hasText(strategy.getTablePrefix()) || !StringUtils.hasText(strategy.getTemplateTable())) {
                log.warn("分表策略配置不完整，跳过预建: name={}", strategy.getName());
                continue;
            }
            int behind = Math.max(strategy.getMonthsBehind(), 0);
            int ahead = Math.max(strategy.getMonthsAhead(), 0);
            for (int i = -behind; i <= ahead; i++) {
                createTableIfAbsent(jdbc, strategy, current.plusMonths(i));
            }
        }
    }

    private void createTableIfAbsent(JdbcTemplate jdbc, TableShardingProperties.Strategy strategy, YearMonth yearMonth) {
        String tableName = sanitizeIdent(strategy.getTablePrefix() + yearMonth.format(MONTH_SUFFIX));
        String template = sanitizeIdent(strategy.getTemplateTable());
        try {
            if (!tableExists(jdbc, template)) {
                log.warn("分表模板不存在，跳过: strategy={}, template={}", strategy.getName(), template);
                return;
            }
            if (tableExists(jdbc, tableName)) {
                log.debug("分表已存在: strategy={}, table={}", strategy.getName(), tableName);
                return;
            }
            jdbc.execute(buildCreateLikeSql(tableName, template));
            log.debug("分表已就绪: strategy={}, table={}", strategy.getName(), tableName);
        } catch (Exception e) {
            log.error("创建分表失败: strategy={}, table={}", strategy.getName(), tableName, e);
        }
    }

    private boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Boolean exists = jdbc.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = current_schema()
                              AND lower(table_name) = lower(?)
                        )
                        """,
                Boolean.class,
                tableName);
        return Boolean.TRUE.equals(exists);
    }

    private String buildCreateLikeSql(String tableName, String template) {
        if (isPostgreSql()) {
            return "CREATE TABLE IF NOT EXISTS " + tableName + " (LIKE " + template + " INCLUDING ALL)";
        }
        return "CREATE TABLE IF NOT EXISTS " + tableName + " AS SELECT * FROM " + template + " WHERE 1=0";
    }

    private boolean isPostgreSql() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String product = meta.getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception e) {
            return true;
        }
    }

    private static String sanitizeIdent(String ident) {
        if (!StringUtils.hasText(ident) || !ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法表名: " + ident);
        }
        return ident;
    }
}
