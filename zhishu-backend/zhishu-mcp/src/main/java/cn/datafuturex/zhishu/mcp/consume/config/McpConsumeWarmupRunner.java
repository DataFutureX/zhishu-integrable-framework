package cn.datafuturex.zhishu.mcp.consume.config;

import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.consume.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * consume 平面启动预热：连接所有 ENABLED 上游并刷新工具缓存。
 * <p>
 * 排在 {@code McpSchemaInitializer}（{@code @Order(30)}）之后，确保表已存在；
 * 单个上游连接失败只记 WARN，不阻断启动。
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class McpConsumeWarmupRunner implements ApplicationRunner {

    private final McpProperties properties;
    private final AiMcpUpstreamMapper upstreamMapper;
    private final McpUpstreamConnectionManager connectionManager;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.consumeEnabled() || !properties.getConsume().isWarmupOnStartup()) {
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
