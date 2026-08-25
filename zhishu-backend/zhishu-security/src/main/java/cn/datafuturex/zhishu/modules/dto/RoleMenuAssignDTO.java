package cn.datafuturex.zhishu.modules.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色菜单授权 DTO
 */
public record RoleMenuAssignDTO(
        @NotNull(message = "菜单ID列表不能为空")
        List<Long> menuIds
) {
}
