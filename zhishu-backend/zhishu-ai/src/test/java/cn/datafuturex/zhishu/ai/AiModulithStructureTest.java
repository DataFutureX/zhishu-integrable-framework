package cn.datafuturex.zhishu.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 校验 AI 内核各应用模块的边界（约定与 {@code BizModulithStructureTest} 一致）。
 * <p>
 * 护栏覆盖：模块只能依赖 {@code allowedDependencies} 声明过的兄弟模块导出接口（NamedInterface），
 * 引用他人内部包（例如未导出的 {@code ai.mcp.support}）会在此失败。
 */
class AiModulithStructureTest {

    @Test
    @DisplayName("AI 模块边界成立")
    void verifyModularStructure() {
        ApplicationModules.of(AiModules.class).verify();
    }
}
