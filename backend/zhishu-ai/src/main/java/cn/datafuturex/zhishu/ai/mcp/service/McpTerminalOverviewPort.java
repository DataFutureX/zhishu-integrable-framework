package cn.datafuturex.zhishu.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.datafuturex.zhishu.ai.biztools.api.TerminalOverviewPort;
import cn.datafuturex.zhishu.ai.mcp.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpUpstreamToolEntity;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpUpstreamToolMapper;
import cn.datafuturex.zhishu.ai.shared.vo.structured.StationCompareResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 经万象等已启用 MCP 上游拉取遥测站在线概览，供会话短路直出表格。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpTerminalOverviewPort implements TerminalOverviewPort {

    private static final String PREFERRED_UPSTREAM = "wanxiang-monitor";
    private static final String TOOL_SUFFIX = "getTerminalOnlineOverview";

    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper upstreamToolMapper;
    private final McpUpstreamConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public Overview build(int detailLimit) {
        int limit = detailLimit <= 0 ? DEFAULT_DETAIL_LIMIT : detailLimit;
        try {
            AiMcpUpstreamEntity upstream = pickUpstream();
            if (upstream == null) {
                return fail("未找到已启用的万象 MCP 上游，请在 MCP Hub 启用 wanxiang-monitor");
            }
            ToolCallback tool = findOverviewTool(upstream.getId());
            if (tool == null) {
                return fail("上游「" + upstream.getName() + "」未提供 getTerminalOnlineOverview，请先探测并刷新工具列表");
            }
            String raw = tool.call("{\"limit\":" + limit + "}");
            if (!StringUtils.hasText(raw)) {
                raw = tool.call("{}");
            }
            log.info("万象 MCP 在线概览 upstream={}, chars={}, preview={}",
                    upstream.getCode(), raw == null ? 0 : raw.length(), clip(raw == null ? "" : raw, 240));
            return parse(raw, limit, upstream.getName());
        } catch (Exception e) {
            log.warn("万象 MCP 在线概览失败: {}", e.getMessage());
            return fail("调用万象 MCP 失败: " + e.getMessage());
        }
    }

    private AiMcpUpstreamEntity pickUpstream() {
        AiMcpUpstreamEntity preferred = upstreamMapper.selectOne(
                new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .eq(AiMcpUpstreamEntity::getCode, PREFERRED_UPSTREAM)
                        .eq(AiMcpUpstreamEntity::getStatus, "ENABLED"));
        if (preferred != null) {
            return preferred;
        }
        List<AiMcpUpstreamEntity> enabled = upstreamMapper.selectList(
                new LambdaQueryWrapper<AiMcpUpstreamEntity>()
                        .eq(AiMcpUpstreamEntity::getStatus, "ENABLED"));
        return enabled.stream()
                .sorted(Comparator.comparing(u -> !"UP".equalsIgnoreCase(u.getHealthStatus())))
                .findFirst()
                .orElse(null);
    }

    private ToolCallback findOverviewTool(Long upstreamId) {
        // 1. 先检查 DB 工具缓存：管理员是否禁用了该工具
        if (!isToolEnabledInDb(upstreamId, TOOL_SUFFIX)) {
            log.debug("工具 {} 在 DB 中未启用 upstreamId={}", TOOL_SUFFIX, upstreamId);
            return null;
        }
        // 2. 从活跃连接获取工具（原名的 ToolCallback）
        List<ToolCallback> tools = connectionManager.callbacks(upstreamId);
        ToolCallback found = matchTool(tools);
        // 3. 活跃连接未找到 → 强制重连再试一次（对端可能更新了工具列表）
        if (found == null) {
            log.info("活跃连接未找到 {} upstreamId={}，尝试重新连接", TOOL_SUFFIX, upstreamId);
            AiMcpUpstreamEntity entity = upstreamMapper.selectById(upstreamId);
            if (entity != null) {
                try {
                    tools = connectionManager.connectAndList(entity);
                    found = matchTool(tools);
                } catch (Exception e) {
                    log.warn("重连上游 MCP 失败 id={}: {}", upstreamId, e.getMessage());
                }
            }
        }
        return found;
    }

    private static ToolCallback matchTool(List<ToolCallback> tools) {
        for (ToolCallback cb : tools) {
            String name = cb.getToolDefinition().name();
            if (name != null && name.endsWith(TOOL_SUFFIX)) {
                return cb;
            }
        }
        return null;
    }

    /**
     * 检查 DB 工具缓存中是否存在原名以指定后缀结尾且已启用的工具。
     * 尊重管理员在 MCP Hub 中的启用/禁用设置。
     */
    private boolean isToolEnabledInDb(Long upstreamId, String suffix) {
        List<AiMcpUpstreamToolEntity> rows = upstreamToolMapper.selectList(
                new LambdaQueryWrapper<AiMcpUpstreamToolEntity>()
                        .eq(AiMcpUpstreamToolEntity::getUpstreamId, upstreamId));
        for (AiMcpUpstreamToolEntity row : rows) {
            if (row.getOriginalName() != null && row.getOriginalName().endsWith(suffix)) {
                return !Boolean.FALSE.equals(row.getEnabled());
            }
        }
        // DB 中无记录（尚未探测过）→ 视为未确认，允许尝试
        return rows.isEmpty();
    }

    private Overview parse(String raw, int limit, String sourceName) {
        if (!StringUtils.hasText(raw)) {
            return fail("万象 MCP 返回空结果");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            String md = "来源：" + sourceName + "\n\n" + trimmed;
            return new Overview(0, 0, 0, List.of(), trimmed, md, new StationCompareResult(null, trimmed, List.of()));
        }
        try {
            UnwrappedPayload unwrapped = unwrapPayload(objectMapper.readTree(trimmed));
            if (unwrapped.plainMarkdown() != null) {
                String md = "来源：" + sourceName + "\n\n" + unwrapped.plainMarkdown().trim();
                return new Overview(0, 0, 0, List.of(), unwrapped.plainMarkdown(), md,
                        new StationCompareResult("online", unwrapped.plainMarkdown(), List.of()));
            }
            JsonNode payload = unwrapped.payload();
            if (payload == null || payload.isNull()) {
                return fail("万象 MCP 返回空结果");
            }
            if (payload.has("found") && !payload.get("found").asBoolean(true)) {
                String message = textVal(payload, "message");
                return fail(StringUtils.hasText(message) ? message : "万象 MCP 未返回在线概览数据");
            }
            String messageOnly = textVal(payload, "message");
            int total = intVal(payload, "total", "totalCount");
            int online = intVal(payload, "onlineCount", "online", "onlineNum");
            int offline = intVal(payload, "offlineCount", "offline", "offlineNum");
            if (total <= 0 && online <= 0 && offline <= 0 && StringUtils.hasText(messageOnly)
                    && !payload.has("items") && !payload.has("markdown")) {
                return fail(messageOnly);
            }
            JsonNode itemsNode = firstArray(payload, "items", "terminals", "list", "rows");
            List<StationCompareResult.StationCompareItem> items = new ArrayList<>();
            if (itemsNode != null) {
                int n = 0;
                for (JsonNode item : itemsNode) {
                    if (!isTerminalItem(item)) {
                        continue;
                    }
                    if (n++ >= limit) {
                        break;
                    }
                    items.add(toItem(item));
                }
            }
            if (total <= 0 && !items.isEmpty()) {
                total = items.size();
            }
            if (online + offline != total && !items.isEmpty()) {
                online = (int) items.stream()
                        .filter(i -> {
                            String r = i.remark();
                            return r != null && (r.startsWith("在线") || "online".equalsIgnoreCase(r));
                        })
                        .count();
                offline = Math.max(0, total - online);
            }
            String summary = String.format("来源：%s。共 %d 站，在线 %d，离线 %d。", sourceName, total, online, offline);
            String upstreamMarkdown = textVal(payload, "markdown");
            String markdown = StringUtils.hasText(upstreamMarkdown)
                    ? "来源：" + sourceName + "\n\n" + upstreamMarkdown.trim()
                    : toMarkdown(summary, items, total, limit);
            StationCompareResult structured = new StationCompareResult("online", summary, items);
            return new Overview(total, online, offline, items, summary, markdown, structured);
        } catch (Exception e) {
            log.warn("解析万象 MCP 在线概览 JSON 失败: {}", e.getMessage());
            String md = "来源：" + sourceName + "\n\n```json\n" + clip(trimmed, 4000) + "\n```";
            return new Overview(0, 0, 0, List.of(), trimmed, md, new StationCompareResult(null, trimmed, List.of()));
        }
    }

    /**
     * 解包 MCP tools/call 结果。
     * <p>
     * Spring AI {@code SyncMcpToolCallback} 返回 {@code jsonHelper.toJson(response.content())}，
     * 即根节点为 {@code [{"type":"text","text":"{...}"}]}，而非 {@code {"content":[...]}}。
     */
    private UnwrappedPayload unwrapPayload(JsonNode root) throws java.io.IOException {
        JsonNode node = root;
        if (node.isTextual()) {
            node = objectMapper.readTree(node.asText());
        }
        if (node.isArray()) {
            return unwrapContentBlocks(node);
        }
        if (node.has("result")) {
            JsonNode result = node.get("result");
            if (result.isTextual()) {
                node = objectMapper.readTree(result.asText());
            } else {
                node = result;
            }
            if (node.isArray()) {
                return unwrapContentBlocks(node);
            }
        }
        if (node.has("content") && node.get("content").isArray()) {
            UnwrappedPayload fromContent = unwrapContentBlocks(node.get("content"));
            if (fromContent.payload() != null || fromContent.plainMarkdown() != null) {
                return fromContent;
            }
        }
        if (node.has("data") && node.get("data").isObject()) {
            return new UnwrappedPayload(node.get("data"), null);
        }
        return new UnwrappedPayload(node, null);
    }

    private UnwrappedPayload unwrapContentBlocks(JsonNode blocks) throws java.io.IOException {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : blocks) {
            if (block != null && block.has("text") && !block.get("text").isNull()) {
                text.append(block.get("text").asText());
            }
        }
        if (text.isEmpty()) {
            return new UnwrappedPayload(null, null);
        }
        String body = text.toString().trim();
        if (body.startsWith("{") || body.startsWith("[")) {
            JsonNode parsed = objectMapper.readTree(body);
            if (parsed.isTextual()) {
                parsed = objectMapper.readTree(parsed.asText());
            }
            return new UnwrappedPayload(parsed, null);
        }
        return new UnwrappedPayload(null, body);
    }

    private record UnwrappedPayload(JsonNode payload, String plainMarkdown) {
    }

    private static boolean isTerminalItem(JsonNode item) {
        if (item == null || !item.isObject()) {
            return false;
        }
        return item.has("terminalCode")
                || item.has("stationAddress")
                || item.has("stationName")
                || item.has("terminalName")
                || item.has("name")
                || item.has("onlineStatus")
                || item.has("online")
                || item.has("onlineLabel");
    }

    private static StationCompareResult.StationCompareItem toItem(JsonNode item) {
        String name = textVal(item,
                "terminalCode", "stationAddress", "name", "stationName", "terminalName", "address");
        String time = textVal(item, "observeTime", "lastCommTime", "lastTime", "lastReportTime", "updateTime");
        String status = statusText(item);
        Double value = "在线".equals(status) ? 1d : 0d;
        String remark = textVal(item, "remark");
        if (!StringUtils.hasText(remark)) {
            remark = status;
        }
        return new StationCompareResult.StationCompareItem(name, time, value, remark);
    }

    private static String statusText(JsonNode item) {
        String label = textVal(item, "onlineLabel");
        if (StringUtils.hasText(label)) {
            return label;
        }
        if (item.has("online")) {
            JsonNode n = item.get("online");
            if (n.isBoolean()) {
                return n.booleanValue() ? "在线" : "离线";
            }
            if (n.isNumber()) {
                return n.intValue() == 1 ? "在线" : "离线";
            }
        }
        if (item.has("onlineStatus") && item.get("onlineStatus").isNumber()) {
            return item.get("onlineStatus").asInt() == 1 ? "在线" : "离线";
        }
        String status = textVal(item, "status", "onlineStatus");
        if ("1".equals(status) || "ONLINE".equalsIgnoreCase(status) || "在线".equals(status)) {
            return "在线";
        }
        if ("0".equals(status) || "OFFLINE".equalsIgnoreCase(status) || "离线".equals(status)) {
            return "离线";
        }
        return StringUtils.hasText(status) ? status : "未知";
    }

    private static String toMarkdown(String summary, List<StationCompareResult.StationCompareItem> items, int total, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary);
        if (total > items.size()) {
            sb.append("下表列出前 ").append(items.size()).append(" 条（共 ").append(total).append("）。\n");
        } else {
            sb.append('\n');
        }
        sb.append('\n');
        if (items.isEmpty()) {
            sb.append("暂无站点明细。\n");
            return sb.toString();
        }
        sb.append("| 遥测站 | 最近通讯 | 状态 |\n| --- | --- | --- |\n");
        for (StationCompareResult.StationCompareItem item : items) {
            sb.append("| ")
                    .append(escapeCell(item.stationAddress()))
                    .append(" | ")
                    .append(escapeCell(item.observeTime()))
                    .append(" | ")
                    .append(escapeCell(item.remark()))
                    .append(" |\n");
        }
        if (total > limit) {
            sb.append("\n其余 ").append(total - items.size()).append(" 站已省略。\n");
        }
        return sb.toString();
    }

    private static Overview fail(String message) {
        StationCompareResult empty = new StationCompareResult(null, message, List.of());
        return new Overview(0, 0, 0, List.of(), message, message, empty);
    }

    private static JsonNode firstArray(JsonNode payload, String... names) {
        for (String name : names) {
            if (payload.has(name) && payload.get(name).isArray()) {
                return payload.get(name);
            }
        }
        return payload.isArray() ? payload : null;
    }

    private static int intVal(JsonNode node, String... names) {
        for (String name : names) {
            if (!node.has(name) || node.get(name).isNull()) {
                continue;
            }
            JsonNode value = node.get(name);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return 0;
    }

    private static String textVal(JsonNode item, String... names) {
        for (String name : names) {
            if (item.has(name) && !item.get(name).isNull()) {
                String v = item.get(name).asText();
                if (StringUtils.hasText(v)) {
                    return v;
                }
            }
        }
        return "";
    }

    private static String escapeCell(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
