package cn.datafuturex.zhishu.ai.briefing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "简报调度 VO")
public record BriefingScheduleVO(
        Long id,
        String name,
        Long agentId,
        String promptTemplate,
        String scopeType,
        String scheduleType,
        String scheduleTime,
        String scheduleDays,
        String cronExpr,
        String timezone,
        LocalDateTime nextRunAt,
        LocalDateTime lastRunAt,
        Boolean notifyBell,
        Boolean notifyEmail,
        String emailToMode,
        String emailExtraTo,
        String emailSubjectTemplate,
        Boolean enabled,
        String createdBy,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
