package cn.datafuturex.zhishu.biz.systemconfig.vo;

import java.time.LocalDateTime;

/**
 * 系统配置视图对象
 */
public record SystemConfigVO(
        Long id,
        String systemName,
        String englishTitle,
        String systemIcon,
        String copyright,
        String systemIntroduction,
        String projectSite,
        Boolean loginRetryLimitEnabled,
        Integer loginMaxRetryAttempts,
        Integer loginLockMinutes,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
