package cn.datafuturex.zhishu.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模型网关的依赖方向护栏。
 * <p>
 * 网关是基础设施层，AI 内核是业务层，依赖方向为 {@code ai → gateway}；
 * 因此这里不再保留任何「白名单式」例外：<b>网关不得引用 {@code cn.datafuturex.zhishu.ai} 下的任何类</b>，
 * 也不得伸进同级的 MCP 特性模块。跨模块协作一律走网关自己的导出面
 * （{@code gateway.port} 端口 / {@code gateway.upstream} 构建器）或 {@code zhishu-security} 的 common 层。
 */
class GatewayDependencyGuardTest {

    /**
     * 网关不得依赖的特性模块包。
     * <p>
     * 注意必须用项目根包锚定：写 {@code ..ai..} 会误命中 Spring AI 的 {@code org.springframework.ai..}。
     */
    private static final String[] FORBIDDEN_PACKAGES = {
            "cn.datafuturex.zhishu.ai..",
            "cn.datafuturex.zhishu.mcp..",
    };

    private final JavaClasses gatewayClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.datafuturex.zhishu.gateway");

    @Test
    @DisplayName("网关不得依赖 AI 内核与 MCP 模块（依赖方向为 ai → gateway）")
    void gatewayMustNotDependOnAiOrMcp() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("cn.datafuturex.zhishu.gateway..")
                .should().dependOnClassesThat(resideInAnyPackage(FORBIDDEN_PACKAGES))
                .because("网关是模型基础设施，反向依赖 ai 会把业务表结构与内部实现牵进来，"
                        + "并使网关无法独立拆分为服务；需要业务能力时把端口声明在 gateway.port，由业务侧实现");
        rule.check(gatewayClasses);
    }
}
