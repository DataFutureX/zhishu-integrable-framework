package cn.datafuturex.zhishu.ai.biztools.mcp;

import cn.datafuturex.zhishu.ai.biztools.api.BizToolProviderPort;
import cn.datafuturex.zhishu.mcp.provide.spi.McpToolContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 AI 业务 Tool Bean 贡献给 MCP 的对外目录（provide 平面 SPI 实现）。
 * <p>
 * 方向刻意是「AI 实现 MCP 的接口」：{@code zhishu-mcp} 不反向依赖 AI 内核。
 * 当前 {@link BizToolProviderPort} 为空实现（监测类 Tool 已改由万象 MCP 上游提供），
 * 因此对外目录只剩 MCP 模块内置 Tool；业务 Tool 回归时此处无需改动。
 */
@Component
@RequiredArgsConstructor
public class BizToolMcpContributor implements McpToolContributor {

    private final BizToolProviderPort bizToolProviderPort;

    @Override
    public String source() {
        return "ai-biztools";
    }

    @Override
    public List<ToolCallback> contribute() {
        Object[] beans = bizToolProviderPort.toolBeans();
        if (beans == null || beans.length == 0) {
            return List.of();
        }
        return List.of(ToolCallbacks.from(beans));
    }
}
