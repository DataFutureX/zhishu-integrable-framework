package cn.datafuturex.zhishu.mcp.provide.catalog;

import org.springframework.ai.tool.ToolCallback;

/**
 * MCP 模块内置对外 Tool 的注册接口。
 * <p>
 * <b>刻意不 extends {@link ToolCallback}</b>：Spring AI 的
 * {@code ToolCallbackConverterAutoConfiguration} 会把容器内<b>所有</b> {@code ToolCallback} Bean
 * 与 {@code ToolCallbackProvider} 的输出一起聚合进 {@code tools/list}。若本接口的实现本身是
 * {@code ToolCallback} Bean，裸实例会与守卫包装后的实例同名并存，并按聚合顺序抢先出现，
 * 导致白名单 / RPM / 审计被绕过。因此这里只提供「产出一个 ToolCallback」的工厂方法，
 * 由 {@link cn.datafuturex.zhishu.mcp.provide.config.McpProvideAutoConfiguration} 统一加守卫后交给
 * {@code ToolCallbackProvider}，容器内不存在未包装的 {@code ToolCallback} Bean。
 */
public interface McpProvidedTool {

    /**
     * 对外 Tool 的实现体（尚未包装守卫）。
     */
    ToolCallback toolCallback();
}
