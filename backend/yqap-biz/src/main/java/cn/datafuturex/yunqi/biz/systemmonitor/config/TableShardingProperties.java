package cn.datafuturex.yunqi.biz.systemmonitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 月分表监控与预建配置
 */
@Data
@ConfigurationProperties(prefix = "yunqi.table-sharding")
public class TableShardingProperties {

    /**
     * 是否启用分表监控（及可选预建）
     */
    private boolean enabled = true;

    private List<Strategy> strategies = new ArrayList<>();

    @Data
    public static class Strategy {
        /** 策略编码，如 operation-log */
        private String name = "default";
        /** 展示名称 */
        private String displayName = "分表";
        /** 物理表前缀，如 sys_operation_log_ */
        private String tablePrefix = "sys_operation_log_";
        /** 结构模板表（CREATE TABLE LIKE） */
        private String templateTable = "sys_operation_log";
        /** 回看月数（含当月前） */
        private int monthsBehind = 6;
        /** 预建未来月数 */
        private int monthsAhead = 1;
        /** 是否自动创建缺失月表 */
        private boolean autoCreate = true;
    }
}
