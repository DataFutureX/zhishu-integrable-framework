package cn.datafuturex.zhishu.ai.kg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wanxiang.kg")
public class KgProperties {

    private boolean enabled = true;
    private Neo4j neo4j = new Neo4j();
    private Sync sync = new Sync();

    @Data
    public static class Neo4j {
        private String uri = "bolt://127.0.0.1:7687";
        private String username = "neo4j";
        private String password = "";
        private String database = "neo4j";
    }

    @Data
    public static class Sync {
        /** Spring cron，默认每 5 分钟 */
        private String cron = "0 */5 * * * *";
        private int alertRetentionDays = 90;
        private int issueClosedRetentionDays = 180;
    }
}
