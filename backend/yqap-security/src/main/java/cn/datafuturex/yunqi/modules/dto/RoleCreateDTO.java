package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 角色创建 DTO
 */
public record RoleCreateDTO(
        @NotBlank(message = "角色编码不能为空")
        String roleCode,

        @NotBlank(message = "角色名称不能为空")
        String roleName,

        String description,

        Integer status,

        Integer sort
) {
}
