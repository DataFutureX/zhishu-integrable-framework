package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单更新 DTO
 */
public record MenuUpdateDTO(
        @NotNull(message = "菜单ID不能为空")
        Long id,

        Long parentId,

        @NotBlank(message = "菜单标题不能为空")
        String title,

        String path,

        String routeName,

        String redirect,

        String icon,

        String menuType,

        Integer visible,

        Integer requiresAuth,

        Integer sort,

        String component,

        String meta,

        Integer status
) {
}
