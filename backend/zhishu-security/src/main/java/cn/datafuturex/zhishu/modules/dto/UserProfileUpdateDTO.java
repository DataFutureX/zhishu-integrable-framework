package cn.datafuturex.zhishu.modules.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 当前用户更新个人资料请求
 */
public record UserProfileUpdateDTO(
        @Size(max = 50, message = "真实姓名不能超过50个字符")
        String realName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过100个字符")
        String email,

        @Size(max = 20, message = "手机号不能超过20个字符")
        String phone
) {
}
