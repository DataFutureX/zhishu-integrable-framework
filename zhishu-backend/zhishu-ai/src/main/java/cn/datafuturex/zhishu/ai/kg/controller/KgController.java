package cn.datafuturex.zhishu.ai.kg.controller;

import cn.datafuturex.zhishu.ai.kg.api.KnowledgeGraphQueryPort;
import cn.datafuturex.zhishu.ai.kg.api.KnowledgeGraphSyncPort;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgNeighborResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgPathResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSearchHit;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgStatsVO;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSubgraphVO;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSyncStatusVO;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgTopologySummary;
import cn.datafuturex.zhishu.ai.kg.support.KgProjectAccess;
import cn.datafuturex.zhishu.ai.shared.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "知识图谱")
@RestController
@RequestMapping("/api/v1/kg")
@RequiredArgsConstructor
public class KgController {

    private final KnowledgeGraphQueryPort queryPort;
    private final KnowledgeGraphSyncPort syncPort;
    private final KgProjectAccess projectAccess;

    @Operation(summary = "Neo4j 连通性")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean ok = queryPort.isAvailable();
        return Result.success(Map.of(
                "connected", ok,
                "available", ok,
                "message", ok ? "ok" : "Neo4j 不可用或知识图谱未启用"
        ));
    }

    @Operation(summary = "图谱统计")
    @GetMapping("/stats")
    public Result<KgStatsVO> stats() {
        return Result.success(queryPort.stats());
    }

    @Operation(summary = "工程子图（可视化主数据；不传 projectId 表示全部有权限工程）")
    @GetMapping("/subgraph")
    public Result<KgSubgraphVO> subgraph(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "2") Integer depth,
            @RequestParam(required = false) String types) {
        try {
            Set<Long> scope = projectAccess.resolveProjectScope(projectId);
            Set<String> typeSet = parseTypes(types);
            return Result.success(queryPort.subgraph(projectId, depth == null ? 2 : depth, typeSet, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(403, e.getMessage());
        }
    }

    @Operation(summary = "实体搜索")
    @GetMapping("/search")
    public Result<List<KgSearchHit>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            Set<Long> scope = projectAccess.requireProjectScope();
            return Result.success(queryPort.search(q, limit == null ? 20 : limit, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(403, e.getMessage());
        }
    }

    @Operation(summary = "邻居展开")
    @GetMapping("/neighbors/{label}/{bizId}")
    public Result<KgNeighborResult> neighbors(
            @PathVariable String label,
            @PathVariable Long bizId,
            @RequestParam(defaultValue = "1") Integer depth) {
        try {
            Set<Long> scope = projectAccess.requireProjectScope();
            return Result.success(queryPort.neighbors(label, bizId, depth == null ? 1 : depth, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "最短路径")
    @GetMapping("/path")
    public Result<KgPathResult> path(
            @RequestParam String fromLabel,
            @RequestParam Long fromBizId,
            @RequestParam String toLabel,
            @RequestParam Long toBizId) {
        try {
            Set<Long> scope = projectAccess.requireProjectScope();
            return Result.success(queryPort.shortestPath(fromLabel, fromBizId, toLabel, toBizId, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "工程拓扑摘要")
    @GetMapping("/topology/{projectId}")
    public Result<KgTopologySummary> topology(@PathVariable Long projectId) {
        try {
            Set<Long> scope = projectAccess.resolveProjectScope(projectId);
            return Result.success(queryPort.projectTopology(projectId, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(403, e.getMessage());
        }
    }

    @Operation(summary = "告警影响面子图")
    @GetMapping("/impact/alert/{bizId}")
    public Result<KgNeighborResult> alertImpact(
            @PathVariable Long bizId,
            @RequestParam(defaultValue = "2") Integer depth) {
        try {
            Set<Long> scope = projectAccess.requireProjectScope();
            return Result.success(queryPort.alertImpact(bizId, depth == null ? 2 : depth, scope));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "触发同步")
    @PostMapping("/sync")
    public Result<KgSyncResult> sync(@RequestParam(defaultValue = "false") boolean full) {
        return Result.success(syncPort.sync(full));
    }

    @Operation(summary = "同步状态")
    @GetMapping("/sync/status")
    public Result<KgSyncStatusVO> syncStatus() {
        return Result.success(syncPort.status());
    }

    private static Set<String> parseTypes(String types) {
        if (!StringUtils.hasText(types)) {
            return Set.of();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
