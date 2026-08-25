package cn.datafuturex.zhishu.modules.dto;

/**
 * 登录请求 DTO
 *
 * @author YunQi Application Platform Team
 */
public record LoginRequestDTO(
        /**
         * 用户名
         */
        String username,

        /**
         * 密码
         */
        String password,

        /**
         * 滑动验证码通过后的令牌
         */
        String captchaToken,

        /**
         * RSA 公钥标识（启用加密传输时必填）
         */
        String keyId
) {
}
