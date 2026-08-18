package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户创建请求 DTO
 *
 * @author YunQi Application Platform Team
 */
public record UserCreateDTO(
        /**
         * 用户名
         */
        @NotBlank(message = "用户名不能为空")
        String username,

        /**
         * 真实姓名
         */
        @NotBlank(message = "真实姓名不能为空")
        String realName,

        /**
         * 邮箱
         */
        @Email(message = "邮箱格式不正确")
        String email,

        /**
         * 手机号
         */
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        /**
         * 密码
         */
        @NotBlank(message = "密码不能为空")
        String password,

        /**
         * 角色ID（关联 sys_role.id）
         */
        @NotNull(message = "角色不能为空")
        Long roleId,

        /**
         * 用户状态（1-正常，0-禁用）
         */
        Integer status
) {
}
