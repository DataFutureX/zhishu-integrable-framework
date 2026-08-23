package cn.datafuturex.zhishu.ai.briefing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "简报投递 VO")
public record BriefingDeliveryVO(
        Long id,
        Long scheduleId,
        String triggerType,
        String triggerRef,
        String userId,
        Long agentId,
        Long runId,
        String title,
        String contentMd,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime readAt,
        String emailStatus,
        LocalDateTime createTime
) {
}
