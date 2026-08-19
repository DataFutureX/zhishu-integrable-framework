package cn.datafuturex.zhishu.modules.service;

import cn.datafuturex.zhishu.modules.dto.CaptchaResponseDTO;
import cn.datafuturex.zhishu.modules.dto.CaptchaVerifyResponseDTO;

/**
 * 滑动验证码服务
 */
public interface CaptchaService {

    /**
     * 生成滑动验证码
     *
     * @return 验证码图片信息
     */
    CaptchaResponseDTO generate();

    /**
     * 校验滑动位置
     *
     * @param captchaId 验证码标识
     * @param slideX    滑动 X 轴偏移
     * @return 验证通过令牌
     */
    CaptchaVerifyResponseDTO verify(String captchaId, int slideX);

    /**
     * 消费验证令牌（登录时校验，一次性）
     *
     * @param captchaToken 验证令牌
     * @return 是否有效
     */
    boolean consumeToken(String captchaToken);
}
