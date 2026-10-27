package cn.datafuturex.zhishu.ai.biztools.mcp;

import cn.datafuturex.zhishu.ai.biztools.api.TerminalOverviewPort;
import cn.datafuturex.zhishu.ai.shared.vo.structured.StationCompareResult;
import cn.datafuturex.zhishu.mcp.consume.api.McpOnlineOverview;
import cn.datafuturex.zhishu.mcp.consume.api.McpTerminalOverviewQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 MCP consume 平面的中立在线概览映射为平台展示契约（供 chat 短路直出表格）。
 * <p>
 * 放在 AI 侧而非 MCP 侧：{@link StationCompareResult} 是平台展示协议，MCP 模块只产出通用字段。
 * MCP 模块缺席或被关闭时（{@link McpTerminalOverviewQuery} Bean 不存在）返回提示文案，不影响主链路。
 */
@Component
@RequiredArgsConstructor
public class McpTerminalOverviewAdapter implements TerminalOverviewPort {

    private static final String DISABLED_HINT = "监测 Tool 未接入（请在 MCP Hub 启用万象 MCP 上游）";

    private final ObjectProvider<McpTerminalOverviewQuery> queryProvider;

    @Override
    public Overview build(int detailLimit) {
        McpTerminalOverviewQuery query = queryProvider.getIfAvailable();
        if (query == null) {
            return empty(DISABLED_HINT);
        }
        McpOnlineOverview source = query.fetch(detailLimit);
        if (source == null) {
            return empty(DISABLED_HINT);
        }
        if (!source.found()) {
            String message = source.message() == null ? DISABLED_HINT : source.message();
            return empty(message);
        }
        List<StationCompareResult.StationCompareItem> items = source.items().stream()
                .map(item -> new StationCompareResult.StationCompareItem(
                        item.stationAddress(), item.observeTime(), item.value(), item.remark()))
                .toList();
        String element = items.isEmpty() ? null : "online";
        StationCompareResult structured = new StationCompareResult(element, source.summary(), items);
        return new Overview(
                source.total(),
                source.onlineCount(),
                source.offlineCount(),
                items,
                source.summary(),
                source.markdown(),
                structured);
    }

    private static Overview empty(String message) {
        StationCompareResult empty = new StationCompareResult(null, message, List.of());
        return new Overview(0, 0, 0, List.of(), message, message, empty);
    }
}
