package cn.datafuturex.zhishu.mcp.consume.api;

/**
 * consume 平面对外出口：经他方 MCP（如万象监测）拉取遥测站在线概览。
 * <p>
 * 由 {@code zhishu-ai} 侧适配器映射为平台展示契约，供会话短路直出表格使用。
 */
public interface McpTerminalOverviewQuery {

    /** 明细默认条数上限 */
    int DEFAULT_DETAIL_LIMIT = 50;

    /**
     * 拉取在线概览。
     *
     * @param detailLimit 明细条数上限，&lt;=0 时使用 {@link #DEFAULT_DETAIL_LIMIT}
     * @return 中立概览模型；无可用上游或调用失败时 {@code found=false}
     */
    McpOnlineOverview fetch(int detailLimit);
}
