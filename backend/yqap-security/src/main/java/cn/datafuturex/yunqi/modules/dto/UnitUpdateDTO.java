package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 单位更新请求 DTO
 */
public record UnitUpdateDTO(
        @NotNull(message = "ID不能为空")
        Long id,

        Long parentId,
        String unitCode,
        String unitName,
        String unitType,
        String region,
        String address,
        String contactPerson,
        String contactPhone,
        Integer sort,
        Integer status,
        String remark
) {
}
