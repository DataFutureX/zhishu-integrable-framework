package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 角色更新 DTO
 */
public record RoleUpdateDTO(
        @NotNull(message = "角色ID不能为空")
        Long id,

        @NotBlank(message = "角色名称不能为空")
        String roleName,

        String description,

        Integer status,

        Integer sort
) {
}
