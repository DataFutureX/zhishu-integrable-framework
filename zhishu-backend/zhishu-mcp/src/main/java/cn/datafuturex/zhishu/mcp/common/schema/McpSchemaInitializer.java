package cn.datafuturex.zhishu.mcp.common.schema;

import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * MCP 表结构初始化（幂等、非破坏性）。
 * <p>
 * 只负责建表；上游连接预热属 consume 平面，见 {@code McpConsumeWarmupRunner}。
 * 历史数据归一（{@code ai_mcp_call_log.direction}）需人工执行
 * {@code db/patch_mcp_call_log_direction.sql}，不在此处自动跑。
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class McpSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final McpProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("MCP 模块已关闭（zhishu.mcp.enabled=false），跳过表结构初始化");
            return;
        }
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/mcp_schema.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 MCP 双平面表结构");
            if (!StringUtils.hasText(properties.getCryptoKey())) {
                log.warn("未配置 zhishu.mcp.crypto-key，上游 Authorization 将明文存储");
            }
        } catch (Exception e) {
            log.warn("MCP 表初始化跳过或失败: {}", e.getMessage());
        }
    }
}
