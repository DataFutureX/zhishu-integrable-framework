package com.datafuturex.assistant.kg.query;

import com.datafuturex.assistant.kg.api.KnowledgeGraphQueryPort;
import com.datafuturex.assistant.kg.api.dto.KgEdgeVO;
import com.datafuturex.assistant.kg.api.dto.KgNeighborResult;
import com.datafuturex.assistant.kg.api.dto.KgNodeVO;
import com.datafuturex.assistant.kg.api.dto.KgPathResult;
import com.datafuturex.assistant.kg.api.dto.KgSearchHit;
import com.datafuturex.assistant.kg.api.dto.KgStatsVO;
import com.datafuturex.assistant.kg.api.dto.KgSubgraphVO;
import com.datafuturex.assistant.kg.api.dto.KgTopologySummary;
import com.datafuturex.assistant.kg.config.Neo4jSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Primary
@ConditionalOnBean(Neo4jSupport.class)
@RequiredArgsConstructor
@Slf4j
public class KgQueryService implements KnowledgeGraphQueryPort {

    private static final Set<String> ALLOWED_LABELS = Set.of(
            "Project", "Terminal", "Alert",
            "InspectionPlan", "InspectionTask", "InspectionIssue", "InspectionCheckpoint",
            "Region", "Person"
    );

    /** 无 projectId 的共享节点，ACL 放行（仍通过关联边受工程范围约束） */
    private static final Set<String> SHARED_LABELS = Set.of("Region", "Person");

    private final Neo4jSupport neo4jSupport;

    @Override
    public boolean isAvailable() {
        return neo4jSupport.isAvailable();
    }

    @Override
    public KgStatsVO stats() {
        if (!neo4jSupport.isAvailable()) {
            return new KgStatsVO(false, 0, 0, Map.of(), Map.of(),
                    neo4jSupport.getErrorMessage() != null ? neo4jSupport.getErrorMessage() : "Neo4j 不可用");
        }
        try (Session session = neo4jSupport.openSession()) {
            long nodes = session.run("MATCH (n) RETURN count(n) AS c").single().get("c").asLong();
            long edges = session.run("MATCH ()-[r]->() RETURN count(r) AS c").single().get("c").asLong();
            Map<String, Long> byLabel = new LinkedHashMap<>();
            session.run("MATCH (n) RETURN labels(n)[0] AS label, count(*) AS c ORDER BY c DESC")
                    .forEachRemaining(r -> byLabel.put(r.get("label").asString(), r.get("c").asLong()));
            Map<String, Long> byType = new LinkedHashMap<>();
            session.run("MATCH ()-[r]->() RETURN type(r) AS t, count(*) AS c ORDER BY c DESC")
                    .forEachRemaining(r -> byType.put(r.get("t").asString(), r.get("c").asLong()));
            return new KgStatsVO(true, nodes, edges, byLabel, byType, "ok");
        } catch (Exception e) {
            log.warn("图谱 stats 失败: {}", e.getMessage());
            return new KgStatsVO(false, 0, 0, Map.of(), Map.of(), e.getMessage());
        }
    }

    @Override
    public KgSubgraphVO subgraph(Long projectId, int depth, Set<String> types, Set<Long> projectScope) {
        int d = Math.max(1, Math.min(depth, 3));
        Set<String> typeFilter = normalizeTypes(types);
        try (Session session = neo4jSupport.openSession()) {
            Map<String, Object> params = new HashMap<>();
            params.put("depth", d);
            StringBuilder cypher = new StringBuilder();
            if (projectId != null) {
                params.put("projectId", projectId);
                cypher.append("""
                        MATCH (p:Project {bizId: $projectId})
                        CALL {
                          WITH p
                          MATCH path = (p)-[*1..%d]-(n)
                          RETURN path
                        }
                        WITH path
                        """.formatted(d));
            } else if (projectScope != null) {
                params.put("projectIds", projectScope);
                cypher.append("""
                        MATCH (p:Project) WHERE p.bizId IN $projectIds
                        CALL {
                          WITH p
                          MATCH path = (p)-[*1..%d]-(n)
                          RETURN path
                        }
                        WITH path
                        """.formatted(d));
            } else {
                // 管理员：全部工程，不再截断前 20 个
                cypher.append("""
                        MATCH (p:Project)
                        CALL {
                          WITH p
                          MATCH path = (p)-[*1..%d]-(n)
                          RETURN path
                        }
                        WITH path
                        """.formatted(d));
            }
            int pathLimit = projectId == null ? 3000 : 800;
            params.put("pathLimit", pathLimit);
            cypher.append("RETURN path LIMIT $pathLimit");
            List<Path> paths = session.run(cypher.toString(), params).list(r -> r.get("path").asPath());
            return toSubgraph(paths, typeFilter, projectScope);
        } catch (Exception e) {
            log.warn("subgraph 查询失败: {}", e.getMessage());
            return new KgSubgraphVO(List.of(), List.of());
        }
    }

