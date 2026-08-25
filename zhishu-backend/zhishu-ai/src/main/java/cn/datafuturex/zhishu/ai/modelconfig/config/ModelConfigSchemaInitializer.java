package cn.datafuturex.zhishu.ai.modelconfig.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(16)
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(ModelConfigProperties.class)
public class ModelConfigSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/patch_ai_model_config_api_key.sql"));
            populator.execute(dataSource);
            log.info("已检查 ai_model_config.api_key_enc 列");
        } catch (Exception e) {
            log.warn("ai_model_config 密钥列补丁跳过或失败: {}", e.getMessage());
        }
    }
}
