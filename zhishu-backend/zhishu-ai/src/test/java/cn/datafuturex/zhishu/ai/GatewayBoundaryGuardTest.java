package cn.datafuturex.zhishu.ai;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AI 内核引用模型网关的边界护栏（与 {@code GatewayDependencyGuardTest} 互为镜像）。
 * <p>
 * 依赖方向为 {@code ai → gateway}，但网关的实现细节（controller / service / mapper / entity）
 * 不应被业务侧牵住，所以 AI 只允许引用网关的导出面：
 * {@code gateway.port}（跨模块端口、通道常量、契约异常）与 {@code gateway.upstream}（OpenAI 兼容构建器）。
 */
class GatewayBoundaryGuardTest {

    /** 网关对外导出面 */
    private static final String[] EXPORTED_GATEWAY_PACKAGES = {
            "cn.datafuturex.zhishu.gateway.port..",
            "cn.datafuturex.zhishu.gateway.upstream..",
    };

    private final JavaClasses aiClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.datafuturex.zhishu.ai");

    @Test
    @DisplayName("AI 只能引用网关的导出面 gateway.port / gateway.upstream")
    void aiMustOnlyUseGatewayExportedSurface() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("cn.datafuturex.zhishu.ai..")
                .should().dependOnClassesThat(resideInAnyPackage("cn.datafuturex.zhishu.gateway..")
                        .and(resideOutsideOfPackages(EXPORTED_GATEWAY_PACKAGES)))
                .because("网关内部实现随时会重构；需要新的协作点时，先在 gateway.port 暴露端口或常量，"
                        + "不要把网关的 service / mapper / entity 直接引进 AI");
        rule.check(aiClasses);
    }
}
