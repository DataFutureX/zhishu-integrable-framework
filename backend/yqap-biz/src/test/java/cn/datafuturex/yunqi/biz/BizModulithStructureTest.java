package cn.datafuturex.yunqi.biz;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 校验 biz 应用模块边界
 */
class BizModulithStructureTest {

    @Test
    void verifyModularStructure() {
        ApplicationModules.of(BizModules.class).verify();
    }
}
