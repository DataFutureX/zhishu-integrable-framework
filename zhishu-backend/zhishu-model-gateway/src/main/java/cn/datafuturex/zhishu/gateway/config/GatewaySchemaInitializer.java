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
 *   <li>{@code db/init_gateway.sql}（新库建全量结构）</li>
 *   <li>若缺少 v2 列且默认供应商唯一，自动执行 {@code db/patch_provider_model_split.sql}</li>
 *   <li>校验必需列；仍缺失则标记网关不可用并给出可操作提示（不阻断应用启动）</li>
 * </ol>
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
        try {
            if (!columnExists("model_type")) {
                Integer defaults = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM ai_model_provider WHERE is_default = TRUE", Integer.class);
                if (defaults != null && defaults > 1) {
                    readiness.markNotReady("存在 " + defaults + " 条默认供应商(is_default=TRUE)，已跳过自动迁移；"
                            + "请先收敛为一条后执行 db/patch_provider_model_split.sql");
                    return;
                }
                log.warn("检测到模型网关缺少 v2 列，自动执行 patch_provider_model_split.sql");
                runScript("db/patch_provider_model_split.sql");
            }
        } catch (Exception e) {
            log.error("模型网关结构自愈失败: {}", e.getMessage(), e);
        }

        List<String> missing = REQUIRED_COLUMNS.stream().filter(col -> !columnExists(col)).toList();
        if (missing.isEmpty()) {
            readiness.markReady();
            return;
        }
        readiness.markNotReady("表 " + TABLE + " 缺少必需列 " + missing
                + "；请执行 zhishu-backend/zhishu-core/src/main/resources/db/patch_provider_model_split.sql 后重启");
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
