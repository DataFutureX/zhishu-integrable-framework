package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 单位创建请求 DTO
 */
public record UnitCreateDTO(
        Long parentId,

        /**
         * 单位编码（可选，未填写时系统自动生成）
         */
        String unitCode,

        @NotBlank(message = "单位名称不能为空")
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
    public UnitCreateDTO {
        if (parentId == null) {
            parentId = 0L;
        }
        if (sort == null) {
            sort = 0;
        }
        if (status == null) {
            status = 1;
        }
    }
}
