package cn.datafuturex.zhishu.mcp.consume.api;

import java.util.List;

/**
 * 他方 MCP 返回的「遥测站在线概览」中立模型。
 * <p>
 * 刻意不引用 {@code ai.shared.vo.structured.StationCompareResult}：MCP 协议侧只产出通用字段，
 * 平台展示契约由 {@code zhishu-ai} 的适配器负责映射，从而保证 {@code mcp} 不依赖 AI 内核。
 *
 * @param found       是否取到概览数据
 * @param message     失败或提示文案（{@code found=false} 时用于直出）
 * @param total       站点总数
 * @param onlineCount 在线数
 * @param offlineCount 离线数
 * @param summary     一句话摘要
 * @param markdown    可直出的 Markdown 表格（上游提供或本地拼装）
 * @param items       明细行（已按上限截断）
 */
public record McpOnlineOverview(
        boolean found,
        String message,
        int total,
        int onlineCount,
        int offlineCount,
        String summary,
        String markdown,
        List<Item> items
) {

    /**
     * 单站明细。
     *
     * @param stationAddress 站点标识 / 地址
     * @param observeTime    最近通讯时间
     * @param value          在线标记值（在线 1、离线 0，未知 null）
     * @param remark         状态文案
     */
    public record Item(String stationAddress, String observeTime, Double value, String remark) {
    }

    /**
     * 构造失败结果（保留原始文案，供上层直出提示）。
     */
    public static McpOnlineOverview fail(String message) {
        return new McpOnlineOverview(false, message, 0, 0, 0, message, message, List.of());
    }
}
