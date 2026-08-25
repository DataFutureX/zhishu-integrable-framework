package cn.datafuturex.zhishu.modules.dto;

/**
 * 滑动验证码校验响应 DTO
 */
public record CaptchaVerifyResponseDTO(
        /**
         * 验证通过令牌，登录时携带
         */
        String captchaToken
) {
}
