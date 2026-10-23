package cn.datafuturex.zhishu.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模型网关的依赖方向护栏。
 * <p>
 * 网关是独立 Maven 模块，内部包边界已由 pom 的依赖方向约束；本测试补上另一半：
 * <b>不允许反向伸进 ai 的业务模块内部</b>。跨模块协作必须通过 ai 暴露的模型接入层
 * （{@code ai.modelconfig.*} / {@code ai.gatewayclient.*} 的导出接口）或 {@code ai.shared}。
 */
class GatewayDependencyGuardTest {

    /**
     * ai 侧业务模块：网关不得直接依赖。
     * <p>
     * 注意必须用项目根包锚定：写成 {@code ..ai.chat..} 会误命中 Spring AI 的
     * {@code org.springframework.ai.chat..}。{@code ai.modelconfig} / {@code ai.gatewayclient}
     * 与 {@code ai.shared} 属于模型接入层与共享层，不在禁止名单内。
     */
    private static final String[] FORBIDDEN_AI_PACKAGES = {
            "cn.datafuturex.zhishu.ai.agent..",
            "cn.datafuturex.zhishu.ai.chat..",
            "cn.datafuturex.zhishu.ai.knowledge..",
            "cn.datafuturex.zhishu.ai.kg..",
            "cn.datafuturex.zhishu.ai.openapi..",
            "cn.datafuturex.zhishu.ai.mcp..",
            "cn.datafuturex.zhishu.ai.platform..",
            "cn.datafuturex.zhishu.ai.biztools..",
            "cn.datafuturex.zhishu.ai.briefing..",
    };

    private final JavaClasses gatewayClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.datafuturex.zhishu.gateway");

    @Test
    @DisplayName("网关不得依赖 ai 的业务模块（跨模块请走端口/导出接口）")
    void gatewayMustNotDependOnAiBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("cn.datafuturex.zhishu.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_AI_PACKAGES)
                .because("模型网关只能依赖 ai 的模型接入层（modelconfig / gatewayclient）与 shared；"
                        + "需要 ai 业务能力时改为在 ai 侧暴露端口，避免表结构与内部实现互相牵制");
        rule.check(gatewayClasses);
    }
}
