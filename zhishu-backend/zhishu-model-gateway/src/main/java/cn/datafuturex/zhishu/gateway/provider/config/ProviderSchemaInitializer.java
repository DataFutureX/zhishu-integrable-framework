package cn.datafuturex.zhishu.gateway.provider.config;

import cn.datafuturex.zhishu.common.crypto.CredentialCrypto;
import cn.datafuturex.zhishu.common.crypto.CredentialProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

/**
 * 默认供应商引导（模型网关侧）。
 * <p>
 * 表结构由 {@link cn.datafuturex.zhishu.gateway.config.GatewaySchemaInitializer}（Order 17）
 * 统一执行 {@code db/init_gateway.sql} 负责；本类只做一件事：
 * 供应商表为空时，从 {@code spring.ai.openai.*} 引导一条默认供应商（Key 加密入库）。
 * <p>
 * 这里只用 JdbcTemplate 读写，不依赖 AI 内核的任何内部实体，保持依赖方向 ai → gateway。
 */
@Slf4j
@Component
@Order(16)
@RequiredArgsConstructor
public class ProviderSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final CredentialProperties credentialProperties;

    @Value("${spring.ai.openai.base-url:}")
    private String envBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String envApiKey;

    /**
     * 建表并引导默认供应商。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        bootstrapDefaultProvider();
    }

    /**
     * 供应商表为空时，从环境变量引导一条默认供应商（仅连接与凭据）。
     *
     * <p>模型行需在「模型网关 · 模型设置」中创建，本方法不写任何模型信息。</p>
     */
    private void bootstrapDefaultProvider() {
        try {
            Integer providerCount = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_model_provider", Integer.class);
            if (providerCount != null && providerCount > 0) {
                return;
            }
        } catch (Exception e) {
            log.warn("供应商表尚不可用，跳过引导: {}", e.getMessage());
            return;
        }

        if (!StringUtils.hasText(envBaseUrl) || !StringUtils.hasText(envApiKey)) {
            log.info("未检测到旧模型配置与 spring.ai.openai 环境变量，不引导默认供应商；请启动后在「模型网关 · 供应商管理」中配置");
            return;
        }
        try {
            String apiKeyEnc = CredentialCrypto.encrypt(envApiKey, credentialProperties.getCryptoKey());
            String masked = envApiKey.length() <= 8
                    ? "****" : envApiKey.substring(0, 3) + "****" + envApiKey.substring(envApiKey.length() - 4);
            int inserted = jdbcTemplate.update("""
                    INSERT INTO ai_model_provider (
                        name, provider_key, base_url, api_key_enc, api_key_masked,
                        is_default, status, sort_order, remark, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, TRUE, 'ENABLED', 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (provider_key) DO NOTHING
                    """,
                    "默认模型设置", "default", trimSlash(envBaseUrl), apiKeyEnc, masked,
                    "由环境变量引导");
            if (inserted > 0) {
                log.info("已从 spring.ai.openai.* 引导默认供应商（Key 已加密入库）；请在「模型设置」中添加对话/向量模型行");
            }
        } catch (Exception e) {
            log.warn("环境变量引导供应商失败: {}", e.getMessage());
        }
    }

    private static String trimSlash(String url) {
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
