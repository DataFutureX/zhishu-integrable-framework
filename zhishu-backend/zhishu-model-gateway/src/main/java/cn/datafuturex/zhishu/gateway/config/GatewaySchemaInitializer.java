package cn.datafuturex.zhishu.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 网关建表与结构自愈。
 * <p>
 * 执行顺序：
 * <ol>
 *   <li>{@code db/init_gateway.sql}（唯一权威脚本：建全量表结构 + 非破坏性结构对齐）</li>
 *   <li>校验必需列；仍缺失则标记网关不可用并给出可操作提示（不阻断应用启动）</li>
 * </ol>
 * 历史库的数据下沉与旧列清理不在此自动执行，需手工跑
 * {@code db/patch_provider_model_split.sql} 与 {@code db/patch_drop_model_legacy.sql}。
 */
@Slf4j
@Component
@Order(17)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zhishu.gateway.enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySchemaInitializer implements ApplicationRunner {

    private static final String TABLE = "ai_gateway_model";
    private static final List<String> REQUIRED_COLUMNS =
            List.of("model_type", "temperature", "top_p", "max_tokens");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final GatewayReadiness readiness;

    /**
     * 执行建表与结构自愈。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        runScript("db/init_gateway.sql");

        List<String> missing = REQUIRED_COLUMNS.stream().filter(col -> !columnExists(col)).toList();
        if (missing.isEmpty()) {
            readiness.markReady();
            return;
        }
        readiness.markNotReady("表 " + TABLE + " 缺少必需列 " + missing
                + "；请确认 db/init_gateway.sql 执行成功（若库仍持有供应商历史列，请先手工执行 archive/patch_provider_model_split.sql）");
    }

    private void runScript(String path) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource(path));
            populator.execute(dataSource);
            log.info("已执行模型网关脚本: {}", path);
        } catch (Exception e) {
            log.error("模型网关脚本执行失败 {}: {}", path, e.getMessage(), e);
        }
    }

    private boolean columnExists(String column) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                     WHERE table_name = ? AND column_name = ?
                    """, Integer.class, TABLE, column);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("校验列 {}.{} 失败: {}", TABLE, column, e.getMessage());
            return false;
        }
    }
}
