package cn.datafuturex.zhishu.modules.dto;

/**
 * 滑动验证码校验请求 DTO
 */
public record CaptchaVerifyRequestDTO(
        /**
         * 验证码唯一标识
         */
        String captchaId,

        /**
         * 用户滑动的 X 轴偏移量
         */
        Integer slideX
) {
}
