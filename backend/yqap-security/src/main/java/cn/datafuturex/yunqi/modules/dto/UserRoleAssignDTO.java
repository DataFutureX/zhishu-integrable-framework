package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 用户角色分配 DTO
 */
public record UserRoleAssignDTO(
        @NotNull(message = "角色ID不能为空")
        Long roleId
) {
}
