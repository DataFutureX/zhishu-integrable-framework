package com.datafuturex.assistant.kg.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

/**
 * Neo4j 运行时句柄：Driver 创建失败时仍可注入，{@link #isAvailable()} 为 false。
 */
@Getter
@Slf4j
public final class Neo4jSupport implements AutoCloseable {

    private final Driver driver;
    private final SessionConfig sessionConfig;
    private final String errorMessage;

    private Neo4jSupport(Driver driver, SessionConfig sessionConfig, String errorMessage) {
        this.driver = driver;
        this.sessionConfig = sessionConfig;
        this.errorMessage = errorMessage;
    }

    public static Neo4jSupport connect(KgProperties properties) {
        KgProperties.Neo4j neo = properties.getNeo4j();
        SessionConfig sessionConfig = SessionConfig.builder()
                .withDatabase(neo.getDatabase())
                .build();
        try {
            log.info("初始化 Neo4j Driver uri={} database={}", neo.getUri(), neo.getDatabase());
            Driver driver = GraphDatabase.driver(
                    neo.getUri(),
                    AuthTokens.basic(neo.getUsername(), neo.getPassword())
            );
            try (Session session = driver.session(sessionConfig)) {
                session.run("RETURN 1 AS ok").consume();
            }
            ensureConstraints(driver, sessionConfig);
            return new Neo4jSupport(driver, sessionConfig, null);
        } catch (Throwable t) {
            log.error("Neo4j Driver 初始化失败，知识图谱降级（不影响主链路）: {}", t.toString());
            return new Neo4jSupport(null, sessionConfig, t.toString());
        }
    }

    public boolean isAvailable() {
        return driver != null;
    }

    public Session openSession() {
        if (driver == null) {
            throw new IllegalStateException("Neo4j 不可用: " + errorMessage);
        }
        return driver.session(sessionConfig);
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    private static void ensureConstraints(Driver driver, SessionConfig sessionConfig) {
        String[] labels = {
                "Project", "Terminal", "Alert",
                "InspectionPlan", "InspectionTask", "InspectionIssue", "InspectionCheckpoint",
                "Region", "Person"
        };
        try (Session session = driver.session(sessionConfig)) {
            for (String label : labels) {
                String cypher = "CREATE CONSTRAINT " + label.toLowerCase() + "_biz_id IF NOT EXISTS "
                        + "FOR (n:" + label + ") REQUIRE n.bizId IS UNIQUE";
                session.run(cypher).consume();
            }
            log.info("Neo4j 唯一约束已检查/创建（{} 个标签）", labels.length);
        } catch (Exception e) {
            log.warn("Neo4j 约束初始化失败（图功能可能不可用）: {}", e.getMessage());
        }
    }
}
