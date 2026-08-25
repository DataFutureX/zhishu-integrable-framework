package cn.datafuturex.zhishu.biz.systemmonitor.service.impl;

import cn.datafuturex.zhishu.biz.systemmonitor.config.TableShardingProperties;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ApplicationMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.BusinessMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ComponentHealthDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.DatabaseMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.JvmMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.OsMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ShardingMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ShardingStrategyMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ShardingTableMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.StorageMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.SystemHealthDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.SystemStatusDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.WebServerMetricsDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.service.SystemMonitorService;
import cn.datafuturex.zhishu.modules.mapper.UserMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统监控服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TableShardingProperties.class)
public class SystemMonitorServiceImpl implements SystemMonitorService {

    private static final double MB = 1024.0 * 1024.0;
    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    private final DataSource dataSource;
    private final UserMapper userMapper;
    private final Environment environment;
    private final TableShardingProperties tableShardingProperties;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.application.name:zhishu}")
    private String applicationName;

    @Value("${yunqi.upload.path:uploads}")
    private String uploadPath;

    @Value("${logging.file.name:logs/zhishu-backend.log}")
    private String logFilePath;

    private final long applicationStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

    @Override
    public SystemStatusDTO getSystemStatus() {
        LocalDateTime now = LocalDateTime.now();
        List<ComponentHealthDTO> components = buildComponentHealthList();

        return new SystemStatusDTO(
                resolveOverallStatus(components),
                now,
                buildApplicationMetrics(),
                buildJvmMetrics(),
                buildOsMetrics(),
                buildDatabaseMetrics(),
                buildWebServerMetrics(),
                buildBusinessMetrics(),
                buildShardingMetrics(),
                buildStorageMetrics(),
                components
        );
    }

    @Override
    public SystemHealthDTO getSystemHealth() {
        List<ComponentHealthDTO> components = buildComponentHealthList();
        return new SystemHealthDTO(resolveOverallStatus(components), LocalDateTime.now(), components);
    }

    private List<ComponentHealthDTO> buildComponentHealthList() {
        List<ComponentHealthDTO> components = new ArrayList<>();
        components.add(checkDatabaseHealth());
        components.add(checkWebServerHealth());
        components.add(checkStorageHealth());
        components.add(checkJvmHealth());
        components.add(checkShardingHealth());
        return components;
    }

    private String resolveOverallStatus(List<ComponentHealthDTO> components) {
        boolean hasDown = components.stream().anyMatch(c -> ComponentHealthDTO.DOWN.equals(c.status()));
        if (hasDown) {
            return ComponentHealthDTO.DOWN;
        }
        boolean hasDegraded = components.stream().anyMatch(c -> ComponentHealthDTO.DEGRADED.equals(c.status()));
        if (hasDegraded) {
            return ComponentHealthDTO.DEGRADED;
        }
        return ComponentHealthDTO.UP;
    }

    private ApplicationMetricsDTO buildApplicationMetrics() {
        long uptimeMillis = System.currentTimeMillis() - applicationStartTime;
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join(",", profiles);

        return new ApplicationMetricsDTO(
                applicationName,
                environment.getProperty("project.version", "1.0.0"),
                System.getProperty("java.version"),
                SpringBootVersion.getVersion(),
                profile,
                uptimeMillis,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(applicationStartTime), ZoneId.systemDefault())
        );
    }

    private JvmMetricsDTO buildJvmMetrics() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        long heapCommitted = memory.getHeapMemoryUsage().getCommitted();
        long nonHeapUsed = memory.getNonHeapMemoryUsage().getUsed();
        long nonHeapCommitted = memory.getNonHeapMemoryUsage().getCommitted();

        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count >= 0) {
                gcCount += count;
            }
            if (time >= 0) {
                gcTime += time;
            }
        }

        double heapUsagePercent = heapMax > 0 ? round2(heapUsed * 100.0 / heapMax) : 0.0;

        return new JvmMetricsDTO(
                round2(heapUsed / MB),
                round2(heapMax / MB),
                round2(heapCommitted / MB),
                heapUsagePercent,
                round2(nonHeapUsed / MB),
                round2(nonHeapCommitted / MB),
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                threads.getDaemonThreadCount(),
                threads.getTotalStartedThreadCount(),
                gcCount,
                gcTime
        );
    }

    private OsMetricsDTO buildOsMetrics() {
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        Double systemCpu = toPercent(osBean.getCpuLoad());
        Double processCpu = toPercent(osBean.getProcessCpuLoad());
        Double totalMemoryMb = osBean.getTotalMemorySize() > 0 ? round2(osBean.getTotalMemorySize() / MB) : null;
        Double freeMemoryMb = osBean.getFreeMemorySize() > 0 ? round2(osBean.getFreeMemorySize() / MB) : null;
        Double memoryUsagePercent = null;
        if (totalMemoryMb != null && freeMemoryMb != null && totalMemoryMb > 0) {
            memoryUsagePercent = round2((totalMemoryMb - freeMemoryMb) * 100.0 / totalMemoryMb);
        }

        return new OsMetricsDTO(
                osBean.getName(),
                osBean.getArch(),
                osBean.getVersion(),
                osBean.getAvailableProcessors(),
                systemCpu,
                processCpu,
                totalMemoryMb,
                freeMemoryMb,
                memoryUsagePercent
        );
    }

    private DatabaseMetricsDTO buildDatabaseMetrics() {
        ComponentHealthDTO health = checkDatabaseHealth();
        String databaseProduct = null;
        String databaseVersion = null;
        String poolName = null;
        Integer activeConnections = 0;
        Integer idleConnections = 0;
        Integer totalConnections = 0;
        Integer maxConnections = 0;
        Integer threadsAwaiting = 0;
        Long connectionTimeoutMs = null;

        if (dataSource instanceof HikariDataSource hikariDataSource) {
            poolName = hikariDataSource.getPoolName();
            connectionTimeoutMs = hikariDataSource.getConnectionTimeout();
            maxConnections = hikariDataSource.getMaximumPoolSize();
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                activeConnections = pool.getActiveConnections();
                idleConnections = pool.getIdleConnections();
                totalConnections = pool.getTotalConnections();
                threadsAwaiting = pool.getThreadsAwaitingConnection();
            }
        }

        if (ComponentHealthDTO.UP.equals(health.status()) || ComponentHealthDTO.DEGRADED.equals(health.status())) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                databaseProduct = metaData.getDatabaseProductName();
                databaseVersion = metaData.getDatabaseProductVersion();
            } catch (Exception e) {
                log.debug("读取数据库元信息失败", e);
            }
        }

        return new DatabaseMetricsDTO(
                health.status(),
                databaseProduct,
                databaseVersion,
                poolName,
                activeConnections,
                idleConnections,
                totalConnections,
                maxConnections,
                threadsAwaiting,
                connectionTimeoutMs,
                health.responseTimeMs()
        );
    }

    private WebServerMetricsDTO buildWebServerMetrics() {
        ComponentHealthDTO health = checkWebServerHealth();
        return new WebServerMetricsDTO(
                health.status(),
                serverPort,
                "Tomcat"
        );
    }

    private BusinessMetricsDTO buildBusinessMetrics() {
        try {
            return new BusinessMetricsDTO(userMapper.selectCount(null));
        } catch (Exception e) {
            log.warn("采集业务指标失败", e);
            return new BusinessMetricsDTO(0L);
        }
    }

    private ShardingMetricsDTO buildShardingMetrics() {
        if (!tableShardingProperties.isEnabled()) {
            return new ShardingMetricsDTO(
                    ComponentHealthDTO.UNKNOWN, false, 0, 0, 0, 0, 0L, List.of());
        }
        List<TableShardingProperties.Strategy> strategies = tableShardingProperties.getStrategies();
        if (strategies == null || strategies.isEmpty()) {
            return new ShardingMetricsDTO(
                    ComponentHealthDTO.UNKNOWN, true, 0, 0, 0, 0, 0L, List.of());
        }

        List<ShardingStrategyMetricsDTO> strategyMetrics = new ArrayList<>();
        int existingTotal = 0;
        int expectedTotal = 0;
        int missingTotal = 0;
        long rowTotal = 0L;

        for (TableShardingProperties.Strategy strategy : strategies) {
            ShardingStrategyMetricsDTO metrics = buildStrategyMetrics(strategy);
            strategyMetrics.add(metrics);
            existingTotal += metrics.existingTableCount();
            expectedTotal += metrics.expectedTableCount();
            missingTotal += metrics.missingTableCount();
            rowTotal += metrics.approximateRowTotal() != null ? metrics.approximateRowTotal() : 0L;
        }

        String status = resolveShardingOverallStatus(strategyMetrics);
        return new ShardingMetricsDTO(
                status,
                true,
                strategyMetrics.size(),
                existingTotal,
                expectedTotal,
                missingTotal,
                rowTotal,
                strategyMetrics
        );
    }

    private ShardingStrategyMetricsDTO buildStrategyMetrics(TableShardingProperties.Strategy strategy) {
        String prefix = strategy.getTablePrefix() != null ? strategy.getTablePrefix() : "";
        int behind = Math.max(strategy.getMonthsBehind(), 0);
        int ahead = Math.max(strategy.getMonthsAhead(), 0);
        YearMonth current = YearMonth.from(LocalDate.now());

        Map<String, TableMeta> existing = loadExistingTables(prefix);
        List<ShardingTableMetricsDTO> tables = new ArrayList<>();
        List<String> missingMonths = new ArrayList<>();
        long rowTotal = 0L;
        long dataLength = 0L;
        int existingCount = 0;
        int expectedCount = 0;

        for (int i = -behind; i <= ahead; i++) {
            YearMonth month = current.plusMonths(i);
            String suffix = month.format(MONTH_SUFFIX);
            String tableName = prefix + suffix;
            expectedCount++;
            TableMeta meta = existing.get(tableName);
            if (meta == null) {
                missingMonths.add(suffix);
                tables.add(new ShardingTableMetricsDTO(tableName, suffix, false, 0L, 0L, null));
            } else {
                existingCount++;
                rowTotal += meta.tableRows();
                dataLength += meta.dataLength();
                tables.add(new ShardingTableMetricsDTO(
                        tableName, suffix, true, meta.tableRows(), meta.dataLength(), meta.createTime()));
            }
        }

        int missingCount = missingMonths.size();
        String status;
        if (expectedCount == 0) {
            status = ComponentHealthDTO.UNKNOWN;
        } else if (missingCount == 0) {
            status = ComponentHealthDTO.UP;
        } else if (existingCount == 0) {
            status = ComponentHealthDTO.DOWN;
        } else {
            status = ComponentHealthDTO.DEGRADED;
        }

        return new ShardingStrategyMetricsDTO(
                strategy.getName(),
                strategy.getDisplayName(),
                prefix,
                status,
                strategy.isAutoCreate(),
                behind,
                ahead,
                existingCount,
                expectedCount,
                missingCount,
                rowTotal,
                dataLength,
                missingMonths,
                tables
        );
    }

    private Map<String, TableMeta> loadExistingTables(String prefix) {
        Map<String, TableMeta> result = new HashMap<>();
        if (!StringUtils.hasText(prefix)) {
            return result;
        }
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    """
                            SELECT c.relname AS "TABLE_NAME",
                                   COALESCE(s.n_live_tup, 0) AS "TABLE_ROWS",
                                   pg_total_relation_size(c.oid) AS "DATA_LENGTH",
                                   NULL AS "CREATE_TIME"
                            FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                            WHERE n.nspname = current_schema()
                              AND c.relkind = 'r'
                              AND c.relname LIKE ?
                            ORDER BY c.relname
                            """,
                    prefix.toLowerCase() + "%");
            for (Map<String, Object> row : rows) {
                String name = String.valueOf(row.get("TABLE_NAME"));
                long tableRows = toLong(row.get("TABLE_ROWS"));
                long dataLen = toLong(row.get("DATA_LENGTH"));
                LocalDateTime createTime = toLocalDateTime(row.get("CREATE_TIME"));
                result.put(name, new TableMeta(tableRows, dataLen, createTime));
            }
        } catch (Exception e) {
            log.warn("查询分表元数据失败: prefix={}", prefix, e);
        }
        return result;
    }

    private String resolveShardingOverallStatus(List<ShardingStrategyMetricsDTO> strategies) {
        if (strategies.isEmpty()) {
            return ComponentHealthDTO.UNKNOWN;
        }
        boolean anyDown = strategies.stream().anyMatch(s -> ComponentHealthDTO.DOWN.equals(s.status()));
        if (anyDown) {
            return ComponentHealthDTO.DOWN;
        }
        boolean anyDegraded = strategies.stream().anyMatch(s -> ComponentHealthDTO.DEGRADED.equals(s.status()));
        if (anyDegraded) {
            return ComponentHealthDTO.DEGRADED;
        }
        boolean allUp = strategies.stream().allMatch(s -> ComponentHealthDTO.UP.equals(s.status()));
        return allUp ? ComponentHealthDTO.UP : ComponentHealthDTO.UNKNOWN;
    }

    private ComponentHealthDTO checkShardingHealth() {
        ShardingMetricsDTO metrics = buildShardingMetrics();
        if (!Boolean.TRUE.equals(metrics.enabled())) {
            return new ComponentHealthDTO("sharding", ComponentHealthDTO.UNKNOWN, "分表监控未启用", null);
        }
        if (metrics.strategyCount() == null || metrics.strategyCount() == 0) {
            return new ComponentHealthDTO("sharding", ComponentHealthDTO.UNKNOWN, "未配置分表策略", null);
        }
        if (ComponentHealthDTO.UP.equals(metrics.status())) {
            return new ComponentHealthDTO("sharding", ComponentHealthDTO.UP,
                    String.format("分表正常：策略 %d，物理表 %d/%d",
                            metrics.strategyCount(), metrics.existingTableCount(), metrics.expectedTableCount()),
                    null);
        }
        if (ComponentHealthDTO.DEGRADED.equals(metrics.status())) {
            return new ComponentHealthDTO("sharding", ComponentHealthDTO.DEGRADED,
                    String.format("分表缺失 %d 张（期望 %d）",
                            metrics.missingTableCount(), metrics.expectedTableCount()),
                    null);
        }
        if (ComponentHealthDTO.DOWN.equals(metrics.status())) {
            return new ComponentHealthDTO("sharding", ComponentHealthDTO.DOWN,
                    "分表窗口内物理表全部缺失", null);
        }
        return new ComponentHealthDTO("sharding", ComponentHealthDTO.UNKNOWN, "分表状态未知", null);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private record TableMeta(long tableRows, long dataLength, LocalDateTime createTime) {
    }

    private StorageMetricsDTO buildStorageMetrics() {
        File root = new File(".");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        double totalMb = round2(total / MB);
        double freeMb = round2(free / MB);
        double usagePercent = total > 0 ? round2((total - free) * 100.0 / total) : 0.0;

        return new StorageMetricsDTO(
                totalMb,
                freeMb,
                usagePercent,
                round2(getFileSizeMb(logFilePath)),
                round2(getDirectorySizeMb(uploadPath))
        );
    }

    private ComponentHealthDTO checkDatabaseHealth() {
        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > 1000) {
                    return new ComponentHealthDTO("database", ComponentHealthDTO.DEGRADED,
                            "数据库响应较慢", elapsed);
                }
                return new ComponentHealthDTO("database", ComponentHealthDTO.UP, "连接正常", elapsed);
            }
            return new ComponentHealthDTO("database", ComponentHealthDTO.DOWN, "连接验证失败", null);
        } catch (Exception e) {
            log.warn("数据库健康检查失败", e);
            return new ComponentHealthDTO("database", ComponentHealthDTO.DOWN, e.getMessage(), null);
        }
    }

    private ComponentHealthDTO checkWebServerHealth() {
        return new ComponentHealthDTO("webServer", ComponentHealthDTO.UP,
                "HTTP API 服务运行中，端口 " + serverPort, null);
    }

    private ComponentHealthDTO checkStorageHealth() {
        File root = new File(".");
        long total = root.getTotalSpace();
        long free = root.getUsableSpace();
        if (total <= 0) {
            return new ComponentHealthDTO("storage", ComponentHealthDTO.UNKNOWN, "无法读取磁盘信息", null);
        }
        double usagePercent = (total - free) * 100.0 / total;
        if (usagePercent >= 95) {
            return new ComponentHealthDTO("storage", ComponentHealthDTO.DOWN,
                    String.format("磁盘使用率过高: %.1f%%", usagePercent), null);
        }
        if (usagePercent >= 85) {
            return new ComponentHealthDTO("storage", ComponentHealthDTO.DEGRADED,
                    String.format("磁盘使用率偏高: %.1f%%", usagePercent), null);
        }
        return new ComponentHealthDTO("storage", ComponentHealthDTO.UP,
                String.format("磁盘使用率: %.1f%%", usagePercent), null);
    }

    private ComponentHealthDTO checkJvmHealth() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        if (heapMax <= 0) {
            return new ComponentHealthDTO("jvm", ComponentHealthDTO.UP, "JVM 运行正常", null);
        }
        double usagePercent = heapUsed * 100.0 / heapMax;
        if (usagePercent >= 95) {
            return new ComponentHealthDTO("jvm", ComponentHealthDTO.DEGRADED,
                    String.format("堆内存使用率过高: %.1f%%", usagePercent), null);
        }
        return new ComponentHealthDTO("jvm", ComponentHealthDTO.UP,
                String.format("堆内存使用率: %.1f%%", usagePercent), null);
    }

    private double getFileSizeMb(String path) {
        File file = new File(path);
        return file.exists() ? file.length() / MB : 0.0;
    }

    private double getDirectorySizeMb(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0.0;
        }
        return calculateDirectorySize(dir) / MB;
    }

    private long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isFile()) {
                size += file.length();
            } else if (file.isDirectory()) {
                size += calculateDirectorySize(file);
            }
        }
        return size;
    }

    private Double toPercent(double value) {
        if (value < 0) {
            return null;
        }
        return round2(value * 100.0);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