    @Override
    public List<KgSearchHit> search(String keyword, int limit, Set<Long> projectScope) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int lim = Math.max(1, Math.min(limit, 50));
        try (Session session = neo4jSupport.openSession()) {
            Map<String, Object> params = new HashMap<>();
            params.put("q", "(?i).*" + escapeRegex(keyword.trim()) + ".*");
            params.put("limit", lim);
            String scopeClause = "";
            if (projectScope != null) {
                params.put("projectIds", projectScope);
                scopeClause = " AND (n.projectId IN $projectIds OR labels(n)[0] IN ['Region','Person']) ";
            }
            String cypher = """
                    MATCH (n)
                    WHERE (n.name =~ $q OR n.code =~ $q)
                    """ + scopeClause + """
                    RETURN labels(n)[0] AS label, n.bizId AS bizId, n.name AS name,
                           n.projectId AS projectId, n.code AS code
                    LIMIT $limit
                    """;
    return session.run(cypher, params).list(r -> new KgSearchHit(
                    r.get("label").asString(),
                    r.get("bizId").isNull() ? null : r.get("bizId").asLong(),
                    r.get("name").isNull() ? null : r.get("name").asString(),
                    r.get("projectId").isNull() ? null : r.get("projectId").asLong(),
                    r.get("code").isNull() ? null : r.get("code").asString()
            ));
        } catch (Exception e) {
            log.warn("图谱搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public KgNeighborResult neighbors(String label, long bizId, int depth, Set<Long> projectScope) {
        String safeLabel = requireLabel(label);
        int d = Math.max(1, Math.min(depth, 3));
        try (Session session = neo4jSupport.openSession()) {
            Map<String, Object> params = Map.of("bizId", bizId, "depth", d);
            var centerResult = session.run(
                    "MATCH (n:" + safeLabel + " {bizId: $bizId}) RETURN n",
                    params
            );
            if (!centerResult.hasNext()) {
                return new KgNeighborResult(false,
                        "节点不存在: " + safeLabel + " bizId=" + bizId,
                        new KgSubgraphVO(List.of(), List.of()));
            }
            Node centerNode = centerResult.next().get("n").asNode();
            if (!inScope(centerNode, projectScope)) {
                return new KgNeighborResult(false, "无权访问该节点", new KgSubgraphVO(List.of(), List.of()));
            }
            List<Path> paths = session.run("""
                    MATCH (n:%s {bizId: $bizId})
                    MATCH path = (n)-[*1..%d]-(m)
                    RETURN path LIMIT 200
                    """.formatted(safeLabel, d), params).list(r -> r.get("path").asPath());
            if (paths.isEmpty()) {
                return new KgNeighborResult(true, "ok",
                        new KgSubgraphVO(List.of(toNodeVO(centerNode)), List.of()));
            }
            KgSubgraphVO g = toSubgraph(paths, Set.of(), projectScope);
            if (g.nodes().stream().noneMatch(n -> safeLabel.equals(n.label())
                    && n.bizId() != null && n.bizId() == bizId)) {
                List<KgNodeVO> nodes = new ArrayList<>(g.nodes());
                nodes.add(0, toNodeVO(centerNode));
                g = new KgSubgraphVO(nodes, g.edges());
            }
            return new KgNeighborResult(true, "ok", g);
        } catch (Exception e) {
            return new KgNeighborResult(false, "节点不存在或查询失败: " + e.getMessage(),
                    new KgSubgraphVO(List.of(), List.of()));
        }
    }

    @Override
    public KgPathResult shortestPath(String fromLabel, long fromBizId, String toLabel, long toBizId,
                                     Set<Long> projectScope) {
        String from = requireLabel(fromLabel);
        String to = requireLabel(toLabel);
        try (Session session = neo4jSupport.openSession()) {
            var result = session.run("""
                    MATCH (a:%s {bizId: $fromId}), (b:%s {bizId: $toId})
                    MATCH path = shortestPath((a)-[*..6]-(b))
                    RETURN path
                    LIMIT 1
                    """.formatted(from, to), Values.parameters("fromId", fromBizId, "toId", toBizId));
            if (!result.hasNext()) {
                return new KgPathResult(false, "未找到路径", List.of(), List.of());
            }
            Path path = result.next().get("path").asPath();
            KgSubgraphVO g = toSubgraph(List.of(path), Set.of(), projectScope);
            if (g.nodes().isEmpty()) {
                return new KgPathResult(false, "无权查看该路径", List.of(), List.of());
            }
            return new KgPathResult(true, "ok", g.nodes(), g.edges());
        } catch (Exception e) {
            return new KgPathResult(false, "路径查询失败: " + e.getMessage(), List.of(), List.of());
        }
    }

    @Override
    public KgTopologySummary projectTopology(long projectId, Set<Long> projectScope) {
        if (projectScope != null && !projectScope.contains(projectId)) {
            return new KgTopologySummary(false, "无权访问工程: " + projectId, projectId, null, 0, 0, 0, 0, 0);
        }
        try (Session session = neo4jSupport.openSession()) {
            Record r = session.run("""
                    MATCH (p:Project {bizId: $projectId})
                    OPTIONAL MATCH (p)-[:CONTAINS]->(t:Terminal)
                    OPTIONAL MATCH (t)-[:HAS_ALERT]->(a:Alert)
                    OPTIONAL MATCH (p)-[:HAS_PLAN]->(plan:InspectionPlan)
                    OPTIONAL MATCH (plan)-[:GENERATES]->(task:InspectionTask)
                    OPTIONAL MATCH (task)-[:HAS_ISSUE]->(issue:InspectionIssue)
                    WHERE issue IS NULL OR issue.status <> 'CLOSED'
                    RETURN p.name AS name,
                           count(DISTINCT t) AS terminals,
                           count(DISTINCT a) AS alerts,
                           count(DISTINCT plan) AS plans,
                           count(DISTINCT task) AS tasks,
                           count(DISTINCT issue) AS issues
                    """, Values.parameters("projectId", projectId)).single();
            return new KgTopologySummary(
                    true,
                    "ok",
                    projectId,
                    r.get("name").isNull() ? null : r.get("name").asString(),
                    r.get("terminals").asLong(),
                    r.get("alerts").asLong(),
                    r.get("plans").asLong(),
                    r.get("tasks").asLong(),
                    r.get("issues").asLong()
            );
        } catch (Exception e) {
            return new KgTopologySummary(false, "工程不存在或查询失败: " + e.getMessage(),
                    projectId, null, 0, 0, 0, 0, 0);
        }
    }

    @Override
    public KgNeighborResult alertImpact(long alertBizId, int depth, Set<Long> projectScope) {
        int d = Math.max(1, Math.min(depth, 3));
        try (Session session = neo4jSupport.openSession()) {
            Map<String, Object> params = Map.of("bizId", alertBizId);
            var centerResult = session.run(
                    "MATCH (a:Alert {bizId: $bizId}) RETURN a", params);
            if (!centerResult.hasNext()) {
                return new KgNeighborResult(false, "告警节点不存在", new KgSubgraphVO(List.of(), List.of()));
            }
            Node alertNode = centerResult.next().get("a").asNode();
            if (!inScope(alertNode, projectScope)) {
                return new KgNeighborResult(false, "无权访问该告警", new KgSubgraphVO(List.of(), List.of()));
            }
            // 影响面：从告警出发多跳展开同站/同工程关联实体
            List<Path> paths = session.run("""
                    MATCH (a:Alert {bizId: $bizId})
                    MATCH path = (a)-[*1..%d]-(m)
                    WHERE m:Terminal OR m:Project OR m:Alert OR m:InspectionIssue
                       OR m:InspectionCheckpoint OR m:InspectionTask OR m:InspectionPlan
                       OR m:Person OR m:Region
                    RETURN path
                    LIMIT 300
                    """.formatted(d), params).list(r -> r.get("path").asPath());
            if (paths.isEmpty()) {
                KgNodeVO node = toNodeVO(alertNode);
                return new KgNeighborResult(true, "仅找到告警节点，无关联拓扑",
                        new KgSubgraphVO(List.of(node), List.of()));
            }
            KgSubgraphVO g = toSubgraph(paths, Set.of(), projectScope);
            if (g.nodes().stream().noneMatch(n -> "Alert".equals(n.label())
                    && n.bizId() != null && n.bizId() == alertBizId)) {
                List<KgNodeVO> nodes = new ArrayList<>(g.nodes());
                nodes.add(0, toNodeVO(alertNode));
                g = new KgSubgraphVO(nodes, g.edges());
            }
            return new KgNeighborResult(true, "ok", g);
        } catch (Exception e) {
            return new KgNeighborResult(false, "影响面查询失败: " + e.getMessage(),
                    new KgSubgraphVO(List.of(), List.of()));
        }
    }

    private KgSubgraphVO toSubgraph(Collection<Path> paths, Set<String> typeFilter, Set<Long> projectScope) {
        Map<String, KgNodeVO> nodes = new LinkedHashMap<>();
        Map<String, KgEdgeVO> edges = new LinkedHashMap<>();
        for (Path path : paths) {
            Map<String, Node> byElementId = new LinkedHashMap<>();
            for (Node node : path.nodes()) {
                byElementId.put(node.elementId(), node);
            }
            for (Node node : path.nodes()) {
                if (!inScope(node, projectScope)) {
                    continue;
                }
                String label = primaryLabel(node);
                if (!typeFilter.isEmpty() && !typeFilter.contains(label)) {
                    continue;
                }
                nodes.putIfAbsent(nodeId(node), toNodeVO(node));
            }
            for (Relationship rel : path.relationships()) {
                Node start = byElementId.get(rel.startNodeElementId());
                Node end = byElementId.get(rel.endNodeElementId());
                if (start == null || end == null) {
                    continue;
                }
                if (!inScope(start, projectScope) || !inScope(end, projectScope)) {
                    continue;
                }
                String startLabel = primaryLabel(start);
                String endLabel = primaryLabel(end);
                if (!typeFilter.isEmpty()
                        && (!typeFilter.contains(startLabel) || !typeFilter.contains(endLabel))) {
                    continue;
                }
                nodes.putIfAbsent(nodeId(start), toNodeVO(start));
                nodes.putIfAbsent(nodeId(end), toNodeVO(end));
                Long projectId = rel.containsKey("projectId") && !rel.get("projectId").isNull()
                        ? rel.get("projectId").asLong() : null;
                String eid = rel.type() + ":" + nodeId(start) + "->" + nodeId(end);
                edges.putIfAbsent(eid, new KgEdgeVO(eid, rel.type(), nodeId(start), nodeId(end), projectId));
            }
        }
        return new KgSubgraphVO(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()));
    }

