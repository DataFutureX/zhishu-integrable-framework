package cn.datafuturex.zhishu.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 校验 AI 内核各应用模块的边界（约定与 {@code BizModulithStructureTest} 一致）。
 * <p>
 * 护栏覆盖：模块只能依赖 {@code allowedDependencies} 声明过的兄弟模块导出接口（NamedInterface），
 * 引用他人内部包（例如 {@code ai.knowledge} 的实现与实体包）会在此失败。
 * <p>
 * MCP 已迁至同级 Maven 模块 {@code zhishu-mcp}（不在本 AI 模块图内），它的平面隔离与
 * 「ai 只依赖 mcp 导出接口」由 {@code zhishu-core} 的 {@code McpArchitectureGuardTest} 强制。
 */
class AiModulithStructureTest {

    @Test
    @DisplayName("AI 模块边界成立")
    void verifyModularStructure() {
        ApplicationModules.of(AiModules.class).verify();
    }
}
