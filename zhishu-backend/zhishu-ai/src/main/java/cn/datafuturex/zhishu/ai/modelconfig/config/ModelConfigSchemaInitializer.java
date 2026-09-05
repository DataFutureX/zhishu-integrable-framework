package cn.datafuturex.zhishu.ai.modelconfig.config;

import cn.datafuturex.zhishu.ai.modelconfig.domain.AiModelConfigEntity;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.AiModelConfigMapper;
import cn.datafuturex.zhishu.ai.modelconfig.mapper.ModelProviderMapper;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@Order(16)
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(ModelConfigProperties.class)
public class ModelConfigSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final AiModelConfigMapper aiModelConfigMapper;
    private final ModelProviderMapper modelProviderMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 幂等执行旧补丁
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.addScript(new ClassPathResource("db/patch_ai_model_config_api_key.sql"));
            populator.addScript(new ClassPathResource("db/V_add_model_provider.sql"));
            populator.execute(dataSource);
            log.info("已执行模型设置迁移脚本");
        } catch (Exception e) {
            log.warn("模型设置迁移补丁跳过或失败: {}", e.getMessage());
        }

        // 2. 数据迁移：从 ai_model_config 迁移到 ai_model_provider
        migrateFromLegacy();
    }

    /**
     * 从旧 ai_model_config 单例迁移到 ai_model_provider 默认记录。
     * 仅在 ai_model_provider 表为空时执行。
     */
    private void migrateFromLegacy() {
        Long providerCount = modelProviderMapper.selectCount(null);
        if (providerCount != null && providerCount > 0) {
            return;
        }
        AiModelConfigEntity legacy = aiModelConfigMapper.selectById(1L);
        if (legacy == null) {
            log.info("无旧模型配置数据，跳过迁移");
            return;
        }
        ModelProviderEntity provider = new ModelProviderEntity();
        provider.setName("默认模型设置");
        provider.setProviderKey("default");
        provider.setBaseUrl(legacy.getBaseUrl() != null ? legacy.getBaseUrl() : "https://dashscope.aliyuncs.com/compatible-mode/v1");
        provider.setApiKeyEnc(legacy.getApiKeyEnc());
        provider.setApiKeyMasked(legacy.getApiKeyMasked());
        provider.setChatModel(legacy.getChatModel() != null ? legacy.getChatModel() : "qwen-plus");
        provider.setEmbeddingModel(legacy.getEmbeddingModel());
        provider.setTemperature(legacy.getTemperature() != null ? legacy.getTemperature() : BigDecimal.valueOf(0.70).setScale(2, RoundingMode.HALF_UP));
        provider.setMaxTokens(legacy.getMaxTokens() != null ? legacy.getMaxTokens() : 2000);
        provider.setTopP(legacy.getTopP() != null ? legacy.getTopP() : BigDecimal.valueOf(0.90).setScale(2, RoundingMode.HALF_UP));
        provider.setIsDefault(true);
        provider.setStatus("ENABLED");
        provider.setSortOrder(0);
        provider.setRemark("从旧模型配置自动迁移");
        provider.setCreateTime(LocalDateTime.now());
        provider.setUpdateTime(LocalDateTime.now());
        modelProviderMapper.insert(provider);
        log.info("已从 ai_model_config 迁移到 ai_model_provider 默认记录 id={}", provider.getId());
    }
}
