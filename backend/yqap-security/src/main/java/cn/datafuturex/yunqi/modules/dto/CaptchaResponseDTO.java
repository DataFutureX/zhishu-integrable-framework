package cn.datafuturex.yunqi.modules.dto;

/**
 * 滑动验证码响应 DTO
 */
public record CaptchaResponseDTO(
        /**
         * 验证码唯一标识
         */
        String captchaId,

        /**
         * 背景图（Base64 PNG）
         */
        String backgroundImage,

        /**
         * 滑块图（Base64 PNG）
         */
        String sliderImage,

        /**
         * 拼图块 Y 轴位置（前端用于对齐）
         */
        Integer sliderY,

        /**
         * 滑块图内拼图块向上预留像素（前端定位时需减去该偏移）
         */
        Integer sliderImageOffsetY
) {
}
