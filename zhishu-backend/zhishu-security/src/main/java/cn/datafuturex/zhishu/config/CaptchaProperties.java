package cn.datafuturex.zhishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 滑动验证码配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunqi.captcha")
public class CaptchaProperties {

    /**
     * 是否启用滑动验证码
     */
    private boolean enabled = true;

    /**
     * 验证码会话过期时间（秒）
     */
    private int expireSeconds = 120;

    /**
     * 验证通过后令牌过期时间（秒）
     */
    private int tokenExpireSeconds = 300;

    /**
     * 滑动位置容差（像素）
     */
    private int tolerance = 5;

    /**
     * 背景图宽度
     */
    private int imageWidth = 310;

    /**
     * 背景图高度
     */
    private int imageHeight = 155;

    /**
     * 拼图块宽度
     */
    private int blockWidth = 50;

    /**
     * 拼图块高度
     */
    private int blockHeight = 50;

    /**
     * 拼图凹凸半径
     */
    private int blockRadius = 8;
}
