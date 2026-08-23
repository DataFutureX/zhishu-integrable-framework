package cn.datafuturex.zhishu.ai.mcp.config;

import cn.datafuturex.zhishu.ai.mcp.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.List;

@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(McpProperties.class)
public class McpSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final McpProperties properties;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final McpUpstreamConnectionManager connectionManager;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/archive/migration_ai_mcp.sql"));
            populator.execute(dataSource);
            log.info("已检查/初始化 MCP 双平面表结构");
            if (!StringUtils.hasText(properties.getCryptoKey())) {
                log.warn("未配置 wanxiang.mcp.crypto-key，上游 Authorization 将明文存储");
            }
        } catch (Exception e) {
            log.warn("MCP 表初始化跳过或失败: {}", e.getMessage());
        }
        if (!properties.isEnabled() || !properties.isClientEnabled()) {
            return;
        }
        List<AiMcpUpstreamEntity> enabled = upstreamMapper.selectList(
                new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .eq(AiMcpUpstreamEntity::getStatus, "ENABLED"));
        for (AiMcpUpstreamEntity upstream : enabled) {
            try {
                connectionManager.connectAndList(upstream);
            } catch (Exception e) {
                log.warn("启动连接上游 MCP {} 失败: {}", upstream.getCode(), e.getMessage());
            }
        }
    }
}
