package com.datafuturex.assistant.briefing.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建/更新简报调度")
public record BriefingScheduleUpsertDTO(
        @NotBlank
        @Size(max = 128)
        @Schema(description = "调度名称")
        String name,

        @Schema(description = "智能体 ID")
        Long agentId,

        @Schema(description = "提示词模板")
        String promptTemplate,

        @Schema(description = "投递范围，默认 USER_PROJECTS")
        String scopeType,

        @NotBlank
        @Pattern(regexp = "DAILY|WEEKLY|CRON", message = "scheduleType 须为 DAILY/WEEKLY/CRON")
        @Schema(description = "DAILY | WEEKLY | CRON")
        String scheduleType,

        @Schema(description = "HH:mm，如 08:00")
        String scheduleTime,

        @Schema(description = "周几，逗号分隔 1-7（周一=1），WEEKLY 时使用")
        String scheduleDays,

        @Schema(description = "Spring 6 段 cron，CRON 时使用")
        String cronExpr,

        @Schema(description = "时区，默认 Asia/Shanghai")
        String timezone,

        @Schema(description = "站内铃通知")
        Boolean notifyBell,

        @Schema(description = "邮件通知")
        Boolean notifyEmail,

        @Schema(description = "USER_PROFILE | EXTRA")
        String emailToMode,

        String emailExtraTo,

        String emailSubjectTemplate,

        @Schema(description = "是否启用")
        Boolean enabled
) {
}
