package cn.datafuturex.zhishu.ai.mcp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.ai.mcp.domain.entity.AiMcpClientEntity;
import cn.datafuturex.zhishu.ai.mcp.mapper.AiMcpClientMapper;
import cn.datafuturex.zhishu.ai.mcp.support.McpCallerContext;
import cn.datafuturex.zhishu.ai.mcp.support.McpCrypto;
import cn.datafuturex.zhishu.ai.mcp.support.McpJson;
import cn.datafuturex.zhishu.ai.mcp.support.McpOutboundCatalog;
import cn.datafuturex.zhishu.ai.shared.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class McpAuthService {

    private final AiMcpClientMapper clientMapper;
    private final McpClientAdminService clientAdminService;

    public boolean authenticate(String bearerToken) {
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("wxmcp_")) {
            return false;
        }
        String prefix = McpCrypto.keyPrefix(bearerToken);
        AiMcpClientEntity entity = clientMapper.selectOne(
                new LambdaQueryWrapper<AiMcpClientEntity>()
                        .eq(AiMcpClientEntity::getKeyPrefix, prefix)
                        .last("LIMIT 1"));
        if (entity == null || !"ENABLED".equalsIgnoreCase(entity.getStatus())) {
            return false;
        }
        if (!McpCrypto.sha256Hex(bearerToken).equalsIgnoreCase(entity.getSecretHash())) {
            return false;
        }
        Set<String> tools = allowedTools(McpJson.parseStringList(entity.getCapabilities()));
        McpCallerContext.set(new McpCallerContext.Caller(
                entity.getId(),
                entity.getName(),
                entity.getBoundUserId(),
                entity.getBoundUsername(),
                tools,
                entity.getRpmLimit() == null ? 60 : entity.getRpmLimit()));
        UserContext.setUserId(entity.getBoundUserId() == null ? null : String.valueOf(entity.getBoundUserId()));
        UserContext.setUsername(entity.getBoundUsername());
        clientAdminService.touchLastUsed(entity.getId());
        return true;
    }

    public static Set<String> allowedTools(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Set.copyOf(McpOutboundCatalog.DEFAULT_TOOL_NAMES);
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String cap : capabilities) {
            switch (cap.trim().toUpperCase()) {
                case "STATION_LATEST" -> names.add("queryStationLatestElements");
                case "STATION_HISTORY" -> names.add("queryStationHistoryElements");
                case "STATION_COMPARE" -> names.add("compareStations");
                case "ONLINE" -> {
                    names.add("getTerminalOnlineOverview");
                    names.add("queryTerminalOnlineStatus");
                    names.add("listTerminals");
                }
                case "PROJECT" -> names.add("listProjects");
                case "ALERT" -> {
                    names.add("queryRecentAlerts");
                    names.add("queryAlertTrends");
                }
                case "INSPECTION_PLAN" -> {
                    names.add("listInspectionPlans");
                    names.add("getInspectionPlan");
                }
                case "INSPECTION_TASK" -> {
                    names.add("listInspectionTasks");
                    names.add("getInspectionTaskDetail");
                }
                case "INSPECTION_ISSUE" -> names.add("listOpenInspectionIssues");
                case "INSPECTION_SUMMARY" -> names.add("getInspectionSummary");
                case "KNOWLEDGE_GRAPH" -> {
                    names.add("searchGraphEntities");
                    names.add("getGraphNeighbors");
                    names.add("findGraphPath");
                    names.add("getProjectTopology");
                    names.add("getAlertImpact");
                }
                default -> {
                    // NL2SQL / RAG 等不对 MCP 开放
                }
            }
        }
        return names;
    }
}
