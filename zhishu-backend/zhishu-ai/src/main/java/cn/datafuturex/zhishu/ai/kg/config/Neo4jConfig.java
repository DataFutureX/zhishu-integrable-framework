package cn.datafuturex.zhishu.ai.kg.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KgProperties.class)
@ConditionalOnProperty(prefix = "wanxiang.kg", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class Neo4jConfig {

    private final KgProperties properties;

    @Bean(destroyMethod = "close")
    public Neo4jSupport neo4jSupport() {
        return Neo4jSupport.connect(properties);
    }
}