    private static KgNodeVO toNodeVO(Node node) {
        String label = primaryLabel(node);
        Long bizId = node.containsKey("bizId") ? node.get("bizId").asLong() : null;
        String name = node.containsKey("name") && !node.get("name").isNull() ? node.get("name").asString() : null;
        Long projectId = node.containsKey("projectId") && !node.get("projectId").isNull()
                ? node.get("projectId").asLong() : null;
        Map<String, Object> props = new LinkedHashMap<>();
        for (String key : node.keys()) {
            Value v = node.get(key);
            Object obj = v.asObject();
            // Map 内 Long 也转字符串，避免前端二次精度丢失
            if (obj instanceof Long l) {
                props.put(key, Long.toString(l));
            } else if (obj instanceof Number n && !(obj instanceof Double) && !(obj instanceof Float)) {
                long lv = n.longValue();
                if (Math.abs(lv) > 9_007_199_254_740_991L) {
                    props.put(key, Long.toString(lv));
                } else {
                    props.put(key, obj);
                }
            } else {
                props.put(key, obj);
            }
        }
        return new KgNodeVO(nodeId(node), label, bizId, name, projectId, props);
    }

    private static String nodeId(Node node) {
        String label = primaryLabel(node);
        long bizId = node.containsKey("bizId") ? node.get("bizId").asLong() : 0L;
        return label + ":" + bizId;
    }

    private static String primaryLabel(Node node) {
        var it = node.labels().iterator();
        return it.hasNext() ? it.next() : "Node";
    }

    private static boolean inScope(Node node, Set<Long> projectScope) {
        if (projectScope == null) {
            return true;
        }
        if (SHARED_LABELS.contains(primaryLabel(node))) {
            return true;
        }
        if (!node.containsKey("projectId") || node.get("projectId").isNull()) {
            return false;
        }
        return projectScope.contains(node.get("projectId").asLong());
    }

    private static Set<String> normalizeTypes(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String t : types) {
            if (StringUtils.hasText(t) && ALLOWED_LABELS.contains(t.trim())) {
                out.add(t.trim());
            }
        }
        return out;
    }

    private static String requireLabel(String label) {
        if (!StringUtils.hasText(label) || !ALLOWED_LABELS.contains(label.trim())) {
            throw new IllegalArgumentException("不支持的节点标签: " + label);
        }
        return label.trim();
    }

    private static String escapeRegex(String raw) {
        return raw.replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("|", "\\|")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }
}
