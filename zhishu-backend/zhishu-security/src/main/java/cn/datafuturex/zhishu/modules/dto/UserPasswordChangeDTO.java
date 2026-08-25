package cn.datafuturex.zhishu.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户修改密码请求 DTO
 *
 * @author YunQi Application Platform Team
 */
public record UserPasswordChangeDTO(
        /**
         * 原密码
         */
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        /**
         * 新密码
         */
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度须在6-64位之间")
        String newPassword
) {
}
