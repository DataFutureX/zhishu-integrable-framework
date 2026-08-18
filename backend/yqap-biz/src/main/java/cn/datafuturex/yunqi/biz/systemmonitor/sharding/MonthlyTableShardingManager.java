package cn.datafuturex.yunqi.biz.systemmonitor.sharding;

import cn.datafuturex.yunqi.biz.systemmonitor.config.TableShardingProperties;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按月分表预建：对配置的策略执行 CREATE TABLE IF NOT EXISTS ... LIKE template。
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
        String tableName = strategy.getTablePrefix() + yearMonth.format(MONTH_SUFFIX);
        String template = strategy.getTemplateTable();
        try {
            Integer templateExists = jdbc.queryForObject(
                    """
                            SELECT COUNT(1) FROM information_schema.TABLES
                            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                            """,
                    Integer.class,
                    template);
            if (templateExists == null || templateExists == 0) {
                log.warn("分表模板不存在，跳过: strategy={}, template={}", strategy.getName(), template);
                return;
            }
            jdbc.execute("CREATE TABLE IF NOT EXISTS `" + tableName + "` LIKE `" + template + "`");
            log.debug("分表已就绪: strategy={}, table={}", strategy.getName(), tableName);
        } catch (Exception e) {
            log.error("创建分表失败: strategy={}, table={}", strategy.getName(), tableName, e);
        }
    }
}
