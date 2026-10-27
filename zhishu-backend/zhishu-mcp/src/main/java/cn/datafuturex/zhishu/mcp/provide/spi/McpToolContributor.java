package cn.datafuturex.zhishu.mcp.provide.spi;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * provide 平面的扩展点：他方模块把自己的业务 Tool 贡献给对外 MCP 目录。
 * <p>
 * 由 {@code zhishu-ai} 实现（委托 {@code BizToolProviderPort}），使 MCP 模块无需反向依赖 AI 内核。
 * 贡献进来的 Tool 一律会被 {@code McpGuardedToolCallback} 包装（能力白名单 + RPM + 审计）。
 */
public interface McpToolContributor {

    /**
     * 来源标识（日志与排障用，如 {@code ai-biztools}）。
     */
    String source();

    /**
     * 候选 Tool；目录过滤与守卫包装由 provide 平面负责，实现方不必关心。
     *
     * @return 工具回调列表，可为空
     */
    List<ToolCallback> contribute();
}
