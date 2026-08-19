package com.datafuturex.assistant.kg.query;

import com.datafuturex.assistant.kg.api.KnowledgeGraphQueryPort;
import com.datafuturex.assistant.kg.api.dto.KgNeighborResult;
import com.datafuturex.assistant.kg.api.dto.KgPathResult;
import com.datafuturex.assistant.kg.api.dto.KgSearchHit;
import com.datafuturex.assistant.kg.api.dto.KgStatsVO;
import com.datafuturex.assistant.kg.api.dto.KgSubgraphVO;
import com.datafuturex.assistant.kg.api.dto.KgTopologySummary;
import com.datafuturex.assistant.kg.config.Neo4jSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Neo4j 未启用时的降级实现，保证 Tool / Controller 可注入。
 */
@Service
@ConditionalOnMissingBean(Neo4jSupport.class)
public class DisabledKgQueryService implements KnowledgeGraphQueryPort {

    private static final String MSG = "知识图谱未启用或 Neo4j 不可用";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public KgStatsVO stats() {
        return new KgStatsVO(false, 0, 0, Map.of(), Map.of(), MSG);
    }

    @Override
    public KgSubgraphVO subgraph(Long projectId, int depth, Set<String> types, Set<Long> projectScope) {
        return new KgSubgraphVO(List.of(), List.of());
    }

    @Override
    public List<KgSearchHit> search(String keyword, int limit, Set<Long> projectScope) {
        return List.of();
    }

    @Override
    public KgNeighborResult neighbors(String label, long bizId, int depth, Set<Long> projectScope) {
        return new KgNeighborResult(false, MSG, new KgSubgraphVO(List.of(), List.of()));
    }

    @Override
    public KgPathResult shortestPath(String fromLabel, long fromBizId, String toLabel, long toBizId,
                                     Set<Long> projectScope) {
        return new KgPathResult(false, MSG, List.of(), List.of());
    }

    @Override
    public KgTopologySummary projectTopology(long projectId, Set<Long> projectScope) {
        return new KgTopologySummary(false, MSG, projectId, null, 0, 0, 0, 0, 0);
    }

    @Override
    public KgNeighborResult alertImpact(long alertBizId, int depth, Set<Long> projectScope) {
        return new KgNeighborResult(false, MSG, new KgSubgraphVO(List.of(), List.of()));
    }
}
