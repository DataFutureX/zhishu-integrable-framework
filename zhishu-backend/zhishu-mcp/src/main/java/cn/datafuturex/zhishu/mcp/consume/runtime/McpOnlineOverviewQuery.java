package cn.datafuturex.zhishu.mcp.consume.runtime;

import cn.datafuturex.zhishu.mcp.consume.api.McpOnlineOverview;
import cn.datafuturex.zhishu.mcp.consume.api.McpTerminalOverviewQuery;
import cn.datafuturex.zhishu.mcp.consume.client.McpUpstreamConnectionManager;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.entity.AiMcpUpstreamToolEntity;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamMapper;
import cn.datafuturex.zhishu.mcp.consume.upstream.mapper.AiMcpUpstreamToolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 经万象等已启用 MCP 上游拉取遥测站在线概览，产出中立模型 {@link McpOnlineOverview}。
 * <p>
 * 平台展示契约（Markdown 表格 / 结构化对比结果）由 {@code zhishu-ai} 侧适配器映射，
 * 本类不引用任何 AI 类型，以保证 {@code mcp} 与 AI 内核单向依赖。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpOnlineOverviewQuery implements McpTerminalOverviewQuery {

    private static final String PREFERRED_UPSTREAM = "wanxiang-monitor";
    private static final String TOOL_SUFFIX = "getTerminalOnlineOverview";

    private final AiMcpUpstreamMapper upstreamMapper;
    private final AiMcpUpstreamToolMapper upstreamToolMapper;
    private final McpUpstreamConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public McpOnlineOverview fetch(int detailLimit) {
        int limit = detailLimit <= 0 ? DEFAULT_DETAIL_LIMIT : detailLimit;
        try {
            AiMcpUpstreamEntity upstream = pickUpstream();
            if (upstream == null) {
                return McpOnlineOverview.fail("未找到已启用的万象 MCP 上游，请在 MCP Hub 启用 wanxiang-monitor");
            }
            ToolCallback tool = findOverviewTool(upstream.getId());
            if (tool == null) {
                return McpOnlineOverview.fail(
                        "上游「" + upstream.getName() + "」未提供 getTerminalOnlineOverview，请先探测并刷新工具列表");
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
            return McpOnlineOverview.fail("调用万象 MCP 失败: " + e.getMessage());
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
        for (ToolCallback callback : tools) {
            String name = callback.getToolDefinition().name();
            if (name != null && name.endsWith(TOOL_SUFFIX)) {
                return callback;
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

    private McpOnlineOverview parse(String raw, int limit, String sourceName) {
        if (!StringUtils.hasText(raw)) {
            return McpOnlineOverview.fail("万象 MCP 返回空结果");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            String md = "来源：" + sourceName + "\n\n" + trimmed;
            return new McpOnlineOverview(true, null, 0, 0, 0, trimmed, md, List.of());
        }
        try {
            UnwrappedPayload unwrapped = unwrapPayload(objectMapper.readTree(trimmed));
            if (unwrapped.plainMarkdown() != null) {
                String text = unwrapped.plainMarkdown().trim();
                String md = "来源：" + sourceName + "\n\n" + text;
                return new McpOnlineOverview(true, null, 0, 0, 0, text, md, List.of());
            }
            JsonNode payload = unwrapped.payload();
            if (payload == null || payload.isNull()) {
                return McpOnlineOverview.fail("万象 MCP 返回空结果");
            }
            if (payload.has("found") && !payload.get("found").asBoolean(true)) {
                String message = textVal(payload, "message");
                return McpOnlineOverview.fail(
                        StringUtils.hasText(message) ? message : "万象 MCP 未返回在线概览数据");
            }
            String messageOnly = textVal(payload, "message");
            int total = intVal(payload, "total", "totalCount");
            int online = intVal(payload, "onlineCount", "online", "onlineNum");
            int offline = intVal(payload, "offlineCount", "offline", "offlineNum");
            if (total <= 0 && online <= 0 && offline <= 0 && StringUtils.hasText(messageOnly)
                    && !payload.has("items") && !payload.has("markdown")) {
                return McpOnlineOverview.fail(messageOnly);
            }
            JsonNode itemsNode = firstArray(payload, "items", "terminals", "list", "rows");
            List<McpOnlineOverview.Item> items = new ArrayList<>();
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
                            String remark = i.remark();
                            return remark != null && (remark.startsWith("在线") || "online".equalsIgnoreCase(remark));
                        })
                        .count();
                offline = Math.max(0, total - online);
            }
            String summary = String.format("来源：%s。共 %d 站，在线 %d，离线 %d。", sourceName, total, online, offline);
            String upstreamMarkdown = textVal(payload, "markdown");
            String markdown = StringUtils.hasText(upstreamMarkdown)
                    ? "来源：" + sourceName + "\n\n" + upstreamMarkdown.trim()
                    : toMarkdown(summary, items, total, limit);
            return new McpOnlineOverview(true, null, total, online, offline, summary, markdown, items);
        } catch (Exception e) {
            log.warn("解析万象 MCP 在线概览 JSON 失败: {}", e.getMessage());
            String md = "来源：" + sourceName + "\n\n```json\n" + clip(trimmed, 4000) + "\n```";
            return new McpOnlineOverview(true, null, 0, 0, 0, trimmed, md, List.of());
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

    private static McpOnlineOverview.Item toItem(JsonNode item) {
        String name = textVal(item,
                "terminalCode", "stationAddress", "name", "stationName", "terminalName", "address");
        String time = textVal(item, "observeTime", "lastCommTime", "lastTime", "lastReportTime", "updateTime");
        String status = statusText(item);
        Double value = "在线".equals(status) ? 1d : 0d;
        String remark = textVal(item, "remark");
        if (!StringUtils.hasText(remark)) {
            remark = status;
        }
        return new McpOnlineOverview.Item(name, time, value, remark);
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

    private static String toMarkdown(String summary, List<McpOnlineOverview.Item> items, int total, int limit) {
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
        for (McpOnlineOverview.Item item : items) {
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
                String value = item.get(name).asText();
                if (StringUtils.hasText(value)) {
                    return value;
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
