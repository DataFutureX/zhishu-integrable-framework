package cn.datafuturex.zhishu.ai.kg.query;

import cn.datafuturex.zhishu.ai.kg.api.KnowledgeGraphQueryPort;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgNeighborResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgPathResult;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSearchHit;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgStatsVO;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgSubgraphVO;
import cn.datafuturex.zhishu.ai.kg.api.dto.KgTopologySummary;
import cn.datafuturex.zhishu.ai.kg.config.Neo4jSupport;
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
