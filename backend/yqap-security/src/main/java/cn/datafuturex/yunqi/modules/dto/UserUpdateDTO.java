package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户更新请求 DTO
 *
 * @author YunQi Application Platform Team
 */
public record UserUpdateDTO(
        /**
         * 用户ID
         */
        @NotNull(message = "用户ID不能为空")
        Long id,

        /**
         * 真实姓名
         */
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
         * 密码（不传则不修改）
         */
        String password,

        /**
         * 角色ID（关联 sys_role.id，不传则不修改）
         */
        Long roleId,

        /**
         * 用户状态（1-正常，0-禁用）
         */
        Integer status
) {
}
