package com.datafuturex.assistant.kg.api;

import com.datafuturex.assistant.kg.api.dto.KgNeighborResult;
import com.datafuturex.assistant.kg.api.dto.KgPathResult;
import com.datafuturex.assistant.kg.api.dto.KgSearchHit;
import com.datafuturex.assistant.kg.api.dto.KgStatsVO;
import com.datafuturex.assistant.kg.api.dto.KgSubgraphVO;
import com.datafuturex.assistant.kg.api.dto.KgTopologySummary;

import java.util.List;
import java.util.Set;

/**
 * 知识图谱只读查询端口（供 BizTools / Agent 调用）。
 */
public interface KnowledgeGraphQueryPort {

    boolean isAvailable();

    KgStatsVO stats();

    KgSubgraphVO subgraph(Long projectId, int depth, Set<String> types, Set<Long> projectScope);

    List<KgSearchHit> search(String keyword, int limit, Set<Long> projectScope);

    KgNeighborResult neighbors(String label, long bizId, int depth, Set<Long> projectScope);

    KgPathResult shortestPath(String fromLabel, long fromBizId, String toLabel, long toBizId, Set<Long> projectScope);

    KgTopologySummary projectTopology(long projectId, Set<Long> projectScope);

    /**
     * 告警影响面：告警 → 终端 → 工程，并展开同站其它告警 / 相关巡检异常 / 检查点。
     */
    KgNeighborResult alertImpact(long alertBizId, int depth, Set<Long> projectScope);
}
