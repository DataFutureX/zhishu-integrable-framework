package com.datafuturex.assistant.kg.sync;

import com.datafuturex.assistant.kg.api.dto.KgSyncResult;
import com.datafuturex.assistant.kg.api.dto.KgUpsertRequest;
import com.datafuturex.assistant.kg.config.Neo4jSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "wanxiang.kg", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KgPushIngestService {

    private final ObjectProvider<Neo4jSupport> neo4jSupportProvider;

    public KgSyncResult ingest(KgUpsertRequest request) {
        LocalDateTime started = LocalDateTime.now();
        Map<String, Integer> upserted = new LinkedHashMap<>();
        Neo4jSupport support = neo4jSupportProvider.getIfAvailable();
        if (support == null || !support.isAvailable()) {
            String msg = support != null && support.getErrorMessage() != null
                    ? support.getErrorMessage()
                    : "Neo4j Driver 未就绪";
            return new KgSyncResult(false, Boolean.TRUE.equals(request.full()), msg, started, LocalDateTime.now(), Map.of(), 0);
        }
        try (Session session = support.openSession()) {
            upserted.put("Project", upsertProjects(session, request.projects()));
            upserted.put("Terminal", upsertTerminals(session, request.terminals()));
            upserted.put("Alert", upsertAlerts(session, request.alerts()));
        }
        log.info("图谱推送入库完成 upserted={}", upserted);
        return new KgSyncResult(true, Boolean.TRUE.equals(request.full()), "推送成功", started, LocalDateTime.now(), upserted, 0);
    }

    private int upsertProjects(Session session, List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return 0;
        }
        session.run("""
                UNWIND $rows AS row
                MERGE (p:Project {bizId: row.bizId})
                SET p.name = row.name, p.code = row.code, p.projectType = row.projectType,
                    p.status = row.status, p.region = row.region, p.projectId = row.projectId,
                    p.longitude = row.longitude, p.latitude = row.latitude,
                    p.contactPerson = row.contactPerson
                """, Values.parameters("rows", rows)).consume();
        return rows.size();
    }

    private int upsertTerminals(Session session, List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return 0;
        }
        session.run("""
                UNWIND $rows AS row
                MERGE (t:Terminal {bizId: row.bizId})
                SET t.name = row.name, t.code = row.code, t.onlineStatus = row.onlineStatus,
                    t.protocolType = row.protocolType, t.projectId = row.projectId,
                    t.longitude = row.longitude, t.latitude = row.latitude
                """, Values.parameters("rows", rows)).consume();
        List<Map<String, Object>> rels = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.get("projectId") != null && row.get("bizId") != null) {
                rels.add(Map.of("projectId", row.get("projectId"), "terminalId", row.get("bizId")));
            }
        }
        if (!rels.isEmpty()) {
            session.run("""
                    UNWIND $rows AS row
                    MATCH (p:Project {bizId: row.projectId})
                    MATCH (t:Terminal {bizId: row.terminalId})
                    MERGE (p)-[r:CONTAINS]->(t)
                    SET r.projectId = row.projectId
                    """, Values.parameters("rows", rels)).consume();
        }
        return rows.size();
    }

    private int upsertAlerts(Session session, List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return 0;
        }
        session.run("""
                UNWIND $rows AS row
                MERGE (a:Alert {bizId: row.bizId})
                SET a.name = row.name, a.code = row.code, a.alertLevel = row.alertLevel,
                    a.status = row.status, a.projectId = row.projectId, a.terminalId = row.terminalId
                """, Values.parameters("rows", rows)).consume();
        List<Map<String, Object>> rels = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.get("terminalId") != null && row.get("bizId") != null) {
                rels.add(Map.of(
                        "terminalId", row.get("terminalId"),
                        "alertId", row.get("bizId"),
                        "projectId", row.get("projectId") == null ? -1L : row.get("projectId")));
            }
        }
        if (!rels.isEmpty()) {
            session.run("""
                    UNWIND $rows AS row
                    MATCH (t:Terminal {bizId: row.terminalId})
                    MATCH (a:Alert {bizId: row.alertId})
                    MERGE (t)-[r:HAS_ALERT]->(a)
                    SET r.projectId = row.projectId
                    """, Values.parameters("rows", rels)).consume();
        }
        return rows.size();
    }
}
