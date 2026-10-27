package cn.datafuturex.zhishu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * MCP 模块的边界护栏（原 {@code ai.mcp} 的 {@code @ApplicationModule} 约束在迁出后由此接管）。
 * <p>
 * 三条硬约束：
 * <ol>
 *   <li>provide（我方对外提供）与 consume（接入他方）<b>互不依赖</b>——两平面各自演进、互不影响；</li>
 *   <li>{@code zhishu-mcp} 不得依赖 AI 内核（{@code cn.datafuturex.zhishu.ai..}），基础类型只能来自
 *       {@code zhishu-security} 的 {@code common}；</li>
 *   <li>AI 只能使用 MCP 的导出契约（{@code consume.api} 与 {@code provide.spi}），
 *       不得伸入两平面的内部实现。</li>
 * </ol>
 * 注意必须用项目根包锚定：写成 {@code ..provide..} 会误命中第三方同名包。
 */
class McpArchitectureGuardTest {

    private static final String MCP = "cn.datafuturex.zhishu.mcp";
    private static final String PROVIDE = MCP + ".provide..";
    private static final String CONSUME = MCP + ".consume..";
    private static final String COMMON = MCP + ".common..";
    private static final String WEB = MCP + ".web..";

    /** MCP 对 AI 暴露的唯一出口 */
    private static final String[] MCP_EXPORTED_PACKAGES = {
            MCP + ".consume.api..",
            MCP + ".provide.spi..",
    };

    private final JavaClasses mcpClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(MCP);

    private final JavaClasses aiClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.datafuturex.zhishu.ai");

    @Test
    @DisplayName("provide 与 consume 两平面互不依赖")
    void planesMustNotDependOnEachOther() {
        ArchRule provideToConsume = noClasses()
                .that().resideInAPackage(PROVIDE)
                .should().dependOnClassesThat().resideInAPackage(CONSUME)
                .because("对外提供平面与接入他方平面必须各自独立演进，跨平面协作只能经控制面（web）只读聚合");
        ArchRule consumeToProvide = noClasses()
                .that().resideInAPackage(CONSUME)
                .should().dependOnClassesThat().resideInAPackage(PROVIDE)
                .because("上游工具不得回流到对外目录，避免形成工具环回");
        provideToConsume.check(mcpClasses);
        consumeToProvide.check(mcpClasses);
    }

    @Test
    @DisplayName("common 只作共享内层，不得反向依赖两平面")
    void commonMustNotDependOnPlanes() {
        noClasses()
                .that().resideInAPackage(COMMON)
                .should().dependOnClassesThat().resideInAnyPackage(PROVIDE, CONSUME, WEB)
                .because("common 是被共享的最内层（配置 / JSON / 建表 / 审计写入），反向引用会让共享层长出血缘")
                .check(mcpClasses);
    }

    @Test
    @DisplayName("两平面不得依赖控制面 web")
    void planesMustNotDependOnWeb() {
        noClasses()
                .that().resideInAnyPackage(PROVIDE, CONSUME)
                .should().dependOnClassesThat().resideInAPackage(WEB)
                .because("web 是控制面门面，只能单向调用平面服务")
                .check(mcpClasses);
    }

    @Test
    @DisplayName("zhishu-mcp 不得依赖 AI 内核")
    void mcpMustNotDependOnAiKernel() {
        noClasses()
                .that().resideInAPackage(MCP + "..")
                .should().dependOnClassesThat().resideInAPackage("cn.datafuturex.zhishu.ai..")
                .because("跨模块协作改为 AI 实现 MCP 的导出契约（provide.spi / consume.api），"
                        + "基础类型下沉到 zhishu-security 的 common 包")
                .check(mcpClasses);
    }

    @Test
    @DisplayName("AI 只能依赖 MCP 的导出契约")
    void aiMustOnlyUseMcpExportedApi() {
        noClasses()
                .that().resideInAPackage("cn.datafuturex.zhishu.ai..")
                .should().dependOnClassesThat(resideInAnyPackage(MCP + "..")
                        .and(not(resideInAnyPackage(MCP_EXPORTED_PACKAGES))))
                .because("需要 MCP 能力时扩展 consume.api / provide.spi，而不是直接引用实现类或实体")
                .check(aiClasses);
    }
}
