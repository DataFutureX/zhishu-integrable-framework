package cn.datafuturex.zhishu.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单创建 DTO
 */
public record MenuCreateDTO(
        Long id,

        @NotNull(message = "父菜单ID不能为空")
        Long parentId,

        @NotBlank(message = "菜单标题不能为空")
        String title,

        String path,

        String routeName,

        String redirect,

        String icon,

        @NotBlank(message = "菜单类型不能为空")
        String menuType,

        Integer visible,

        Integer requiresAuth,

        Integer sort,

        String component,

        String meta,

        Integer status
) {
}
