package cn.datafuturex.zhishu.biz.systemconfig.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 系统配置更新 DTO
 */
public record SystemConfigUpdateDTO(
        @NotBlank(message = "系统名称不能为空")
        @Size(max = 100, message = "系统名称不能超过100个字符")
        String systemName,

        @Size(max = 100, message = "英文标题不能超过100个字符")
        String englishTitle,

        @Size(max = 500, message = "系统图标URL不能超过500个字符")
        String systemIcon,

        @Size(max = 200, message = "版权信息不能超过200个字符")
        String copyright,

        @Size(max = 5000, message = "系统介绍不能超过5000个字符")
        String systemIntroduction,

        @Size(max = 200, message = "项目地不能超过200个字符")
        String projectSite,

        @NotNull(message = "是否开启登录重试限制不能为空")
        Boolean loginRetryLimitEnabled,

        @NotNull(message = "允许登录失败次数不能为空")
        @Min(value = 1, message = "允许登录失败次数至少为1次")
        @Max(value = 20, message = "允许登录失败次数不能超过20次")
        Integer loginMaxRetryAttempts,

        @NotNull(message = "登录锁定分钟数不能为空")
        @Min(value = 1, message = "登录锁定分钟数至少为1分钟")
        @Max(value = 1440, message = "登录锁定分钟数不能超过1440分钟")
        Integer loginLockMinutes
) {
}
