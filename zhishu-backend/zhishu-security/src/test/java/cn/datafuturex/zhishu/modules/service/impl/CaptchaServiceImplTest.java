package cn.datafuturex.zhishu.modules.service.impl;

import cn.datafuturex.zhishu.captcha.CaptchaStore;
import cn.datafuturex.zhishu.captcha.SlideCaptchaGenerator;
import cn.datafuturex.zhishu.config.CaptchaProperties;
import cn.datafuturex.zhishu.modules.dto.CaptchaResponseDTO;
import cn.datafuturex.zhishu.modules.dto.CaptchaVerifyResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑动验证码服务单元测试
 */
class CaptchaServiceImplTest {

    private CaptchaServiceImpl captchaService;
    private CaptchaStore captchaStore;

    @BeforeEach
    void setUp() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setTolerance(5);
        properties.setExpireSeconds(120);
        properties.setTokenExpireSeconds(300);

        captchaStore = new CaptchaStore();
        SlideCaptchaGenerator generator = new SlideCaptchaGenerator(properties);
        captchaService = new CaptchaServiceImpl(generator, captchaStore, properties);
    }

    @Test
    @DisplayName("生成验证码应返回完整图片数据")
    void testGenerate() {
        CaptchaResponseDTO response = captchaService.generate();

        assertNotNull(response.captchaId());
        assertNotNull(response.backgroundImage());
        assertNotNull(response.sliderImage());
        assertNotNull(response.sliderY());
    }

    @Test
    @DisplayName("正确滑动位置应校验通过并返回令牌")
    void testVerifySuccess() {
        String captchaId = "test-captcha-id";
        int targetX = 120;
        captchaStore.saveSession(captchaId, targetX, System.currentTimeMillis() + 120_000);

        CaptchaVerifyResponseDTO verifyResponse = captchaService.verify(captchaId, targetX);

        assertNotNull(verifyResponse.captchaToken());
        assertTrue(captchaService.consumeToken(verifyResponse.captchaToken()));
        assertFalse(captchaService.consumeToken(verifyResponse.captchaToken()));
    }

    @Test
    @DisplayName("错误滑动位置应校验失败")
    void testVerifyFailure() {
        CaptchaResponseDTO response = captchaService.generate();

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> captchaService.verify(response.captchaId(), 0));

        assertEquals("验证失败，请重试", exception.getMessage());
    }
}
