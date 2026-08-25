package cn.datafuturex.zhishu.modules.service.impl;

import cn.datafuturex.zhishu.captcha.CaptchaStore;
import cn.datafuturex.zhishu.captcha.SlideCaptchaGenerator;
import cn.datafuturex.zhishu.config.CaptchaProperties;
import cn.datafuturex.zhishu.modules.dto.CaptchaResponseDTO;
import cn.datafuturex.zhishu.modules.dto.CaptchaVerifyResponseDTO;
import cn.datafuturex.zhishu.modules.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 滑动验证码服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final SlideCaptchaGenerator slideCaptchaGenerator;
    private final CaptchaStore captchaStore;
    private final CaptchaProperties captchaProperties;

    @Override
    public CaptchaResponseDTO generate() {
        SlideCaptchaGenerator.CaptchaImage image = slideCaptchaGenerator.generate();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + captchaProperties.getExpireSeconds() * 1000L;
        captchaStore.saveSession(captchaId, image.targetX(), expireAt);

        log.debug("生成滑动验证码: captchaId={}, targetX={}", captchaId, image.targetX());
        return new CaptchaResponseDTO(
                captchaId,
                image.backgroundImage(),
                image.sliderImage(),
                image.targetY(),
                image.sliderImageOffsetY()
        );
    }

    @Override
    public CaptchaVerifyResponseDTO verify(String captchaId, int slideX) {
        if (!StringUtils.hasText(captchaId)) {
            throw new RuntimeException("验证码已失效，请刷新重试");
        }

        var targetX = captchaStore.consumeSessionTargetX(captchaId)
                .orElseThrow(() -> new RuntimeException("验证码已失效，请刷新重试"));

        int diff = Math.abs(slideX - targetX);
        if (diff > captchaProperties.getTolerance()) {
            log.warn("滑动验证码校验失败: captchaId={}, slideX={}, targetX={}, diff={}",
                    captchaId, slideX, targetX, diff);
            throw new RuntimeException("验证失败，请重试");
        }

        long expireAt = System.currentTimeMillis() + captchaProperties.getTokenExpireSeconds() * 1000L;
        String captchaToken = captchaStore.createVerifyToken(expireAt);
        log.debug("滑动验证码校验成功: captchaId={}", captchaId);
        return new CaptchaVerifyResponseDTO(captchaToken);
    }

    @Override
    public boolean consumeToken(String captchaToken) {
        if (!StringUtils.hasText(captchaToken)) {
            return false;
        }
        return captchaStore.consumeVerifyToken(captchaToken);
    }
}
