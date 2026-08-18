package cn.datafuturex.yunqi.biz.systemmonitor.dto;

/**
 * 数据库与连接池指标（HikariCP + MySQL）
 */
public record DatabaseMetricsDTO(
        String status,
        String databaseProduct,
        String databaseVersion,
        String poolName,
        Integer activeConnections,
        Integer idleConnections,
        Integer totalConnections,
        Integer maxConnections,
        Integer threadsAwaitingConnection,
        Long connectionTimeoutMs,
        Long validationTimeMs
) {
}
