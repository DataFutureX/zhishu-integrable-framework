package cn.datafuturex.zhishu.gateway.provider.service.impl;

import cn.datafuturex.zhishu.gateway.provider.domain.ModelProviderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 供应商管理列表排序回归测试。
 * <p>
 * 规则：默认供应商 → 已启用 → 排序号 → 主键；停用的沉底，避免管理员误选。
 */
class ModelProviderSortTest {

    private static ModelProviderEntity provider(Long id, String name, boolean isDefault,
                                                String status, Integer sortOrder) {
        ModelProviderEntity entity = new ModelProviderEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setIsDefault(isDefault);
        entity.setStatus(status);
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private static List<String> names(List<ModelProviderEntity> entities) {
        return entities.stream().map(ModelProviderEntity::getName).toList();
    }

    @Test
    @DisplayName("默认供应商最前，其次启用，停用沉底")
    void defaultThenEnabledThenDisabled() {
        List<ModelProviderEntity> input = new ArrayList<>(List.of(
                provider(3L, "停用A", false, "DISABLED", 0),
                provider(2L, "启用B", false, "ENABLED", 5),
                provider(1L, "默认C", true, "ENABLED", 9),
                provider(4L, "停用D", false, "DISABLED", 1)));

        assertEquals(List.of("默认C", "启用B", "停用A", "停用D"), names(
                ModelProviderServiceImpl.sortForAdmin(input)));
    }

    @Test
    @DisplayName("同优先级按排序号升序，再按主键升序")
    void sortOrderThenId() {
        List<ModelProviderEntity> input = new ArrayList<>(List.of(
                provider(7L, "启用-排序2", false, "ENABLED", 2),
                provider(9L, "启用-排序1-id9", false, "ENABLED", 1),
                provider(5L, "启用-排序1-id5", false, "ENABLED", 1),
                provider(6L, "启用-空排序", false, "ENABLED", null)));

        assertEquals(List.of("启用-排序1-id5", "启用-排序1-id9", "启用-排序2", "启用-空排序"),
                names(ModelProviderServiceImpl.sortForAdmin(input)));
    }

    @Test
    @DisplayName("默认但已停用的供应商仍排在普通启用之前（默认优先于状态）")
    void defaultWinsOverStatus() {
        List<ModelProviderEntity> input = new ArrayList<>(List.of(
                provider(2L, "启用普通", false, "ENABLED", 0),
                provider(1L, "默认已停用", true, "DISABLED", 9)));

        assertEquals(List.of("默认已停用", "启用普通"),
                names(ModelProviderServiceImpl.sortForAdmin(input)));
    }

    @Test
    @DisplayName("不修改入参顺序")
    void doesNotMutateInput() {
        List<ModelProviderEntity> input = new ArrayList<>(List.of(
                provider(2L, "普通", false, "ENABLED", 1),
                provider(1L, "默认", true, "ENABLED", 0)));

        ModelProviderServiceImpl.sortForAdmin(input);

        assertEquals(List.of("普通", "默认"), names(input));
    }
}
