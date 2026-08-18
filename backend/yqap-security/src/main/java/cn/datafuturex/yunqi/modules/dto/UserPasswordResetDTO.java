package cn.datafuturex.yunqi.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码请求
 */
public record UserPasswordResetDTO(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度须在6-64位之间")
        String newPassword
) {
}
