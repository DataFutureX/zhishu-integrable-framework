package cn.datafuturex.zhishu.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境配置守卫：把「静默回落默认值」变成「启动失败」。
 * <p>
 * 这些项在 {@code application.yml} 里都带开发默认值，且 {@code application-prod.yml} 不覆盖它们；
 * 漏配时应用照常启动，但安全边界已经失效或被悄悄改变，排障代价极高。真实教训：
 * {@code zhishu.ai.crypto-key} 在两台环境用了不同取值却共用同一个库，导致存量模型 Key
 * 全部报「解密失败」，而启动阶段没有任何提示。
 * <p>
 * 应急逃生开关：{@code ZHISHU_PROD_GUARD_ENABLED=false}（仅用于临时回滚窗口，
 * 关闭后仍应尽快补齐配置）。
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigGuard implements InitializingBean {

    /** 与 application.yml 中的开发默认值保持一致，命中即视为「未显式配置」 */
    private static final String DEFAULT_AI_CRYPTO_KEY = "zhishu-dev-model-key-wrap";
    private static final String DEFAULT_JWT_SECRET = "zhishu-secret-key-for-jwt-token-generation-2024";
    private static final String DEFAULT_NEO4J_PASSWORD = "12345678";

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (!environment.getProperty("zhishu.prod-guard.enabled", Boolean.class, true)) {
            log.warn("生产配置守卫已被 ZHISHU_PROD_GUARD_ENABLED=false 关闭：密钥与鉴权项不再校验，请尽快补齐配置");
            return;
        }

        List<String> problems = new ArrayList<>();

        // 模型 API Key 的加解密密钥：与入库时不一致会让全部存量密文无法解出
        rejectDefault("zhishu.ai.crypto-key", DEFAULT_AI_CRYPTO_KEY,
                "请用 ZHISHU_AI_CRYPTO_KEY 注入独立密钥（同一数据库的所有实例必须使用同一把）", problems);

        // JWT 签名密钥：prod 应由 JWT_SECRET 注入，回落默认等于任何人可伪造 token
        rejectDefault("jwt.secret", DEFAULT_JWT_SECRET,
                "请用 JWT_SECRET 注入生产签名密钥", problems);

        // 入站鉴权密钥：留空即「不鉴权」，属于功能开关级风险，必须显式决策
        // MCP 不再使用静态共享密钥：/mcp 凭据为每 Client 的 wxmcp_*（库内 SHA-256）或平台登录 JWT，
        // 因此这里不再有 zhishu.mcp.server.auth-key 校验（历史项已随 zhishu-mcp 双平面合并移除）。
        if (environment.getProperty("zhishu.desktop.enabled", Boolean.class, true)) {
            rejectBlank("zhishu.desktop.auth-key",
                    "桌面端鉴权密钥；请用 ZHISHU_DESKTOP_AUTH_KEY 注入，若确需关闭鉴权请同时置 zhishu.desktop.enabled=false", problems);
        }

        // 网关静态 token 模式：mode=static 时 token 为空等于 /v1 无门禁
        String authMode = environment.getProperty("zhishu.gateway.auth.mode", "jwt");
        if ("static".equalsIgnoreCase(authMode)) {
            rejectBlank("zhishu.gateway.auth.static-token",
                    "zhishu.gateway.auth.mode=static 时必须提供 ZHISHU_GATEWAY_TOKEN，否则 /v1 完全无门禁", problems);
        }

        // 图谱启用时，Neo4j 不允许用弱默认口令
        if (environment.getProperty("wanxiang.kg.enabled", Boolean.class, false)) {
            rejectDefault("wanxiang.kg.neo4j.password", DEFAULT_NEO4J_PASSWORD,
                    "已启用知识图谱（wanxiang.kg.enabled=true），请用 NEO4J_PASSWORD 注入真实口令", problems);
        }

        if (!problems.isEmpty()) {
            String message = "生产环境配置检查未通过（" + problems.size() + " 项）：\n  - "
                    + String.join("\n  - ", problems);
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info("生产环境配置检查通过：密钥与入站鉴权项均已显式注入");
    }

    private void rejectDefault(String key, String devDefault, String hint, List<String> problems) {
        String value = environment.getProperty(key);
        if (!StringUtils.hasText(value)) {
            problems.add(key + " 未配置 —— " + hint);
        } else if (devDefault.equals(value)) {
            problems.add(key + " 仍在使用开发默认值 —— " + hint);
        }
    }

    private void rejectBlank(String key, String hint, List<String> problems) {
        if (!StringUtils.hasText(environment.getProperty(key))) {
            problems.add(key + " 为空 —— " + hint);
        }
    }
}
